/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Metadata for a content-addressed blob. The bytes themselves may
 * live in the KV store (small), on disk, in MinIO, in S3, etc. —
 * this record just tells you where.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Blob {

    /** Content address. Primary key. */
    public String sha256;
    public String mime;
    public long   sizeBytes;
    public Instant firstSeenAt;
    /** Physical location — 'kv://', 'file:///…', 'minio://…', 's3://…'. */
    public String storageUrl;

    public Blob() { }

    public Blob(String sha256, String mime, long sizeBytes, String storageUrl) {
        this.sha256 = sha256;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.storageUrl = storageUrl;
        this.firstSeenAt = Instant.now();
    }
}
