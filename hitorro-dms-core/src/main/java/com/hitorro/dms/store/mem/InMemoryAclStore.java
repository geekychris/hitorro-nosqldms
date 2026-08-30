/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store.mem;

import com.hitorro.dms.model.Grant;
import com.hitorro.dms.store.AclStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAclStore implements AclStore {

    private final Map<String, List<Grant>> byDoc = new ConcurrentHashMap<>();
    private final Map<String, List<Grant>> byPrincipal = new ConcurrentHashMap<>();

    @Override
    public synchronized void grant(Grant g) {
        // Replace an existing (doc, principal, permission) grant to keep sets clean.
        revoke(g.canonicalId, g.principal, g.permission);
        byDoc      .computeIfAbsent(g.canonicalId, k -> new ArrayList<>()).add(g);
        byPrincipal.computeIfAbsent(g.principal,   k -> new ArrayList<>()).add(g);
    }

    @Override
    public synchronized void revoke(String canonicalId, String principal, String permission) {
        List<Grant> d = byDoc.get(canonicalId);
        if (d != null) d.removeIf(g -> g.principal.equals(principal) && g.permission.equals(permission));
        List<Grant> p = byPrincipal.get(principal);
        if (p != null) p.removeIf(g -> g.canonicalId.equals(canonicalId) && g.permission.equals(permission));
    }

    @Override
    public synchronized List<Grant> listForDoc(String canonicalId) {
        return new ArrayList<>(byDoc.getOrDefault(canonicalId, List.of()));
    }

    @Override
    public synchronized List<Grant> listForPrincipal(String principal) {
        return new ArrayList<>(byPrincipal.getOrDefault(principal, List.of()));
    }
}
