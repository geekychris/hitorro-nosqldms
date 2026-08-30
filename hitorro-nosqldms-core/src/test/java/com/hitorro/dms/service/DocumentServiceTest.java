/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.hitorro.dms.blob.InMemoryBlobStore;
import com.hitorro.dms.model.ContentRef;
import com.hitorro.dms.model.Document;
import com.hitorro.dms.model.VersionLabel;
import com.hitorro.dms.store.mem.InMemoryDocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the copy-on-write rendition split, version bump precedence,
 *  and the head-management contract. */
class DocumentServiceTest {

    private InMemoryDocumentStore docs;
    private InMemoryBlobStore blobs;
    private DocumentService svc;

    @BeforeEach
    void setup() {
        docs = new InMemoryDocumentStore();
        blobs = new InMemoryBlobStore();
        svc = new DocumentService(docs, blobs, null);
    }

    // ---- create ---------------------------------------------------------

    @Test
    void create_produces_1_0_0_and_marks_head() throws Exception {
        CreateRequest req = new CreateRequest();
        req.title = "Spec";
        req.contentType = "wiki-page";
        req.createdBy = "user:alice";
        Document d = svc.create(req);

        assertThat(d.versionLabel).isEqualTo("1.0.0");
        assertThat(d.versionMajor).isEqualTo(1);
        assertThat(d.versionMinor).isEqualTo(0);
        assertThat(d.versionPatch).isEqualTo(0);
        assertThat(d.isHead).isTrue();
        assertThat(d.isStable).isTrue();
        assertThat(d.canonicalId).startsWith("doc-");
        assertThat(d.versionId).startsWith("ver-");

        assertThat(svc.getHead(d.canonicalId)).isPresent().get().extracting(x -> x.versionId).isEqualTo(d.versionId);
    }

    @Test
    void create_attaches_initial_renditions() throws Exception {
        CreateRequest req = new CreateRequest();
        req.title = "Photo";
        req.createdBy = "u";
        req.withRendition("primary", "image/jpeg", "PRIMARY-BYTES".getBytes());
        req.withRendition("thumbnail", "image/jpeg", "THUMB-BYTES".getBytes());
        Document d = svc.create(req);

        assertThat(d.contentRefs).hasSize(2)
                .extracting(c -> c.role).containsExactly("primary", "thumbnail");
        assertThat(d.contentRefs).allSatisfy(c ->
                assertThat(c.sourceVersionId).isEqualTo(d.versionId));
    }

    // ---- check-in versioning bumps -------------------------------------

    @Test
    void checkIn_minor_bump_advances_label_and_swaps_head() throws Exception {
        Document v1 = svc.create(newDoc("Spec"));
        CheckInRequest req = new CheckInRequest();
        req.canonicalId = v1.canonicalId;
        req.body = "updated body";
        req.modifiedBy = "user:bob";
        req.bumpKind = VersionLabel.Kind.MINOR;
        Document v2 = svc.checkIn(req);

        assertThat(v2.versionLabel).isEqualTo("1.1.0");
        assertThat(v2.isHead).isTrue();
        assertThat(v2.parentVersion).isEqualTo(v1.versionId);
        assertThat(v2.body).isEqualTo("updated body");
        // Head has moved to v2; v1 no longer marked head
        assertThat(svc.getHead(v1.canonicalId).get().versionId).isEqualTo(v2.versionId);
        assertThat(svc.getVersionById(v1.versionId).get().isHead).isFalse();
    }

    @Test
    void checkIn_major_bump_zeroes_minor_and_patch() throws Exception {
        Document v1 = svc.create(newDoc("d"));
        // 1.0.0 → 1.0.1 → 1.1.0 → 2.0.0
        Document v2 = bumpMinor(v1); assertThat(v2.versionLabel).isEqualTo("1.1.0");
        Document v3 = bumpMinor(v2); assertThat(v3.versionLabel).isEqualTo("1.2.0");
        Document v4 = bumpMajor(v3); assertThat(v4.versionLabel).isEqualTo("2.0.0");
    }

    @Test
    void checkIn_patch_bump_only_increments_patch() throws Exception {
        Document v1 = svc.create(newDoc("d"));
        Document v2 = bump(v1, VersionLabel.Kind.PATCH, null);
        assertThat(v2.versionLabel).isEqualTo("1.0.1");
    }

