/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.dms.model.TypeDef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads document type definitions from a classpath directory
 * ({@code dms-types/*.json}) and optionally a user-supplied directory
 * ({@link #loadFromDir}) at construction. Read-only after startup —
 * type definitions are curated content, not runtime config.
 *
 * <p>User directory takes precedence over the classpath (so operators
 * can override bundled types by dropping a same-named file into their
 * types dir). Missing user dir is not an error.</p>
 */
public final class TypeRegistry {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CP_DIR = "dms-types/";
    /** Type files we ship in the jar. Kept as a resource-loader manifest
     *  because listing classpath directories reliably is a pain. */
    private static final String[] BUNDLED = {
            "wiki-page.json", "task.json", "contact.json", "folder.json",
    };

    private final Map<String, TypeDef> byName = new LinkedHashMap<>();

    public TypeRegistry() {
        loadClasspath();
    }

    public TypeRegistry(Path userDir) {
        loadClasspath();
        if (userDir != null) loadFromDir(userDir);
    }

    public List<TypeDef> all() {
        return new ArrayList<>(byName.values());
    }

    public Optional<TypeDef> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public boolean has(String name) { return byName.containsKey(name); }

    /** Validate incoming typed-fields against the registered type.
     *  Currently a light check: required fields must be present + non-blank.
     *  Returns a list of error messages; empty = valid. */
    public List<String> validate(String typeName, Map<String, Object> fields) {
        TypeDef t = byName.get(typeName);
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

    // ---- loading -------------------------------------------------------

    private void loadClasspath() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = TypeRegistry.class.getClassLoader();
        for (String file : BUNDLED) {
            try (InputStream in = cl.getResourceAsStream(CP_DIR + file)) {
                if (in == null) continue;
                TypeDef t = JSON.readValue(in.readAllBytes(), TypeDef.class);
                if (t.name != null) byName.put(t.name, t);
            } catch (IOException e) {
                System.err.println("[TypeRegistry] failed to load " + file + ": " + e.getMessage());
            }
        }
    }

    /** Overlay user-supplied types on top of the bundled set. */
    public void loadFromDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    TypeDef t = JSON.readValue(Files.readString(p, StandardCharsets.UTF_8), TypeDef.class);
                    if (t.name != null) byName.put(t.name, t);
                } catch (IOException e) {
                    System.err.println("[TypeRegistry] " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("[TypeRegistry] scan " + dir + ": " + e.getMessage());
        }
    }

    /** Snapshot for tests + diagnostics. */
    public Map<String, TypeDef> snapshot() {
        return Collections.unmodifiableMap(byName);
    }
}
