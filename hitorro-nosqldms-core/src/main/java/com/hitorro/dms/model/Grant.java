/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One ACL entry: this principal has (or is denied) this permission
 * on this document. Stored in {@code a|{canonical}|{principal}} and
 * mirrored in {@code g|{principal}|{canonical}}.
 *
 * <p>NOT stored on the document itself — grants mutate on org changes,
 * many times more often than doc bodies.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Grant {

    public static final String PERM_READ   = "read";
    public static final String PERM_WRITE  = "write";
    public static final String PERM_DELETE = "delete";
    public static final String PERM_SHARE  = "share";
    public static final String PERM_ADMIN  = "admin";

    public String canonicalId;
    /** 'user:alice', 'group:eng', 'role:reviewer', 'public'. */
    public String principal;
    public String permission;
    /** true = allow, false = deny (deny beats allow). */
    public boolean grant = true;
    /** Optional: canonical id of the folder these grants were inherited from. */
    public String inheritFrom;
    public Instant grantedAt;

    public Grant() { }

    public Grant(String canonicalId, String principal, String permission, boolean grant) {
        this.canonicalId = canonicalId;
        this.principal = principal;
        this.permission = permission;
        this.grant = grant;
        this.grantedAt = Instant.now();
    }
}
