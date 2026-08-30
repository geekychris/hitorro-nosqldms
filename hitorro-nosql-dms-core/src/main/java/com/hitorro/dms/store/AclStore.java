/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store;

import com.hitorro.dms.model.Grant;

import java.util.List;

/**
 * Per-(doc × principal) ACL grants. Grant/revoke are small independent
 * writes that do NOT touch the doc body.
 */
public interface AclStore {

    void grant(Grant g);
    void revoke(String canonicalId, String principal, String permission);

    /** All grants on this doc. */
    List<Grant> listForDoc(String canonicalId);

    /** All docs this principal has any grant on. */
    List<Grant> listForPrincipal(String principal);
}
