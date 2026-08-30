/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.index;

import com.hitorro.dms.model.Document;

/**
 * Sink-style interface for keeping the derived search index in sync
 * with the authoritative document store. Every impl must be
 * idempotent: reindexing the same version twice is legal and
 * produces the same rows.
 */
public interface IndexWriter extends AutoCloseable {

    /** No-op impl for tests + services that don't want a search index. */
    IndexWriter NOOP = new IndexWriter() {
        @Override public void indexDocument(Document doc) { }
        @Override public void deleteDocument(String versionId) { }
        @Override public void commit() { }
        @Override public void close() { }
    };

    void indexDocument(Document doc);
    void deleteDocument(String versionId);
    void commit();
    @Override void close();
}
