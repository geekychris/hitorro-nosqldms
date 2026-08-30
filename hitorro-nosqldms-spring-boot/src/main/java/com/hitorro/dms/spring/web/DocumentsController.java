/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.model.Document;
import com.hitorro.dms.service.CheckInRequest;
import com.hitorro.dms.service.CreateRequest;
import com.hitorro.dms.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentsController {

    private final DocumentService svc;

    public DocumentsController(DocumentService svc) { this.svc = svc; }

    /** {@code POST /api/documents} — create a new doc at 1.0.0. */
    @PostMapping
    public ResponseEntity<Document> create(@RequestBody CreateRequest req) throws IOException {
        return ResponseEntity.ok(svc.create(req));
    }

    /** {@code GET /api/documents} — list every canonical id (paged in a real impl). */
    @GetMapping
    public List<String> list() { return svc.listCanonicals(); }

    /** {@code GET /api/documents/{id}} — current head. */
    @GetMapping("/{id}")
    public ResponseEntity<Document> head(@PathVariable("id") String id) {
        return svc.getHead(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** {@code GET /api/documents/{id}/versions} — full version history. */
    @GetMapping("/{id}/versions")
    public List<Document> versions(@PathVariable("id") String id) { return svc.listVersions(id); }

    /** {@code GET /api/documents/{id}/versions/{label}} — one specific version. */
    @GetMapping("/{id}/versions/{label}")
    public ResponseEntity<Document> version(@PathVariable("id") String id,
                                            @PathVariable("label") String label) {
        return svc.getVersion(id, label).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** {@code POST /api/documents/{id}/versions} — check in a new version.
     *  Content changes go via the rendition endpoints or a multipart follow-up. */
    @PostMapping("/{id}/versions")
    public ResponseEntity<Document> checkIn(@PathVariable("id") String id,
                                            @RequestBody CheckInRequest req) throws IOException {
        req.canonicalId = id;
        return ResponseEntity.ok(svc.checkIn(req));
    }

    /** {@code DELETE /api/documents/{id}} — soft delete (tombstone). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> tombstone(@PathVariable("id") String id) {
        svc.tombstone(id);
        return ResponseEntity.noContent().build();
    }
}
