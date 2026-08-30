/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single version of a document. Immutable in spirit — the DMS
 * service should treat these as write-once at check-in time. The one
 * exception is the {@link #contentRefs} manifest, which pipelines may
 * mutate in-place to attach derived renditions (thumbnails, extracts)
 * without bumping the version.
 *
 * <p>Fields that mutate on independent lifecycles (references, folder
 * memberships, ACLs, tags) are <b>not</b> on this class — they live
 * in sibling storage keyspaces so adding a reference doesn't rewrite
 * the doc.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Document {

    /** Unique per version. Every check-in produces a new versionId. */
    public String versionId;

    /** Stable across every version of this document. */
    public String canonicalId;

    // --- version identity (queryable) ---
    public String versionLabel;
    public long   versionMajor;
    public long   versionMinor;
    public long   versionPatch;
    /** Pre-release tag (null = stable). */
    public String versionQualifier;
    /** Numeric suffix on the qualifier — 'alpha3' → 3. */
    public Long   versionQualNumber;
    /** Monotonic build number across the whole canonicalId lineage. */
    public long   versionBuild;
    /** 'release' / 'draft' / 'branch' / 'hotfix'. Coarser than qualifier. */
    public String versionKind;
    /** Direct predecessor version id (null for v1). */
    public String parentVersion;
    /** Non-null when this is the tip of a branch — points at the branched-from version. */
    public String branchOf;

    /** Denormalised — true when this is the current head for its branch. */
    public boolean isHead;
    /** Denormalised — versionQualifier == null. */
    public boolean isStable;

    // --- content-defining metadata ---
    public String title;
    public String body;
    public String description;
    public String contentType;   // 'folder', 'wiki-page', 'photo', 'video', ...

    /** Registered type name — one of the names in {@link com.hitorro.dms.service.TypeRegistry}.
     *  Defaults to {@link #contentType} when not explicitly set. */
    public String typeName;

    /** Type-specific field values. Opaque map — the DMS stores whatever
     *  the caller supplies and round-trips it verbatim, so evolving a
     *  TypeDef doesn't invalidate old docs. UI renders it as a form
     *  driven by the TypeDef's field list. */
    public java.util.Map<String, Object> typeFields = new java.util.LinkedHashMap<>();

    public String createdBy;
    public String modifiedBy;
    public Instant createdAt;
    public Instant modifiedAt;

    /** Rendition manifest — copy-on-write across versions. */
    public List<ContentRef> contentRefs = new ArrayList<>();

    // --- lifecycle ---
    public String checkedOutBy;
    public Instant checkedOutAt;
    public boolean tombstoned;

    public Document() { }

    /** Deep-copy the {@link #contentRefs} manifest — used by check-in for
     *  the shallow-share-by-hash semantics. Returns a NEW list whose
     *  entries are new ContentRef instances that share the same {@code sha256}
     *  addresses with the source. */
    public static List<ContentRef> shallowCopyManifest(List<ContentRef> source) {
        if (source == null) return new ArrayList<>();
        List<ContentRef> out = new ArrayList<>(source.size());
        for (ContentRef c : source) {
            ContentRef copy = new ContentRef();
            copy.role            = c.role;
            copy.mime            = c.mime;
            copy.sizeBytes       = c.sizeBytes;
            copy.sha256          = c.sha256;   // same hash → shared blob
            copy.url             = c.url;
            copy.inline          = c.inline;
            copy.generatedBy     = c.generatedBy;
            copy.derivedFromRole = c.derivedFromRole;
            copy.sourceVersionId = c.sourceVersionId;   // preserved — this rendition entry was first attached at that version
            copy.attachedAt      = c.attachedAt;
            out.add(copy);
        }
        return out;
    }
}
