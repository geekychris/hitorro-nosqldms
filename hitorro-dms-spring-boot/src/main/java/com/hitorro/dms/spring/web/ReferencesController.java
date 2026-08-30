/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.model.Reference;
import com.hitorro.dms.store.ReferenceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{canonical}/references")
public class ReferencesController {

    private final ReferenceStore refs;

    public ReferencesController(ReferenceStore refs) { this.refs = refs; }

    @GetMapping
    public List<Reference> outbound(@PathVariable("canonical") String canonical) {
        return refs.listOutbound(canonical);
    }

    @GetMapping("/inbound")
    public List<Reference> inbound(@PathVariable("canonical") String canonical) {
        return refs.listInbound(canonical);
    }

    @PostMapping
    public ResponseEntity<Reference> add(@PathVariable("canonical") String canonical,
                                          @RequestBody Reference ref) {
        ref.fromCanonical = canonical;
        refs.add(ref);
        return ResponseEntity.ok(ref);
    }

    @DeleteMapping("/{to}/{kind}")
    public ResponseEntity<Void> remove(@PathVariable("canonical") String canonical,
                                        @PathVariable("to") String to,
                                        @PathVariable("kind") String kind) {
        refs.remove(canonical, to, kind);
        return ResponseEntity.noContent().build();
    }
}