    @Test
    void checkIn_qualifier_enters_prerelease_cycle() throws Exception {
        Document v1 = svc.create(newDoc("d"));
        Document v2 = bump(v1, VersionLabel.Kind.MINOR, "beta");
        assertThat(v2.versionLabel).isEqualTo("1.1.0-beta1");
        assertThat(v2.isStable).isFalse();
        assertThat(v2.versionQualifier).isEqualTo("beta");
        assertThat(v2.versionQualNumber).isEqualTo(1);
    }

    @Test
    void checkIn_build_number_monotonic_across_the_lineage() throws Exception {
        Document v1 = svc.create(newDoc("d"));
        Document v2 = bumpMinor(v1);
        Document v3 = bumpMinor(v2);
        assertThat(v1.versionBuild).isEqualTo(1);
        assertThat(v2.versionBuild).isEqualTo(2);
        assertThat(v3.versionBuild).isEqualTo(3);
    }

    // ---- COPY-ON-WRITE: the important tests ----------------------------

    @Test
    void checkIn_without_renditions_SHARES_all_renditions() throws Exception {
        // v1 with two renditions
        Document v1 = svc.create(newDoc("d")
                .withRendition("primary",   "image/jpeg", "IMG".getBytes())
                .withRendition("thumbnail", "image/jpeg", "THM".getBytes()));

        // v2 = metadata-only bump — supplies no renditions
        CheckInRequest req = new CheckInRequest();
        req.canonicalId = v1.canonicalId;
        req.title = "renamed";
        Document v2 = svc.checkIn(req);

        // Manifest must be identical entry-for-entry by hash
        assertThat(v2.contentRefs).hasSize(2);
        for (int i = 0; i < 2; i++) {
            ContentRef a = v1.contentRefs.get(i);
            ContentRef b = v2.contentRefs.get(i);
            assertThat(b.role).isEqualTo(a.role);
            assertThat(b.sha256).isEqualTo(a.sha256);   // ← shared blob
            assertThat(b.sourceVersionId).isEqualTo(a.sourceVersionId);  // ← still credited to v1
        }
        // And the blob store shows only two entries (no duplicate bytes stored)
        assertThat(blobs.allHashes()).hasSize(2);
    }

    @Test
    void checkIn_replacing_only_primary_SPLITS_only_that_rendition() throws Exception {
        Document v1 = svc.create(newDoc("d")
                .withRendition("primary",   "image/jpeg", "IMG-A".getBytes())
                .withRendition("thumbnail", "image/jpeg", "THM-A".getBytes()));

        CheckInRequest req = new CheckInRequest();
        req.canonicalId = v1.canonicalId;
        req.withRendition("primary", "image/jpeg", "IMG-B".getBytes());   // NEW bytes
        Document v2 = svc.checkIn(req);

        // v2 has both renditions
        ContentRef v2Primary = findRole(v2, "primary");
        ContentRef v2Thumb   = findRole(v2, "thumbnail");
        ContentRef v1Primary = findRole(v1, "primary");
        ContentRef v1Thumb   = findRole(v1, "thumbnail");

        // primary SPLIT — different hash, sourceVersionId now points at v2
        assertThat(v2Primary.sha256).isNotEqualTo(v1Primary.sha256);
        assertThat(v2Primary.sourceVersionId).isEqualTo(v2.versionId);

        // thumbnail STILL SHARED — same hash, still credited to v1
        assertThat(v2Thumb.sha256).isEqualTo(v1Thumb.sha256);
        assertThat(v2Thumb.sourceVersionId).isEqualTo(v1.versionId);

        // Blob store now has 3 entries: two primaries + one shared thumb
        assertThat(blobs.allHashes()).hasSize(3);
    }

    @Test
    void checkIn_adding_a_new_role_appends_without_touching_others() throws Exception {
        Document v1 = svc.create(newDoc("d")
                .withRendition("primary", "image/jpeg", "IMG".getBytes()));

        CheckInRequest req = new CheckInRequest();
        req.canonicalId = v1.canonicalId;
        req.withRendition("extract", "text/plain", "extracted text".getBytes());
        Document v2 = svc.checkIn(req);

        // primary still shared; extract is new (sourceVersionId = v2)
        assertThat(v2.contentRefs).hasSize(2);
        assertThat(findRole(v2, "primary").sha256).isEqualTo(findRole(v1, "primary").sha256);
        assertThat(findRole(v2, "extract").sourceVersionId).isEqualTo(v2.versionId);
    }

