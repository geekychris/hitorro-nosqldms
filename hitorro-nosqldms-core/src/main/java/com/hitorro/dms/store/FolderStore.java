/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store;

import com.hitorro.dms.model.FolderMembership;

import java.util.List;

/**
 * Many-to-many folder ↔ document links. A doc can be in any number
 * of folders. Add/remove are 2 KV writes each; doc bodies untouched.
 */
public interface FolderStore {

    void link(String folderCanonical, String childCanonical, String addedBy);
    void unlink(String folderCanonical, String childCanonical);

    /** Contents of a folder. */
    List<FolderMembership> listChildren(String folderCanonical);

    /** All folders a doc is linked to. */
    List<FolderMembership> listContainingFolders(String childCanonical);
}
