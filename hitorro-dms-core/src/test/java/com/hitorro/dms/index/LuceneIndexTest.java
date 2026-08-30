/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.index;

import com.hitorro.dms.blob.InMemoryBlobStore;
import com.hitorro.dms.index.lucene.LuceneIndex;
import com.hitorro.dms.model.Document;
import com.hitorro.dms.service.CheckInRequest;
import com.hitorro.dms.service.CreateRequest;
import com.hitorro.dms.service.DocumentService;
import com.hitorro.dms.store.mem.InMemoryDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneIndexTest {

    @Test
    void index_and_full_text_search(@TempDir Path tmp) throws Exception {
        LuceneIndex idx = new LuceneIndex(tmp.resolve("idx"));
        DocumentService svc = new DocumentService(new InMemoryDocumentStore(), new InMemoryBlobStore(), idx);

        // Body contains singular "container" — StandardAnalyzer doesn't stem.
        Document d1 = svc.create(newDoc("Kubernetes intro", "container orchestration platform"));
        Document d2 = svc.create(newDoc("Docker guide", "container runtime"));
        Document d3 = svc.create(newDoc("Java notes", "unrelated content"));

        List<IndexSearcher.SearchHit> hits = idx.search("orchestration", 10);
        assertThat(hits).extracting(IndexSearcher.SearchHit::canonicalId).containsExactly(d1.canonicalId);

        List<IndexSearcher.SearchHit> containerHits = idx.search("container", 10);
        assertThat(containerHits).extracting(IndexSearcher.SearchHit::canonicalId)
                .containsExactlyInAnyOrder(d1.canonicalId, d2.canonicalId);

        idx.close();
    }

    @Test
    void reindexing_same_version_is_idempotent(@TempDir Path tmp) throws Exception {
        LuceneIndex idx = new LuceneIndex(tmp.resolve("idx"));
        DocumentService svc = new DocumentService(new InMemoryDocumentStore(), new InMemoryBlobStore(), idx);
        Document d = svc.create(newDoc("hello", "unique kubernetes doc"));
        idx.indexDocument(d);
        idx.indexDocument(d);
        idx.indexDocument(d);
        assertThat(idx.search("unique", 10)).hasSize(1);
        idx.close();
    }

    @Test
    void search_by_version_label(@TempDir Path tmp) throws Exception {
        LuceneIndex idx = new LuceneIndex(tmp.resolve("idx"));
        DocumentService svc = new DocumentService(new InMemoryDocumentStore(), new InMemoryBlobStore(), idx);

        // Two documents, each with two versions. Body has a unique phrase per doc.
        Document a1 = svc.create(newDoc("A", "alphahopper body"));
        CheckInRequest req = new CheckInRequest(); req.canonicalId = a1.canonicalId;
        Document a2 = svc.checkIn(req);
        assertThat(a2.versionLabel).isEqualTo("1.1.0");

        // Full-text search finds both versions (both indexed). is_head filter
        // narrows to the current one — StandardAnalyzer keeps "true" as-is.
        List<IndexSearcher.SearchHit> allA = idx.search("alphahopper", 10);
        assertThat(allA).hasSize(2);   // both versions indexed
        List<IndexSearcher.SearchHit> headA = idx.search("alphahopper AND is_head:true", 10);
        assertThat(headA).hasSize(1);
        assertThat(headA.get(0).versionLabel()).isEqualTo("1.1.0");

        idx.close();
    }

    @Test
    void fetch_returns_stored_fields(@TempDir Path tmp) throws Exception {
        LuceneIndex idx = new LuceneIndex(tmp.resolve("idx"));
        DocumentService svc = new DocumentService(new InMemoryDocumentStore(), new InMemoryBlobStore(), idx);
        Document d = svc.create(newDoc("Title", "body"));
        var stored = idx.fetch(d.versionId);
        assertThat(stored).containsEntry("version_id", d.versionId);
        assertThat(stored).containsEntry("canonical_id", d.canonicalId);
        idx.close();
    }

    private CreateRequest newDoc(String title, String body) {
        CreateRequest r = new CreateRequest();
        r.title = title;
        r.body = body;
        r.contentType = "wiki-page";
        r.createdBy = "test";
        return r;
    }
}
