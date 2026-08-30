/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.model.Grant;
import com.hitorro.dms.store.AclStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{canonical}/acls")
public class AclsController {

    private final AclStore acls;

    public AclsController(AclStore acls) { this.acls = acls; }

    @GetMapping
    public List<Grant> list(@PathVariable("canonical") String canonical) {
        return acls.listForDoc(canonical);
    }

    @PostMapping
    public ResponseEntity<Grant> grant(@PathVariable("canonical") String canonical,
                                        @RequestBody Grant g) {
        g.canonicalId = canonical;
        acls.grant(g);
        return ResponseEntity.ok(g);
    }

    @DeleteMapping("/{principal}/{permission}")
    public ResponseEntity<Void> revoke(@PathVariable("canonical") String canonical,
                                        @PathVariable("principal") String principal,
                                        @PathVariable("permission") String permission) {
        acls.revoke(canonical, principal, permission);
        return ResponseEntity.noContent().build();
    }
}
