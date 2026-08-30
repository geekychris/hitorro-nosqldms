/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.context;

import com.hitorro.dms.blob.BlobStore;
import com.hitorro.dms.blob.InMemoryBlobStore;
import com.hitorro.dms.index.IndexSearcher;
import com.hitorro.dms.index.IndexWriter;
import com.hitorro.dms.index.lucene.LuceneIndex;
import com.hitorro.dms.service.DocumentService;
import com.hitorro.dms.store.AclStore;
import com.hitorro.dms.store.DocumentStore;
import com.hitorro.dms.store.FolderStore;
import com.hitorro.dms.store.ReferenceStore;
import com.hitorro.dms.store.TagStore;
import com.hitorro.dms.store.mem.InMemoryAclStore;
import com.hitorro.dms.store.mem.InMemoryDocumentStore;
import com.hitorro.dms.store.mem.InMemoryFolderStore;
import com.hitorro.dms.store.mem.InMemoryReferenceStore;
import com.hitorro.dms.store.mem.InMemoryTagStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Framework-neutral service registry — the DMS equivalent of hitorro's
 * {@code com.hitorro.util.startupframework.ServiceContext}. Instances
 * are built via {@link Builder} and hand out the wired-up services.
 *
 * <p>Two typical shapes:</p>
 * <ul>
 *   <li><b>Standalone / tests</b> — {@code DmsContext.inMemory()} for a
 *       zero-config all-in-memory registry.</li>
 *   <li><b>Standalone with Lucene</b> — {@code DmsContext.builder().withLucene(path).build()}
 *       for persistent search over an in-memory (or later, KV-backed) store.</li>
 * </ul>
 *
 * <p>The Spring Boot module ({@code hitorro-dms-spring-boot}) publishes
 * the same objects as beans — same objects, different container.</p>
 */
public final class DmsContext implements AutoCloseable {

    private final DocumentStore  documentStore;
    private final ReferenceStore referenceStore;
    private final FolderStore    folderStore;
    private final AclStore       aclStore;
    private final TagStore       tagStore;
    private final BlobStore      blobStore;
    private final IndexWriter    indexWriter;
    private final IndexSearcher  indexSearcher;
    private final DocumentService documentService;
    private final Map<Class<?>, Object> extras;

    private DmsContext(Builder b) throws IOException {
        this.documentStore  = b.documentStore  != null ? b.documentStore  : new InMemoryDocumentStore();
        this.referenceStore = b.referenceStore != null ? b.referenceStore : new InMemoryReferenceStore();
        this.folderStore    = b.folderStore    != null ? b.folderStore    : new InMemoryFolderStore();
        this.aclStore       = b.aclStore       != null ? b.aclStore       : new InMemoryAclStore();
        this.tagStore       = b.tagStore       != null ? b.tagStore       : new InMemoryTagStore();
        this.blobStore      = b.blobStore      != null ? b.blobStore      : new InMemoryBlobStore();

        if (b.luceneDir != null) {
            LuceneIndex lucene = new LuceneIndex(b.luceneDir);
            this.indexWriter = lucene;
            this.indexSearcher = lucene;
        } else {
            this.indexWriter = IndexWriter.NOOP;
            this.indexSearcher = null;
        }

        this.documentService = new DocumentService(documentStore, blobStore, indexWriter);
        this.extras = new HashMap<>(b.extras);
    }

    public static DmsContext inMemory() {
        try { return builder().build(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    public static Builder builder() { return new Builder(); }

    // ---- service accessors ---------------------------------------------

    public DocumentStore   documentStore()   { return documentStore; }
    public ReferenceStore  referenceStore()  { return referenceStore; }
    public FolderStore     folderStore()     { return folderStore; }
    public AclStore        aclStore()        { return aclStore; }
    public TagStore        tagStore()        { return tagStore; }
    public BlobStore       blobStore()       { return blobStore; }
    public IndexWriter     indexWriter()     { return indexWriter; }
    public IndexSearcher   indexSearcher()   { return indexSearcher; }
    public DocumentService documentService() { return documentService; }

    /** Escape hatch for user-supplied extras registered via {@link Builder#with(Class, Object)}. */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Object v = extras.get(type);
        if (v == null) throw new IllegalArgumentException("no service of type " + type);
        return (T) v;
    }

    @Override
    public void close() throws Exception {
        if (indexWriter != null && indexWriter != IndexWriter.NOOP) indexWriter.close();
    }

    // ---- Builder --------------------------------------------------------

    public static final class Builder {
        private DocumentStore  documentStore;
        private ReferenceStore referenceStore;
        private FolderStore    folderStore;
        private AclStore       aclStore;
        private TagStore       tagStore;
        private BlobStore      blobStore;
        private Path luceneDir;
        private final Map<Class<?>, Object> extras = new HashMap<>();

        public Builder documentStore(DocumentStore v)   { this.documentStore = v; return this; }
        public Builder referenceStore(ReferenceStore v) { this.referenceStore = v; return this; }
        public Builder folderStore(FolderStore v)       { this.folderStore = v; return this; }
        public Builder aclStore(AclStore v)             { this.aclStore = v; return this; }
        public Builder tagStore(TagStore v)             { this.tagStore = v; return this; }
        public Builder blobStore(BlobStore v)           { this.blobStore = v; return this; }
        public Builder withLucene(Path dir)             { this.luceneDir = dir; return this; }
        public <T> Builder with(Class<T> type, T instance) { extras.put(type, instance); return this; }

        public DmsContext build() throws IOException { return new DmsContext(this); }
    }
}
