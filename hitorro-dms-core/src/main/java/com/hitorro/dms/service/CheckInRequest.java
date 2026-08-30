/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.hitorro.dms.model.VersionLabel.Kind;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@link DocumentService#checkIn(CheckInRequest)}.
 *
 * <p>Content changes are supplied as a map of {@code role → bytes} —
 * every role in the map replaces the corresponding entry in the new
 * version's rendition manifest. Roles NOT in the map remain shared
 * with the previous head (copy-on-write). A check-in with an empty
 * or null {@code renditions} map is a metadata-only version bump —
 * zero bytes copied.</p>
 */
public class CheckInRequest {

    public String canonicalId;
    public String title;
    public String body;
    public String description;
    public String contentType;
    public String modifiedBy;

    public Kind bumpKind = Kind.MINOR;
    /** Optional pre-release qualifier for this version. */
    public String qualifier;
    /** Optional 'release' / 'draft' / 'hotfix'. */
    public String versionKind;

    /** role → (mime, bytes) — every entry here becomes a new / replacement rendition. */
    public Map<String, Rendition> renditions = new LinkedHashMap<>();

    public CheckInRequest() { }

    public CheckInRequest withRendition(String role, String mime, byte[] bytes) {
        renditions.put(role, new Rendition(role, mime, bytes));
        return this;
    }

    public static class Rendition {
        public String role;
        public String mime;
        public byte[] bytes;

        public Rendition() { }
        public Rendition(String role, String mime, byte[] bytes) {
            this.role = role;
            this.mime = mime;
            this.bytes = bytes;
        }
    }
}
