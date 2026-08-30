/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.index;

import java.util.List;
import java.util.Map;

/** Read-side of the derived index. Query grammar is the impl's own
 *  (Lucene classic query parser in the shipped impl). */
public interface IndexSearcher extends AutoCloseable {

    /** Search the primary documents index. Returns up to {@code limit}
     *  matching version_ids (most relevant first). */
    List<SearchHit> search(String queryString, int limit);

    /** Fetch stored fields for one version_id. Empty when not found. */
    Map<String, Object> fetch(String versionId);

    @Override void close();

    record SearchHit(String versionId, String canonicalId,
                     String versionLabel, String title, double score) { }
}
