/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.index.lucene;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hitorro.dms.index.IndexSearcher;
import com.hitorro.dms.index.IndexWriter;
import com.hitorro.dms.model.ContentRef;
import com.hitorro.dms.model.Document;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher.LeafSlice;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lucene-backed impl of both {@link IndexWriter} and {@link IndexSearcher}.
 * One index per DMS deployment; each version is one Lucene document
 * keyed by {@code version_id}. Reindex is idempotent via
 * {@code updateDocument(Term)}.
 */
public class LuceneIndex implements IndexWriter, IndexSearcher {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Directory dir;
    private final StandardAnalyzer analyzer;
    private final org.apache.lucene.index.IndexWriter writer;

    public LuceneIndex(Path indexDir) throws IOException {
        Files.createDirectories(indexDir);
        this.dir = MMapDirectory.open(indexDir);
        this.analyzer = new StandardAnalyzer();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new org.apache.lucene.index.IndexWriter(dir, cfg);
    }

    // ---- IndexWriter ----------------------------------------------------

    @Override
    public synchronized void indexDocument(Document doc) {
        org.apache.lucene.document.Document lu = new org.apache.lucene.document.Document();

        // Identity
        lu.add(new StringField("version_id", nz(doc.versionId), Field.Store.YES));
        lu.add(new StringField("canonical_id", nz(doc.canonicalId), Field.Store.YES));

        // Version labeling — every part indexed separately for query.
        lu.add(new StringField("version_label", nz(doc.versionLabel), Field.Store.YES));
        lu.add(new LongPoint("version_major", doc.versionMajor));
        lu.add(new StoredField("version_major_s", doc.versionMajor));
        lu.add(new LongPoint("version_minor", doc.versionMinor));
        lu.add(new LongPoint("version_patch", doc.versionPatch));
        lu.add(new LongPoint("version_build", doc.versionBuild));
        lu.add(new NumericDocValuesField("version_build_dv", doc.versionBuild));
        if (doc.versionQualifier != null) {
            lu.add(new StringField("version_qualifier", doc.versionQualifier, Field.Store.YES));
        }
        if (doc.versionQualNumber != null) {
            lu.add(new LongPoint("version_qual_number", doc.versionQualNumber));
        }
        lu.add(new StringField("version_kind", nz(doc.versionKind), Field.Store.YES));
        lu.add(new StringField("is_head",   Boolean.toString(doc.isHead),   Field.Store.YES));
        lu.add(new StringField("is_stable", Boolean.toString(doc.isStable), Field.Store.YES));

        // Metadata
        if (doc.title       != null) lu.add(new TextField("title",       doc.title,       Field.Store.YES));
        if (doc.body        != null) lu.add(new TextField("body",        doc.body,        Field.Store.NO));
        if (doc.description != null) lu.add(new TextField("description", doc.description, Field.Store.YES));
        if (doc.contentType != null) lu.add(new StringField("content_type", doc.contentType, Field.Store.YES));
        if (doc.createdBy   != null) lu.add(new StringField("created_by",   doc.createdBy,   Field.Store.YES));
        if (doc.modifiedBy  != null) lu.add(new StringField("modified_by",  doc.modifiedBy,  Field.Store.YES));
        if (doc.tombstoned)          lu.add(new StringField("tombstoned",   "true",           Field.Store.YES));

        // Rendition manifest — multi-valued facets
        for (ContentRef c : doc.contentRefs) {
            if (c.role != null) lu.add(new StringField("content_refs.role", c.role, Field.Store.NO));
            if (c.mime != null) lu.add(new StringField("content_refs.mime", c.mime, Field.Store.NO));
        }

        // Full JSON in _source for reconstruction without KV round-trip
        try {
            lu.add(new StoredField("_source", JSON.writeValueAsString(doc)));
        } catch (Exception ignore) { /* best-effort */ }

        try {
            writer.updateDocument(new Term("version_id", doc.versionId), lu);
        } catch (IOException e) {
            throw new RuntimeException("Lucene index failed for version " + doc.versionId, e);
        }
    }

    @Override
    public synchronized void deleteDocument(String versionId) {
        try {
            writer.deleteDocuments(new Term("version_id", versionId));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void commit() {
        try { writer.commit(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    @Override
    public synchronized void close() {
        try { writer.close(); dir.close(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    // ---- IndexSearcher --------------------------------------------------

    @Override
    public synchronized List<SearchHit> search(String queryString, int limit) {
        commit();   // make writes visible to a fresh reader
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
            org.apache.lucene.search.IndexSearcher s = new org.apache.lucene.search.IndexSearcher(reader);
            QueryParser p = new QueryParser("body", analyzer);
            Query q = p.parse(queryString);
            TopDocs td = s.search(q, limit);
            List<SearchHit> hits = new ArrayList<>(td.scoreDocs.length);
            for (ScoreDoc sd : td.scoreDocs) {
                org.apache.lucene.document.Document d = s.storedFields().document(sd.doc);
                hits.add(new SearchHit(
                        d.get("version_id"),
                        d.get("canonical_id"),
                        d.get("version_label"),
                        d.get("title"),
                        sd.score));
            }
            return hits;
        } catch (Exception e) {
            throw new RuntimeException("Lucene search failed: " + queryString, e);
        }
    }

    @Override
    public synchronized Map<String, Object> fetch(String versionId) {
        commit();
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
            org.apache.lucene.search.IndexSearcher s = new org.apache.lucene.search.IndexSearcher(reader);
            TopDocs td = s.search(new org.apache.lucene.search.TermQuery(new Term("version_id", versionId)), 1);
            if (td.totalHits.value == 0) return Map.of();
            org.apache.lucene.document.Document d = s.storedFields().document(td.scoreDocs[0].doc);
            Map<String, Object> out = new LinkedHashMap<>();
            for (IndexableField f : d.getFields()) {
                out.putIfAbsent(f.name(), f.stringValue());
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
