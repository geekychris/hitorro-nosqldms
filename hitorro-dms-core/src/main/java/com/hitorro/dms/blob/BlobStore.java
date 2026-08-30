/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.blob;

import com.hitorro.dms.model.Blob;

import java.io.IOException;
import java.util.Optional;

/**
 * Content-addressed blob storage. The store computes sha256 on put
 * and returns it; same bytes → same hash → same stored entity
 * (dedup for free).
 */
public interface BlobStore {

    /** Store bytes; return the resulting {@link Blob} metadata.
     *  Idempotent: putting the same bytes twice returns the same hash
     *  and stores once. */
    Blob put(byte[] bytes, String mime) throws IOException;

    /** Retrieve bytes by hash. */
    Optional<byte[]> get(String sha256) throws IOException;

    /** Metadata lookup without reading the body. */
    Optional<Blob> stat(String sha256);

    /** Explicit delete (typically only called by GC after mark-and-sweep). */
    void delete(String sha256) throws IOException;

    /** True when the store has this hash. Cheap — should not read the body. */
    boolean exists(String sha256);
}
