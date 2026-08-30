/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.model.ContentRef;
import com.hitorro.dms.model.Document;
import com.hitorro.dms.service.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/** Rendition-scoped CRUD — the copy-on-write half of the DMS API. */
@RestController
@RequestMapping("/api/documents/{canonical}")
public class RenditionsController {

    private final DocumentService svc;

    public RenditionsController(DocumentService svc) { this.svc = svc; }

    /** List renditions on the head version. */
    @GetMapping("/renditions")
    public ResponseEntity<List<ContentRef>> listHead(@PathVariable("canonical") String canonical) {
        return svc.getHead(canonical)
                .map(d -> ResponseEntity.ok(d.contentRefs))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** List renditions on a specific version. */
    @GetMapping("/versions/{versionId}/renditions")
    public ResponseEntity<List<ContentRef>> list(@PathVariable("canonical") String canonical,
                                                  @PathVariable("versionId") String versionId) {
        return svc.getVersionById(versionId)
                .filter(d -> canonical.equals(d.canonicalId))
                .map(d -> ResponseEntity.ok(d.contentRefs))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Fetch rendition bytes (on the head or a specific version). */
    @GetMapping("/renditions/{role}")
    public ResponseEntity<byte[]> readHead(@PathVariable("canonical") String canonical,
                                            @PathVariable("role") String role) throws IOException {
        var head = svc.getHead(canonical).orElseThrow();
        return readBytes(canonical, head.versionId, role, head);
    }

    @GetMapping("/versions/{versionId}/renditions/{role}")
    public ResponseEntity<byte[]> read(@PathVariable("canonical") String canonical,
                                       @PathVariable("versionId") String versionId,
                                       @PathVariable("role") String role) throws IOException {
        Document d = svc.getVersionById(versionId).orElseThrow();
        return readBytes(canonical, versionId, role, d);
    }

    private ResponseEntity<byte[]> readBytes(String canonical, String versionId,
                                              String role, Document d) throws IOException {
        var bytes = svc.readRendition(canonical, versionId, role);
        if (bytes.isEmpty()) return ResponseEntity.notFound().build();
        String mime = d.contentRefs.stream().filter(c -> role.equals(c.role))
                .findFirst().map(c -> c.mime).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(bytes.get());
    }

    /** Attach a derived rendition (e.g. thumbnail) to an existing version — no version bump.
     *  Query params: {@code role}, {@code mime}, {@code generatedBy}, {@code derivedFromRole}. */
    @PutMapping(value = "/versions/{versionId}/renditions/{role}",
                consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Document> attach(@PathVariable("canonical") String canonical,
                                            @PathVariable("versionId") String versionId,
                                            @PathVariable("role") String role,
                                            @RequestHeader(value = "Content-Type", defaultValue = MediaType.APPLICATION_OCTET_STREAM_VALUE) String mime,
                                            @RequestHeader(value = "X-Generated-By", defaultValue = "user") String generatedBy,
                                            @RequestHeader(value = "X-Derived-From", required = false) String derivedFromRole,
                                            @RequestBody byte[] body) throws IOException {
        return ResponseEntity.ok(svc.attachRendition(canonical, versionId, role, mime, body,
                generatedBy, derivedFromRole));
    }

    /** Delete a rendition from a version. */
    @DeleteMapping("/versions/{versionId}/renditions/{role}")
    public ResponseEntity<Void> delete(@PathVariable("canonical") String canonical,
                                        @PathVariable("versionId") String versionId,
                                        @PathVariable("role") String role) throws IOException {
        svc.deleteRendition(canonical, versionId, role);
        return ResponseEntity.noContent().build();
    }
}
