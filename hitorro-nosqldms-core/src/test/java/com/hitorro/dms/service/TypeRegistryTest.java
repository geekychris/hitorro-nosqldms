/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.hitorro.dms.blob.InMemoryBlobStore;
import com.hitorro.dms.context.DmsContext;
import com.hitorro.dms.model.Document;
import com.hitorro.dms.model.TypeDef;
import com.hitorro.dms.store.mem.InMemoryDocumentStore;
import com.hitorro.jsontypesystem.JsonTypeSystem;
import com.hitorro.jsontypesystem.Type;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TypeRegistry projects the JVS type system onto a UI-friendly
 * shape. Every DMS type MUST extend the JVS {@code sysobject} type
 * (transitively via {@code dms_document}) — that's what these tests
 * verify against the real JsonTypeSystem.
 */
class TypeRegistryTest {

    @Test
    void bundled_dms_types_load_and_extend_sysobject() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            TypeRegistry r = ctx.typeRegistry();
            assertThat(r.all()).extracting(t -> t.name)
                    .containsExactlyInAnyOrder(
                            "dms_wiki_page", "dms_task", "dms_contact", "dms_folder");

            // Every DMS type must transitively extend `sysobject`
            for (TypeDef td : r.all()) {
                Type t = r.jvsType(td.name).orElseThrow();
                assertThat(walkSuper(t)).contains("sysobject")
                        .as("type %s must extend sysobject", td.name);
            }
        }
    }

    @Test
    void wiki_page_extends_dms_document_extends_sysobject() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            Type t = ctx.typeRegistry().jvsType("dms_wiki_page").orElseThrow();
            List<String> chain = walkSuper(t);
            assertThat(chain).containsExactly("dms_wiki_page", "dms_document", "sysobject");
        }
    }

    @Test
    void inherited_sysobject_fields_visible_via_JVS_getField() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            Type wiki = ctx.typeRegistry().jvsType("dms_wiki_page").orElseThrow();
            // JVS's Type.getField recursively resolves through supers.
            assertThat(wiki.getField("id")).isNotNull()
                    .as("id (from sysobject) must be visible on dms_wiki_page");
            assertThat(wiki.getField("title")).isNotNull()
                    .as("title (from sysobject) must be visible on dms_wiki_page");
            assertThat(wiki.getField("version_label")).isNotNull()
                    .as("version_label (from dms_document) must be visible on dms_wiki_page");
        }
    }

    @Test
    void type_specific_fields_projected_into_TypeDef() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            TypeDef task = ctx.typeRegistry().get("dms_task").orElseThrow();
            assertThat(task.fields).extracting(f -> f.name)
                    .contains("assignee", "status", "priority", "due_date", "estimate_h", "labels");
            // Structural (sysobject/dms_document) fields are NOT projected —
            // the UI shows them via dedicated widgets, not the type form.
            assertThat(task.fields).extracting(f -> f.name)
                    .doesNotContain("id", "title", "version_label", "content_refs");
        }
    }

    @Test
    void field_kind_derived_from_JVS_primitive_type() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            TypeDef contact = ctx.typeRegistry().get("dms_contact").orElseThrow();
            var email = contact.fields.stream().filter(f -> f.name.equals("email")).findFirst().orElseThrow();
            assertThat(email.kind).isEqualTo("string");    // from core_string
            var website = contact.fields.stream().filter(f -> f.name.equals("website")).findFirst().orElseThrow();
            assertThat(website.kind).isEqualTo("url");     // from core_url
            var notes = contact.fields.stream().filter(f -> f.name.equals("notes")).findFirst().orElseThrow();
            assertThat(notes.kind).isEqualTo("text");      // core_mls → 'text' in UI (writes to [en])
        }
    }

    @Test
    void vector_field_gets_array_kind_prefix() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            TypeDef task = ctx.typeRegistry().get("dms_task").orElseThrow();
            var labels = task.fields.stream().filter(f -> f.name.equals("labels")).findFirst().orElseThrow();
            assertThat(labels.kind).isEqualTo("array<string>");
        }
    }

    @Test
    void enum_kind_inferred_from_description() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            TypeDef task = ctx.typeRegistry().get("dms_task").orElseThrow();
            var status = task.fields.stream().filter(f -> f.name.equals("status")).findFirst().orElseThrow();
            assertThat(status.kind).isEqualTo("enum");
            assertThat(status.choices).containsExactly("todo", "in-progress", "blocked", "done");
        }
    }

    @Test
    void unknown_type_returns_empty() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            assertThat(ctx.typeRegistry().get("no-such-type")).isEmpty();
        }
    }

    @Test
    void document_service_round_trips_typeName_and_fields() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            DocumentService svc = ctx.documentService();
            CreateRequest req = new CreateRequest();
            req.title = "Ship v0.2";
            req.contentType = "dms_task";
            req.typeName = "dms_task";
            req.typeFields = Map.of("assignee", "alice", "status", "in-progress", "priority", "high");
            req.createdBy = "u";
            Document v1 = svc.create(req);

            assertThat(v1.typeName).isEqualTo("dms_task");
            assertThat(v1.typeFields).containsEntry("assignee", "alice")
                                     .containsEntry("status", "in-progress");
        }
    }

    // ---- helpers --------------------------------------------------------

    private static List<String> walkSuper(Type t) {
        List<String> out = new java.util.ArrayList<>();
        for (Type cur = t; cur != null; cur = cur.getSuper()) out.add(cur.getName());
        return out;
    }
}
