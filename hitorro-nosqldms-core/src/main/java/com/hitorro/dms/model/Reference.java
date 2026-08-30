/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Directed edge from one document to another. Stored in {@code r|} and {@code br|} KV keyspaces. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Reference {

    public String fromCanonical;
    public String toCanonical;
    /** Optional: pin to a specific target version. Null = "follow head." */
    public String toVersion;
    /** 'supersedes' / 'cites' / 'attached-to' / ... */
    public String kind;
    /** Free-form JSON string, per-kind. */
    public String aux;
    public Instant createdAt;

    public Reference() { }

    public Reference(String fromCanonical, String toCanonical, String kind) {
        this.fromCanonical = fromCanonical;
        this.toCanonical = toCanonical;
        this.kind = kind;
        this.createdAt = Instant.now();
    }
}
