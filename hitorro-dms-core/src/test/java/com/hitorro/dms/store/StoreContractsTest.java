/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.store;

import com.hitorro.dms.model.FolderMembership;
import com.hitorro.dms.model.Grant;
import com.hitorro.dms.model.Reference;
import com.hitorro.dms.store.mem.InMemoryAclStore;
import com.hitorro.dms.store.mem.InMemoryFolderStore;
import com.hitorro.dms.store.mem.InMemoryReferenceStore;
import com.hitorro.dms.store.mem.InMemoryTagStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the sibling stores: references, folders, ACLs, tags —
 *  all the concerns externalised from the doc body. */
class StoreContractsTest {

    // ---- ReferenceStore ------------------------------------------------

    @Test
    void reference_add_writes_both_outbound_and_inbound() {
        InMemoryReferenceStore rs = new InMemoryReferenceStore();
        rs.add(new Reference("A", "B", "cites"));
        rs.add(new Reference("A", "C", "cites"));
        rs.add(new Reference("D", "B", "supersedes"));

        assertThat(rs.listOutbound("A")).extracting(r -> r.toCanonical).containsExactly("B", "C");
        assertThat(rs.listInbound("B")).extracting(r -> r.fromCanonical).containsExactly("A", "D");
        assertThat(rs.listInbound("C")).extracting(r -> r.fromCanonical).containsExactly("A");
    }

    @Test
    void reference_remove_scoped_by_kind() {
        InMemoryReferenceStore rs = new InMemoryReferenceStore();
        rs.add(new Reference("A", "B", "cites"));
        rs.add(new Reference("A", "B", "attached-to"));

        rs.remove("A", "B", "cites");
        assertThat(rs.listOutbound("A")).extracting(r -> r.kind).containsExactly("attached-to");
        assertThat(rs.listInbound("B")).extracting(r -> r.kind).containsExactly("attached-to");
    }

    // ---- FolderStore ---------------------------------------------------

    @Test
    void folder_link_supports_many_to_many() {
        InMemoryFolderStore fs = new InMemoryFolderStore();
        fs.link("folder-eng", "doc-1", "alice");
        fs.link("folder-eng", "doc-2", "alice");
        fs.link("folder-archive", "doc-1", "bob");

        assertThat(fs.listChildren("folder-eng")).extracting(m -> m.childCanonical)
                .containsExactly("doc-1", "doc-2");
        assertThat(fs.listContainingFolders("doc-1")).extracting(m -> m.folderCanonical)
                .containsExactly("folder-eng", "folder-archive");   // doc in 2 folders
    }

    @Test
    void folder_unlink_removes_from_both_sides() {
        InMemoryFolderStore fs = new InMemoryFolderStore();
        fs.link("F", "C", "u");
        fs.unlink("F", "C");
        assertThat(fs.listChildren("F")).isEmpty();
        assertThat(fs.listContainingFolders("C")).isEmpty();
    }

    // ---- AclStore ------------------------------------------------------

    @Test
    void acl_grant_and_list_per_doc_and_per_principal() {
        InMemoryAclStore acl = new InMemoryAclStore();
        acl.grant(new Grant("doc-1", "user:alice", "read",  true));
        acl.grant(new Grant("doc-1", "user:alice", "write", true));
        acl.grant(new Grant("doc-1", "user:bob",   "read",  true));
        acl.grant(new Grant("doc-2", "user:alice", "read",  true));

        assertThat(acl.listForDoc("doc-1")).hasSize(3);
        assertThat(acl.listForPrincipal("user:alice")).hasSize(3)
                .extracting(g -> g.canonicalId).containsExactlyInAnyOrder("doc-1", "doc-1", "doc-2");
    }

    @Test
    void acl_grant_replaces_existing_same_permission() {
        InMemoryAclStore acl = new InMemoryAclStore();
        acl.grant(new Grant("doc-1", "user:alice", "read", true));
        acl.grant(new Grant("doc-1", "user:alice", "read", false));   // deny replaces allow
        assertThat(acl.listForDoc("doc-1")).hasSize(1).allSatisfy(g -> assertThat(g.grant).isFalse());
    }

    @Test
    void acl_revoke_removes_from_both_sides() {
        InMemoryAclStore acl = new InMemoryAclStore();
        acl.grant(new Grant("doc-1", "user:alice", "read", true));
        acl.revoke("doc-1", "user:alice", "read");
        assertThat(acl.listForDoc("doc-1")).isEmpty();
        assertThat(acl.listForPrincipal("user:alice")).isEmpty();
    }

    // ---- TagStore ------------------------------------------------------

    @Test
    void tag_and_untag() {
        InMemoryTagStore t = new InMemoryTagStore();
        t.tag("doc-1", "urgent");
        t.tag("doc-1", "wip");
        t.tag("doc-2", "urgent");

        assertThat(t.listTags("doc-1")).containsExactly("urgent", "wip");
        assertThat(t.listDocsWithTag("urgent")).containsExactly("doc-1", "doc-2");

        t.untag("doc-1", "urgent");
        assertThat(t.listTags("doc-1")).containsExactly("wip");
        assertThat(t.listDocsWithTag("urgent")).containsExactly("doc-2");
    }

    @Test
    void tag_is_idempotent() {
        InMemoryTagStore t = new InMemoryTagStore();
        t.tag("doc-1", "hot");
        t.tag("doc-1", "hot");
        assertThat(t.listTags("doc-1")).containsExactly("hot");
    }
}
