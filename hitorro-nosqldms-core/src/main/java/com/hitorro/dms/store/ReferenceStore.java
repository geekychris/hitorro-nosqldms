/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store;

import com.hitorro.dms.model.Reference;

import java.util.List;

/**
 * References between documents — outbound (from → to) and back-refs
 * (to → from). Each add is TWO independent KV writes; on a single
 * partition they're batched into one transaction, cross-partition
 * they're eventually consistent (the design doc's tradeoff).
 */
public interface ReferenceStore {

    void add(Reference ref);
    void remove(String fromCanonical, String toCanonical, String kind);

    List<Reference> listOutbound(String fromCanonical);
    List<Reference> listInbound(String toCanonical);
}
