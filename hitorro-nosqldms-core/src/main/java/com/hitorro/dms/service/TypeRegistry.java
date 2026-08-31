/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.dms.model.TypeDef;
import com.hitorro.jsontypesystem.Field;
import com.hitorro.jsontypesystem.JsonTypeSystem;
import com.hitorro.jsontypesystem.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Facade over hitorro-jsontypesystem's {@link JsonTypeSystem} —
 * exposes the DMS-registered types as a stable projection for the
 * REST + UI layer while keeping the JVS type registry as the source
 * of truth.
 *
 * <p>Every DMS type <b>extends sysobject</b> (transitively, via
 * {@code dms_document}) so it inherits the JVS id / title / times /
 * body / description composite fields — see the JSON in
 * {@code src/main/resources/config/types/}.</p>
 *
 * <p>The projection ({@link TypeDef}) flattens the JVS type into a
 * form the UI can render as a dynamic form: one row per non-inherited
 * field, with a UI-friendly {@code kind} derived from the field's JVS
 * primitive type. Composite fields (core_mls, core_id, core_dates)
 * inherited from sysobject are NOT flattened — the UI shows title as
 * a simple string that maps to {@code title.mls[en].text} on
 * persistence.</p>
 */
public final class TypeRegistry {

    /** The DMS types this registry lists in the UI picker. Everything
     *  else JsonTypeSystem knows about is still queryable by name but
     *  not shown as a "createable" option. */
    private static final List<String> DMS_TYPE_NAMES = List.of(
            "dms_wiki_page", "dms_task", "dms_contact", "dms_folder");

    /** Types considered "structural" — inherited-from-sysobject fields
     *  we don't want cluttering the type-specific form. */
    private static final Set<String> STRUCTURAL_FIELDS = Set.of(
            // sysobject
            "id", "times", "title", "body", "description",
            // dms_document (versioning + rendition manifest — managed by the DMS)
            "canonical_id", "version_id", "version_label",
            "version_major", "version_minor", "version_patch",
            "version_qualifier", "version_qual_number", "version_build",
            "version_kind", "parent_version", "is_head", "is_stable",
            "content_type", "created_by", "modified_by",
            "content_refs", "tombstoned");

    public TypeRegistry() { this(null); }

    public TypeRegistry(Path unusedLegacyUserDir) {
        // The user-overlay dir concept is now handled by dropping JSON
        // into $HT_BIN/config/types/ directly — JsonTypeSystem's
        // convention. TypeBootstrap places the DMS bundled types there;
        // operators can drop additional dms_*.json into the same dir.
    }

    /** All DMS types projected as {@link TypeDef}s for the UI. */
    public List<TypeDef> all() {
        List<TypeDef> out = new ArrayList<>();
        for (String name : DMS_TYPE_NAMES) {
            get(name).ifPresent(out::add);
        }
        return out;
    }

    /** Projection of one type. Absent if JsonTypeSystem doesn't know it. */
    public Optional<TypeDef> get(String name) {
        Type t = safeGet(name);
        if (t == null) return Optional.empty();
        return Optional.of(project(t));
    }

    /** Get the raw JVS {@link Type} (source of truth). */
    public Optional<Type> jvsType(String name) {
        return Optional.ofNullable(safeGet(name));
    }

    public boolean has(String name) { return safeGet(name) != null; }

    /**
     * Light validation — requires the type to be known + reports any
     * required-field violations. For richer validation (type coercion,
     * constraints, dynamic-field checks) use {@code JVSValidator}
     * against a materialised JVS instance.
     */
    public List<String> validate(String typeName, Map<String, Object> fields) {
        TypeDef t = get(typeName).orElse(null);
        if (t == null) return List.of("unknown type: " + typeName);
        List<String> errs = new ArrayList<>();
        for (TypeDef.FieldDef f : t.fields) {
            if (f.required) {
                Object v = fields == null ? null : fields.get(f.name);
                if (v == null || (v instanceof String s && s.isBlank()))
                    errs.add("required field missing: " + f.name);
            }
        }
        return errs;
    }

    /** Snapshot for tests + diagnostics. */
    public Map<String, TypeDef> snapshot() {
        Map<String, TypeDef> m = new LinkedHashMap<>();
        for (TypeDef t : all()) m.put(t.name, t);
        return Collections.unmodifiableMap(m);
    }

    // ---- projection JVS.Type → TypeDef -------------------------------

    private static Type safeGet(String name) {
        try { return JsonTypeSystem.getMe().getType(name); }
        catch (Exception e) { return null; }
    }

    private static TypeDef project(Type t) {
        TypeDef td = new TypeDef();
        td.name = t.getName();
        JsonNode meta = t.getMetaNode();
        if (meta != null) {
            if (meta.has("description")) td.description = meta.path("description").asText();
            td.title = titleOf(t.getName());
        }

        // Walk the type's OWN fields (skip inherited structural ones).
        JsonNode fieldsNode = meta == null ? null : meta.get("fields");
        if (fieldsNode != null && fieldsNode.isArray()) {
            for (JsonNode fn : fieldsNode) {
                String fname = fn.path("name").asText(null);
                if (fname == null || STRUCTURAL_FIELDS.contains(fname)) continue;
                td.fields.add(projectField(t, fname, fn));
            }
        }
        return td;
    }

    private static TypeDef.FieldDef projectField(Type parent, String name, JsonNode fn) {
        TypeDef.FieldDef fd = new TypeDef.FieldDef();
        fd.name  = name;
        fd.label = labelOf(name);
        String jvsType = fn.path("type").asText();
        boolean vector = fn.path("vector").asBoolean(false);
        fd.kind = kindFor(jvsType, vector);
        if (fn.has("description")) fd.help = fn.path("description").asText();
        // A field with description containing "Enum-like: a / b / c" gets its
        // choices parsed out — cheap convention that keeps the JSON simple.
        if ("core_string".equals(jvsType) && fd.help != null && fd.help.contains("Enum-like:")) {
            fd.kind = "enum";
            fd.choices = parseChoices(fd.help);
        }
        // Fields nested inside dms_content_ref etc. — skip for now (rendered elsewhere).
        return fd;
    }

    private static String kindFor(String jvsType, boolean vector) {
        String base = switch (jvsType) {
            case "core_string"  -> "string";
            case "core_long"    -> "long";
            case "core_double"  -> "double";
            case "core_boolean" -> "boolean";
            case "core_date"    -> "date";
            case "core_url"     -> "url";
            case "core_mls"     -> "text";     // multilingual → simple textarea in UI (writes to [en])
            default             -> "string";   // composite fallback
        };
        return vector ? "array<" + base + ">" : base;
    }

    private static List<String> parseChoices(String help) {
        int i = help.indexOf("Enum-like:");
        if (i < 0) return null;
        String rest = help.substring(i + "Enum-like:".length()).trim();
        // Trailing period optional
        if (rest.endsWith(".")) rest = rest.substring(0, rest.length() - 1);
        List<String> out = new ArrayList<>();
        for (String s : rest.split("/")) {
            String v = s.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private static String titleOf(String name) {
        if (name.startsWith("dms_")) name = name.substring(4);
        StringBuilder b = new StringBuilder();
        for (String w : name.split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private static String labelOf(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name.split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }
}
