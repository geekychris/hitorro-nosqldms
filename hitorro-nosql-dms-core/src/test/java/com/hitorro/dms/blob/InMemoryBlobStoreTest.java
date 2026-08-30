/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.blob;

import com.hitorro.dms.model.Blob;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBlobStoreTest {

    @Test
    void put_returns_metadata_with_sha256() throws Exception {
        InMemoryBlobStore s = new InMemoryBlobStore();
        Blob b = s.put("hello".getBytes(), "text/plain");
        assertThat(b.sha256).hasSize(64);   // 32 bytes hex
        assertThat(b.sizeBytes).isEqualTo(5);
        assertThat(b.mime).isEqualTo("text/plain");
        assertThat(b.storageUrl).isEqualTo("blob://" + b.sha256);
    }

    @Test
    void put_is_idempotent_content_addressed() throws Exception {
        InMemoryBlobStore s = new InMemoryBlobStore();
        Blob a = s.put("same".getBytes(), "text/plain");
        Blob b = s.put("same".getBytes(), "text/plain");
        assertThat(a.sha256).isEqualTo(b.sha256);
        assertThat(s.allHashes()).hasSize(1);
    }

    @Test
    void different_bytes_different_hashes() throws Exception {
        InMemoryBlobStore s = new InMemoryBlobStore();
        Blob a = s.put("one".getBytes(), "text/plain");
        Blob b = s.put("two".getBytes(), "text/plain");
        assertThat(a.sha256).isNotEqualTo(b.sha256);
        assertThat(s.allHashes()).hasSize(2);
    }

    @Test
    void get_returns_original_bytes() throws Exception {
        InMemoryBlobStore s = new InMemoryBlobStore();
        Blob b = s.put("data".getBytes(), "text/plain");
        assertThat(s.get(b.sha256)).isPresent().get().isEqualTo("data".getBytes());
    }

    @Test
    void exists_and_stat_do_not_require_reading_bytes() throws Exception {
        InMemoryBlobStore s = new InMemoryBlobStore();
        Blob b = s.put("x".getBytes(), "text/plain");
        assertThat(s.exists(b.sha256)).isTrue();
        assertThat(s.stat(b.sha256)).isPresent();
        assertThat(s.exists("nope")).isFalse();
    }

    @Test
    void delete_removes_both_bytes_and_metadata() throws Exception {
        InMemoryBlobStore s = new InMemoryBlobStore();
        Blob b = s.put("gone".getBytes(), "text/plain");
        s.delete(b.sha256);
        assertThat(s.exists(b.sha256)).isFalse();
        assertThat(s.get(b.sha256)).isEmpty();
    }
}
