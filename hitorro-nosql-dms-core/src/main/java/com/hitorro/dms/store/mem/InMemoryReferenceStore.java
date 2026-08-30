/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store.mem;

import com.hitorro.dms.model.Reference;
import com.hitorro.dms.store.ReferenceStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryReferenceStore implements ReferenceStore {

    private final Map<String, List<Reference>> outbound = new ConcurrentHashMap<>();
    private final Map<String, List<Reference>> inbound  = new ConcurrentHashMap<>();

    @Override
    public synchronized void add(Reference ref) {
        outbound.computeIfAbsent(ref.fromCanonical, k -> new ArrayList<>()).add(ref);
        inbound .computeIfAbsent(ref.toCanonical,   k -> new ArrayList<>()).add(ref);
    }

    @Override
    public synchronized void remove(String fromCanonical, String toCanonical, String kind) {
        List<Reference> out = outbound.get(fromCanonical);
        if (out != null) out.removeIf(r -> r.toCanonical.equals(toCanonical) && r.kind.equals(kind));
        List<Reference> in = inbound.get(toCanonical);
        if (in != null)  in.removeIf(r -> r.fromCanonical.equals(fromCanonical) && r.kind.equals(kind));
    }

    @Override
    public synchronized List<Reference> listOutbound(String fromCanonical) {
        return new ArrayList<>(outbound.getOrDefault(fromCanonical, List.of()));
    }

    @Override
    public synchronized List<Reference> listInbound(String toCanonical) {
        return new ArrayList<>(inbound.getOrDefault(toCanonical, List.of()));
    }
}
