/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * A registered document type — the "wiki-page", "task", "contact"
 * shape a document conforms to. Compatible with a subset of JVS
 * type definitions (name + list of fields); loaded from JSON files
 * on the classpath or a user-supplied directory.
 *
 * <p>Field defs drive the UI's dynamic form (create + edit) and
 * the DMS's field-level query surface. Every doc carries a
 * {@code typeName} + a {@code typeFields} map — the DMS treats
 * {@code typeFields} as opaque JSON, so unknown fields (e.g. added
 * later after some docs already exist) are preserved on round-trip.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TypeDef {

    /** Registered id — kebab-case, e.g. {@code wiki-page}. */
    public String name;

    /** Human title for the UI picker. */
    public String title;

    /** One-line description. */
    public String description;

    /** Field definitions in display order. */
    public List<FieldDef> fields = new ArrayList<>();

    /** Optional extends target — one level of nesting only (JVS full
     *  inheritance is out of scope). */
    public String extendsType;

    public TypeDef() { }

    /** Field def — subset of JVS field metadata. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldDef {
        public String  name;
        /** {@code string} / {@code text} / {@code long} / {@code double} /
         *  {@code boolean} / {@code date} / {@code url} / {@code enum} /
         *  {@code array<string>}. UI picks a widget per kind. */
        public String  kind;
        public String  label;
        public String  help;
        public boolean required;
        /** For {@code kind: enum} — allowed values in display order. */
        public List<String> choices;
        /** For {@code kind: text} — hint about the widget height. */
        public Integer rows;

        public FieldDef() { }
        public FieldDef(String name, String kind, String label) {
            this.name = name; this.kind = kind; this.label = label;
        }
    }
}
