/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store.mem;

import com.hitorro.dms.model.Document;
import com.hitorro.dms.store.DocumentStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link DocumentStore} — default for phase 1 and used by
 * every unit test. Behaviour is identical to a RocksDB-backed impl for
 * the ops the DMS uses; only durability differs. Swap for a persistent
 * impl later without touching any service code.
 */
public class InMemoryDocumentStore implements DocumentStore {

    /** canonicalId → (versionLabel → doc), insertion-ordered. */
    private final Map<String, Map<String, Document>> versionsByCanonical = new ConcurrentHashMap<>();
    private final Map<String, Document> byVersionId = new ConcurrentHashMap<>();
    private final Map<String, String> heads = new ConcurrentHashMap<>();      // canonicalId → versionId
    private final Map<String, Boolean> tombstones = new ConcurrentHashMap<>();

    @Override
    public void putVersion(Document doc) {
        if (doc.canonicalId == null || doc.versionLabel == null || doc.versionId == null)
            throw new IllegalArgumentException("doc must have canonicalId + versionLabel + versionId");
        versionsByCanonical
                .computeIfAbsent(doc.canonicalId, k -> Collections.synchronizedMap(new LinkedHashMap<>()))
                .put(doc.versionLabel, doc);
        byVersionId.put(doc.versionId, doc);
    }

    @Override
    public void setHead(String canonicalId, String versionId) {
        // Also flip is_head on the docs — the DMS service does this in its
        // own copy before calling putVersion, but a raw setHead should
        // also toggle the flag on stored versions to keep queries correct.
        heads.put(canonicalId, versionId);
        Map<String, Document> versions = versionsByCanonical.get(canonicalId);
        if (versions == null) return;
        for (Document d : versions.values()) {
            d.isHead = d.versionId.equals(versionId);
        }
    }

    @Override
    public Optional<Document> getHead(String canonicalId) {
        String versionId = heads.get(canonicalId);
        if (versionId == null) return Optional.empty();
        return Optional.ofNullable(byVersionId.get(versionId));
    }

    @Override
    public Optional<Document> getVersion(String canonicalId, String versionLabel) {
        Map<String, Document> versions = versionsByCanonical.get(canonicalId);
        if (versions == null) return Optional.empty();
        return Optional.ofNullable(versions.get(versionLabel));
    }

    @Override
    public Optional<Document> getVersionById(String versionId) {
        return Optional.ofNullable(byVersionId.get(versionId));
    }

    @Override
    public List<Document> listVersions(String canonicalId) {
        Map<String, Document> versions = versionsByCanonical.get(canonicalId);
        if (versions == null) return List.of();
        synchronized (versions) { return new ArrayList<>(versions.values()); }
    }

    @Override
    public List<String> listCanonicals() {
        return new ArrayList<>(versionsByCanonical.keySet());
    }

    @Override
    public void tombstone(String canonicalId) {
        tombstones.put(canonicalId, Boolean.TRUE);
    }

    @Override
    public boolean isTombstoned(String canonicalId) {
        return Boolean.TRUE.equals(tombstones.get(canonicalId));
    }

    @Override
    public void purge(String canonicalId) {
        Map<String, Document> versions = versionsByCanonical.remove(canonicalId);
        if (versions != null) {
            synchronized (versions) {
                for (Document d : versions.values()) byVersionId.remove(d.versionId);
            }
        }
        heads.remove(canonicalId);
        tombstones.remove(canonicalId);
    }
}
