/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry in a document version's rendition manifest — points at a
 * content blob by content-address (sha256).
 *
 * <p>The role discriminates renditions ({@code primary}, {@code thumbnail},
 * {@code text-extract}, {@code transcript}, {@code ocr}, …). A version
 * carries a list of these; entries with the same {@code role} across
 * two versions that both have the same {@code sha256} are the
 * "shared" case — no bytes were copied between them.</p>
 *
 * <p>Renditions with {@code generatedBy} starting with {@code "pipeline:"}
 * are regeneratable — a pipeline can rebuild them from the
 * {@code derivedFromRole} source of truth.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentRef {

    public static final String GENERATED_BY_USER = "user";
    public static final String ROLE_PRIMARY   = "primary";
    public static final String ROLE_THUMBNAIL = "thumbnail";
    public static final String ROLE_EXTRACT   = "text-extract";

    public String role;
    public String mime;
    public long   sizeBytes;
    /** Content address. Same bytes ⇒ same hash ⇒ same blob. */
    public String sha256;
    /** {@code blob://{sha256}} / {@code minio://…} / {@code s3://…} / {@code file://…}. */
    public String url;
    /** For small blobs (~≤4 KB), the base64 bytes inline instead of a blob-store round-trip. */
    public String inline;

    /** {@code "user"} (source of truth) or {@code "pipeline:{name}"} (derived; regeneratable). */
    public String generatedBy;
    /** For derived renditions: the role this was computed from. */
    public String derivedFromRole;
    /** The version_id that first attached this specific entry (this role, this hash). */
    public String sourceVersionId;
    public Instant attachedAt;

    public ContentRef() { }

    public ContentRef(String role, String mime, long sizeBytes, String sha256, String url) {
        this.role = role;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.url = url;
        this.generatedBy = GENERATED_BY_USER;
        this.attachedAt = Instant.now();
    }

    /** True when this ref points at the same blob as {@code other} (same role + same hash).
     *  If both are true, the two versions share bytes for this role. */
    public boolean sharesBytesWith(ContentRef other) {
        return other != null
                && Objects.equals(role, other.role)
                && Objects.equals(sha256, other.sha256)
                && sha256 != null;
    }
}
