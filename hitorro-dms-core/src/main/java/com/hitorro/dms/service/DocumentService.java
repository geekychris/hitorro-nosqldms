/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.hitorro.dms.blob.BlobStore;
import com.hitorro.dms.index.IndexWriter;
import com.hitorro.dms.model.Blob;
import com.hitorro.dms.model.ContentRef;
import com.hitorro.dms.model.Document;
import com.hitorro.dms.model.VersionLabel;
import com.hitorro.dms.store.DocumentStore;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates document lifecycle across storage + blob + index.
 * Framework-neutral — no Spring, no annotations. Wire in via
 * {@code DmsContext} for standalone, {@code @Bean} for Spring.
 *
 * <h3>Copy-on-write semantics</h3>
 * <p>{@link #checkIn} builds the new version's rendition manifest by
 * <b>shallow-copying</b> the previous head's — every entry keeps its
 * {@code sha256} so bytes are shared. Only roles named in the
 * {@link CheckInRequest#renditions} map are replaced; every other
 * role stays shared with the previous head.</p>
 */
public class DocumentService {

    private final DocumentStore docs;
    private final BlobStore     blobs;
    private final IndexWriter   index;   // optional — may be a no-op

    public DocumentService(DocumentStore docs, BlobStore blobs, IndexWriter index) {
        this.docs = docs;
        this.blobs = blobs;
        this.index = index == null ? IndexWriter.NOOP : index;
    }

    /** Create a brand-new document at version 1.0.0. */
    public Document create(CreateRequest req) throws IOException {
        String canonicalId = "doc-" + UUID.randomUUID();
        Document d = newVersion(canonicalId, VersionLabel.initial(), null,
                                1, "release", req.createdBy, req.createdBy);
        d.title       = req.title;
        d.body        = req.body;
        d.description = req.description;
        d.contentType = req.contentType;
        // Attach initial renditions.
        for (var r : req.renditions.values()) {
            Blob b = blobs.put(r.bytes, r.mime);
            d.contentRefs.add(makeContentRef(r.role, r.mime, b, d.versionId, ContentRef.GENERATED_BY_USER));
        }
        d.isHead = true;
        docs.putVersion(d);
        docs.setHead(canonicalId, d.versionId);
        index.indexDocument(d);
        return d;
    }

    /** Check in a new version. Copy-on-write per rendition. */
    public Document checkIn(CheckInRequest req) throws IOException {
        Document head = docs.getHead(req.canonicalId)
                .orElseThrow(() -> new IllegalArgumentException("no document: " + req.canonicalId));

        VersionLabel prev = VersionLabel.parse(head.versionLabel);
        VersionLabel next = req.qualifier == null
                ? prev.bump(req.bumpKind, null)
                : prev.bump(req.bumpKind, req.qualifier);

        Document nu = newVersion(req.canonicalId, next, head.versionId,
                                 head.versionBuild + 1, req.versionKind,
                                 head.createdBy, req.modifiedBy);

        // Content-defining metadata: prefer request, fall back to previous head.
        nu.title       = req.title       != null ? req.title       : head.title;
        nu.body        = req.body        != null ? req.body        : head.body;
        nu.description = req.description != null ? req.description : head.description;
        nu.contentType = req.contentType != null ? req.contentType : head.contentType;

        // Shallow-copy the manifest — every entry still points at the same
        // sha256 → every rendition is shared until an entry below replaces it.
        nu.contentRefs = Document.shallowCopyManifest(head.contentRefs);

        // Apply replacement renditions from the request.
        for (var r : req.renditions.values()) {
            Blob b = blobs.put(r.bytes, r.mime);
            ContentRef fresh = makeContentRef(r.role, r.mime, b, nu.versionId, ContentRef.GENERATED_BY_USER);
            replaceOrAppendRendition(nu.contentRefs, fresh);
        }

        // Flip is_head on the new + demote the old.
        nu.isHead = true;
        head.isHead = false;
        docs.putVersion(head);         // rewrite old head with is_head=false so index stays honest
        docs.putVersion(nu);
        docs.setHead(req.canonicalId, nu.versionId);

        index.indexDocument(head);
        index.indexDocument(nu);
        return nu;
    }

    /** In-place attach a derived rendition to an existing version (e.g. a pipeline
     *  computed a thumbnail). Does NOT bump the version. */
    public Document attachRendition(String canonicalId, String versionId,
                                    String role, String mime, byte[] bytes,
                                    String generatedBy, String derivedFromRole) throws IOException {
        Document d = docs.getVersionById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("no version: " + versionId));
        if (!d.canonicalId.equals(canonicalId))
            throw new IllegalArgumentException("versionId does not belong to canonical " + canonicalId);
        Blob b = blobs.put(bytes, mime);
        ContentRef fresh = makeContentRef(role, mime, b, versionId, generatedBy);
        fresh.derivedFromRole = derivedFromRole;
        replaceOrAppendRendition(d.contentRefs, fresh);
        docs.putVersion(d);
        index.indexDocument(d);
        return d;
    }

    /** Remove a rendition from a version. Bytes are unref'd; GC sweeps orphaned blobs later. */
    public void deleteRendition(String canonicalId, String versionId, String role) throws IOException {
        Document d = docs.getVersionById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("no version: " + versionId));
        d.contentRefs.removeIf(c -> role.equals(c.role));
        docs.putVersion(d);
        index.indexDocument(d);
    }

    /** Fetch bytes for one rendition of one version. */
    public Optional<byte[]> readRendition(String canonicalId, String versionId, String role) throws IOException {
        Document d = docs.getVersionById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("no version: " + versionId));
        return d.contentRefs.stream()
                .filter(c -> role.equals(c.role))
                .findFirst()
                .flatMap(c -> {
                    try { return blobs.get(c.sha256); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
    }

    /** Soft-delete. */
    public void tombstone(String canonicalId) {
        docs.tombstone(canonicalId);
    }

    // ---- Read pass-throughs -----------------------------------------------

    public Optional<Document> getHead(String canonicalId) { return docs.getHead(canonicalId); }
    public Optional<Document> getVersion(String canonicalId, String label) { return docs.getVersion(canonicalId, label); }
    public Optional<Document> getVersionById(String versionId) { return docs.getVersionById(versionId); }
    public List<Document> listVersions(String canonicalId) { return docs.listVersions(canonicalId); }
    public List<String> listCanonicals() { return docs.listCanonicals(); }

    // ---- helpers ----------------------------------------------------------

    private Document newVersion(String canonicalId, VersionLabel label, String parentVersion,
                                long build, String kind, String createdBy, String modifiedBy) {
        Document d = new Document();
        d.versionId          = "ver-" + UUID.randomUUID();
        d.canonicalId        = canonicalId;
        d.versionLabel       = label.label();
        d.versionMajor       = label.major;
        d.versionMinor       = label.minor;
        d.versionPatch       = label.patch;
        d.versionQualifier   = label.qualifier;
        d.versionQualNumber  = label.qualNumber;
        d.versionBuild       = build;
        d.versionKind        = kind == null ? (label.isStable() ? "release" : "draft") : kind;
        d.parentVersion      = parentVersion;
        d.isStable           = label.isStable();
        d.createdBy          = createdBy;
        d.modifiedBy         = modifiedBy;
        d.createdAt          = Instant.now();
        d.modifiedAt         = d.createdAt;
        return d;
    }

    private ContentRef makeContentRef(String role, String mime, Blob b,
                                      String versionId, String generatedBy) {
        ContentRef r = new ContentRef(role, mime, b.sizeBytes, b.sha256, b.storageUrl);
        r.generatedBy = generatedBy;
        r.sourceVersionId = versionId;
        r.attachedAt = Instant.now();
        return r;
    }

    private static void replaceOrAppendRendition(List<ContentRef> manifest, ContentRef fresh) {
        for (int i = 0; i < manifest.size(); i++) {
            if (fresh.role.equals(manifest.get(i).role)) {
                manifest.set(i, fresh);
                return;
            }
        }
        manifest.add(fresh);
    }
}
