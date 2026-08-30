/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store.mem;

import com.hitorro.dms.model.FolderMembership;
import com.hitorro.dms.store.FolderStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFolderStore implements FolderStore {

    private final Map<String, List<FolderMembership>> childrenByFolder = new ConcurrentHashMap<>();
    private final Map<String, List<FolderMembership>> foldersByChild   = new ConcurrentHashMap<>();

    @Override
    public synchronized void link(String folderCanonical, String childCanonical, String addedBy) {
        FolderMembership m = new FolderMembership(folderCanonical, childCanonical, addedBy);
        childrenByFolder.computeIfAbsent(folderCanonical, k -> new ArrayList<>()).add(m);
        foldersByChild  .computeIfAbsent(childCanonical,   k -> new ArrayList<>()).add(m);
    }

    @Override
    public synchronized void unlink(String folderCanonical, String childCanonical) {
        List<FolderMembership> cs = childrenByFolder.get(folderCanonical);
        if (cs != null) cs.removeIf(m -> m.childCanonical.equals(childCanonical));
        List<FolderMembership> fs = foldersByChild.get(childCanonical);
        if (fs != null) fs.removeIf(m -> m.folderCanonical.equals(folderCanonical));
    }

    @Override
    public synchronized List<FolderMembership> listChildren(String folderCanonical) {
        return new ArrayList<>(childrenByFolder.getOrDefault(folderCanonical, List.of()));
    }

    @Override
    public synchronized List<FolderMembership> listContainingFolders(String childCanonical) {
        return new ArrayList<>(foldersByChild.getOrDefault(childCanonical, List.of()));
    }
}