    @Test
    void identical_bytes_across_two_versions_dedup_to_one_blob() throws Exception {
        Document v1 = svc.create(newDoc("d")
                .withRendition("primary", "image/jpeg", "SAME".getBytes()));
        CheckInRequest req = new CheckInRequest();
        req.canonicalId = v1.canonicalId;
        req.withRendition("primary", "image/jpeg", "SAME".getBytes());   // same bytes
        Document v2 = svc.checkIn(req);

        // Same hash → same blob (content-addressed dedup)
        assertThat(findRole(v2, "primary").sha256).isEqualTo(findRole(v1, "primary").sha256);
        assertThat(blobs.allHashes()).hasSize(1);
        // But sourceVersionId still updated to v2 because we deliberately supplied a replacement.
        assertThat(findRole(v2, "primary").sourceVersionId).isEqualTo(v2.versionId);
    }

    // ---- in-place rendition attach (thumbnail worker) ------------------

    @Test
    void attachRendition_mutates_version_without_bumping_it() throws Exception {
        Document v1 = svc.create(newDoc("d")
                .withRendition("primary", "image/jpeg", "IMG".getBytes()));

        // Simulate a thumbnail worker
        Document v1Prime = svc.attachRendition(v1.canonicalId, v1.versionId,
                "thumbnail", "image/jpeg", "T".getBytes(),
                "pipeline:thumbnail-worker", "primary");

        assertThat(v1Prime.versionId).isEqualTo(v1.versionId);   // SAME version
        assertThat(v1Prime.versionLabel).isEqualTo("1.0.0");
        assertThat(v1Prime.contentRefs).hasSize(2);
        ContentRef th = findRole(v1Prime, "thumbnail");
        assertThat(th.generatedBy).isEqualTo("pipeline:thumbnail-worker");
        assertThat(th.derivedFromRole).isEqualTo("primary");
    }

    @Test
    void deleteRendition_removes_the_entry() throws Exception {
        Document v1 = svc.create(newDoc("d")
                .withRendition("primary", "image/jpeg", "IMG".getBytes())
                .withRendition("thumbnail", "image/jpeg", "T".getBytes()));

        svc.deleteRendition(v1.canonicalId, v1.versionId, "thumbnail");
        Document reloaded = svc.getVersionById(v1.versionId).get();
        assertThat(reloaded.contentRefs).hasSize(1).allSatisfy(c ->
                assertThat(c.role).isEqualTo("primary"));
    }

    // ---- read paths -----------------------------------------------------

    @Test
    void readRendition_returns_stored_bytes() throws Exception {
        Document v1 = svc.create(newDoc("d")
                .withRendition("primary", "image/jpeg", "HELLO".getBytes()));
        byte[] read = svc.readRendition(v1.canonicalId, v1.versionId, "primary").orElseThrow();
        assertThat(new String(read)).isEqualTo("HELLO");
    }

    @Test
    void listVersions_returns_insertion_order() throws Exception {
        Document v1 = svc.create(newDoc("d"));
        Document v2 = bumpMinor(v1);
        Document v3 = bumpMinor(v2);
        List<Document> list = svc.listVersions(v1.canonicalId);
        assertThat(list).extracting(d -> d.versionLabel).containsExactly("1.0.0", "1.1.0", "1.2.0");
    }

    @Test
    void tombstone_marks_the_canonical_soft_deleted() throws Exception {
        Document v1 = svc.create(newDoc("d"));
        svc.tombstone(v1.canonicalId);
        assertThat(docs.isTombstoned(v1.canonicalId)).isTrue();
    }

    // ---- helpers --------------------------------------------------------

    private CreateRequest newDoc(String title) {
        CreateRequest r = new CreateRequest();
        r.title = title;
        r.contentType = "wiki-page";
        r.createdBy = "u";
        return r;
    }

    private Document bumpMinor(Document prev) throws Exception {
        return bump(prev, VersionLabel.Kind.MINOR, null);
    }
    private Document bumpMajor(Document prev) throws Exception {
        return bump(prev, VersionLabel.Kind.MAJOR, null);
    }
    private Document bump(Document prev, VersionLabel.Kind kind, String qualifier) throws Exception {
        CheckInRequest req = new CheckInRequest();
        req.canonicalId = prev.canonicalId;
        req.bumpKind = kind;
        req.qualifier = qualifier;
        return svc.checkIn(req);
    }

    private static ContentRef findRole(Document d, String role) {
        return d.contentRefs.stream().filter(c -> role.equals(c.role))
                .findFirst().orElseThrow(() -> new AssertionError("no rendition: " + role));
    }
}
