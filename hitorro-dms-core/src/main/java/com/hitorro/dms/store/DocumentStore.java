/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store;

import com.hitorro.dms.model.Document;

import java.util.List;
import java.util.Optional;

/**
 * Authoritative store for {@link Document} versions.
 *
 * <p>Every operation on this interface corresponds to at most a
 * <b>single</b> KV keyspace operation (see the
 * <i>Minimal-update principle</i> in the design doc). Adding a
 * reference, granting an ACL, or linking to a folder do NOT touch
 * this interface — those live on {@link ReferenceStore},
 * {@link AclStore}, {@link FolderStore} respectively.</p>
 *
 * <p>Contract for implementations:</p>
 * <ul>
 *   <li>{@link #putVersion} writes one {@code v|canonical|label} entry.</li>
 *   <li>{@link #setHead} atomically updates {@code d|canonical} — one KV write.</li>
 *   <li>{@link #listVersions} is a prefix scan (must be linear in output size).</li>
 * </ul>
 */
public interface DocumentStore {

    /** Persist a version. Idempotent by (canonicalId, versionLabel). */
    void putVersion(Document doc);

    /** Update the head pointer. Atomic swap. */
    void setHead(String canonicalId, String versionId);

    /** Read the current head version, if any. */
    Optional<Document> getHead(String canonicalId);

    /** Read one specific version by label. */
    Optional<Document> getVersion(String canonicalId, String versionLabel);

    /** Read one specific version by its (unique) version id. */
    Optional<Document> getVersionById(String versionId);

    /** All versions of a document, in insertion order (== check-in order). */
    List<Document> listVersions(String canonicalId);

    /** Every known canonical id — for listings, scans, rebuild jobs. */
    List<String> listCanonicals();

    /** Soft-delete marker. */
    void tombstone(String canonicalId);
    boolean isTombstoned(String canonicalId);

    /** Remove a schedule and every version — hard delete. Used by tests + admin ops. */
    void purge(String canonicalId);
}
