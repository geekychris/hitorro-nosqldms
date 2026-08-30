/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.blob;

import com.hitorro.dms.model.Blob;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory blob store — dedups by sha256, keeps bytes in a
 * ConcurrentHashMap. Fine for tests + small-footprint runs; swap
 * for a filesystem/MinIO/S3 impl in production.
 */
public class InMemoryBlobStore implements BlobStore {

    private final Map<String, byte[]> bytesByHash = new ConcurrentHashMap<>();
    private final Map<String, Blob>   metaByHash  = new ConcurrentHashMap<>();

    @Override
    public Blob put(byte[] bytes, String mime) throws IOException {
        String hash = sha256Hex(bytes);
        // Content-addressed: identical bytes → identical hash → single stored copy.
        bytesByHash.putIfAbsent(hash, bytes);
        metaByHash.computeIfAbsent(hash, k -> new Blob(hash, mime, bytes.length, "blob://" + hash));
        return metaByHash.get(hash);
    }

    @Override
    public Optional<byte[]> get(String sha256) {
        byte[] b = bytesByHash.get(sha256);
        return b == null ? Optional.empty() : Optional.of(b);
    }

    @Override
    public Optional<Blob> stat(String sha256) {
        return Optional.ofNullable(metaByHash.get(sha256));
    }

    @Override
    public void delete(String sha256) {
        bytesByHash.remove(sha256);
        metaByHash.remove(sha256);
    }

    @Override
    public boolean exists(String sha256) {
        return metaByHash.containsKey(sha256);
    }

    /** Live hash set — for GC diagnostics + tests. */
    public java.util.Set<String> allHashes() { return metaByHash.keySet(); }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
