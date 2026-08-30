/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parameters for creating a brand-new document (its 1.0.0 version). */
public class CreateRequest {

    public String title;
    public String body;
    public String description;
    /** 'folder' / 'wiki-page' / 'photo' / … */
    public String contentType;
    /** Registered type name from {@link TypeRegistry}. Defaults to {@link #contentType}. */
    public String typeName;
    /** Type-specific field values — matched against the type's field defs. */
    public java.util.Map<String, Object> typeFields = new java.util.LinkedHashMap<>();
    public String createdBy;

    /** role → (mime, bytes) — attached to the initial version. */
    public Map<String, CheckInRequest.Rendition> renditions = new LinkedHashMap<>();

    public CreateRequest() { }

    public CreateRequest withRendition(String role, String mime, byte[] bytes) {
        renditions.put(role, new CheckInRequest.Rendition(role, mime, bytes));
        return this;
    }
}
