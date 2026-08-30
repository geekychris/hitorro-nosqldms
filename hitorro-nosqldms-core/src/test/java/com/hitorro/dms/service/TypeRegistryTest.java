/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.hitorro.dms.blob.InMemoryBlobStore;
import com.hitorro.dms.model.Document;
import com.hitorro.dms.model.TypeDef;
import com.hitorro.dms.store.mem.InMemoryDocumentStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeRegistryTest {

    @Test
    void bundled_types_load_from_classpath() {
        TypeRegistry r = new TypeRegistry();
        assertThat(r.all()).extracting(t -> t.name)
                .containsExactlyInAnyOrder("wiki-page", "task", "contact", "folder");
    }

    @Test
    void wiki_page_fields_are_present_and_ordered() {
        TypeDef t = new TypeRegistry().get("wiki-page").orElseThrow();
        assertThat(t.title).isEqualTo("Wiki page");
        assertThat(t.fields).extracting(f -> f.name)
                .containsExactly("summary", "author_alias", "status", "tags");
        var status = t.fields.stream().filter(f -> f.name.equals("status")).findFirst().orElseThrow();
        assertThat(status.required).isTrue();
        assertThat(status.kind).isEqualTo("enum");
        assertThat(status.choices).containsExactly("draft", "review", "published", "archived");
    }

    @Test
    void validate_missing_required_returns_error() {
        TypeRegistry r = new TypeRegistry();
        // task type has assignee + status required
        List<String> errs = r.validate("task", Map.of("priority", "high"));
        assertThat(errs).anyMatch(e -> e.contains("assignee"))
                .anyMatch(e -> e.contains("status"));
    }

    @Test
    void validate_passing_all_required_is_empty() {
        TypeRegistry r = new TypeRegistry();
        List<String> errs = r.validate("task", Map.of(
                "assignee", "alice", "status", "todo"));
        assertThat(errs).isEmpty();
    }

    @Test
    void unknown_type_flagged_by_validate() {
        assertThat(new TypeRegistry().validate("no-such-type", Map.of()))
                .anyMatch(e -> e.contains("unknown type"));
    }

    @Test
    void document_service_round_trips_type_fields() throws Exception {
        DocumentService svc = new DocumentService(
                new InMemoryDocumentStore(), new InMemoryBlobStore(), null);
        CreateRequest req = new CreateRequest();
        req.title = "My task";
        req.contentType = "task";
        req.typeName = "task";
        req.typeFields = Map.of("assignee", "alice", "status", "todo", "priority", "high");
        req.createdBy = "u";
        Document v1 = svc.create(req);
        assertThat(v1.typeName).isEqualTo("task");
        assertThat(v1.typeFields).containsEntry("assignee", "alice");

        // Check-in a metadata bump — typeFields inherited
        CheckInRequest ci = new CheckInRequest();
        ci.canonicalId = v1.canonicalId;
        Document v2 = svc.checkIn(ci);
        assertThat(v2.typeFields).containsEntry("assignee", "alice")
                                 .containsEntry("status", "todo");

        // Check-in updating one field — merge semantics
        CheckInRequest ci2 = new CheckInRequest();
        ci2.canonicalId = v1.canonicalId;
        ci2.typeFields = Map.of("status", "done");
        Document v3 = svc.checkIn(ci2);
        assertThat(v3.typeFields).containsEntry("status", "done")
                                 .containsEntry("assignee", "alice");   // still inherited
    }
}
