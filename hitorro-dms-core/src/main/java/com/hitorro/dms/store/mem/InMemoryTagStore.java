/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store.mem;

import com.hitorro.dms.store.TagStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTagStore implements TagStore {

    private final Map<String, Set<String>> tagsByDoc  = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> docsByTag  = new ConcurrentHashMap<>();

    @Override
    public synchronized void tag(String canonicalId, String tag) {
        tagsByDoc.computeIfAbsent(canonicalId, k -> new LinkedHashSet<>()).add(tag);
        docsByTag.computeIfAbsent(tag,         k -> new LinkedHashSet<>()).add(canonicalId);
    }

    @Override
    public synchronized void untag(String canonicalId, String tag) {
        Set<String> t = tagsByDoc.get(canonicalId);
        if (t != null) t.remove(tag);
        Set<String> d = docsByTag.get(tag);
        if (d != null) d.remove(canonicalId);
    }

    @Override
    public synchronized List<String> listTags(String canonicalId) {
        return new ArrayList<>(tagsByDoc.getOrDefault(canonicalId, Set.of()));
    }

    @Override
    public synchronized List<String> listDocsWithTag(String tag) {
        return new ArrayList<>(docsByTag.getOrDefault(tag, Set.of()));
    }
}
