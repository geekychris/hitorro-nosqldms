/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.model.FolderMembership;
import com.hitorro.dms.store.FolderStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FoldersController {

    private final FolderStore folders;

    public FoldersController(FolderStore folders) { this.folders = folders; }

    /** {@code GET /api/folders/{folder}/contents} — direct children. */
    @GetMapping("/{folder}/contents")
    public List<FolderMembership> contents(@PathVariable("folder") String folder) {
        return folders.listChildren(folder);
    }

    /** {@code POST /api/folders/{folder}/contents} — link a doc into this folder.
     *  Body: {@code {"child":"doc-…","addedBy":"user:alice"}}. */
    @PostMapping("/{folder}/contents")
    public ResponseEntity<Void> link(@PathVariable("folder") String folder,
                                      @RequestBody Map<String, String> body) {
        String child = body.get("child");
        String by    = body.getOrDefault("addedBy", "unknown");
        if (child == null) return ResponseEntity.badRequest().build();
        folders.link(folder, child, by);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{folder}/contents/{child}")
    public ResponseEntity<Void> unlink(@PathVariable("folder") String folder,
                                        @PathVariable("child") String child) {
        folders.unlink(folder, child);
        return ResponseEntity.noContent().build();
    }

    /** {@code GET /api/folders/for-doc/{child}} — every folder containing this doc. */
    @GetMapping("/for-doc/{child}")
    public List<FolderMembership> foldersFor(@PathVariable("child") String child) {
        return folders.listContainingFolders(child);
    }
}
