/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** A single (folder, child) link. Stored twice — {@code f|folder|child} + {@code df|child|folder} —
 *  so both "browse folder" and "which folders contain this doc" are prefix scans. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FolderMembership {

    public String folderCanonical;
    public String childCanonical;
    public String addedBy;
    public Instant addedAt;

    public FolderMembership() { }

    public FolderMembership(String folder, String child, String addedBy) {
        this.folderCanonical = folder;
        this.childCanonical = child;
        this.addedBy = addedBy;
        this.addedAt = Instant.now();
    }
}
