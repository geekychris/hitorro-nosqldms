/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store;

import java.util.List;

/** Free-form user tags — mutated frequently, does NOT bump doc versions. */
public interface TagStore {

    void tag(String canonicalId, String tag);
    void untag(String canonicalId, String tag);
    List<String> listTags(String canonicalId);
    List<String> listDocsWithTag(String tag);
}
