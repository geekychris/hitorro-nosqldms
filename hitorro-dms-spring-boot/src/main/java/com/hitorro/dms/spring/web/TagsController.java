/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.store.TagStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TagsController {

    private final TagStore tags;

    public TagsController(TagStore tags) { this.tags = tags; }

    @GetMapping("/documents/{canonical}/tags")
    public List<String> list(@PathVariable("canonical") String canonical) {
        return tags.listTags(canonical);
    }

    @PostMapping("/documents/{canonical}/tags/{tag}")
    public ResponseEntity<Void> tag(@PathVariable("canonical") String canonical,
                                     @PathVariable("tag") String tag) {
        tags.tag(canonical, tag);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documents/{canonical}/tags/{tag}")
    public ResponseEntity<Void> untag(@PathVariable("canonical") String canonical,
                                       @PathVariable("tag") String tag) {
        tags.untag(canonical, tag);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tags/{tag}/documents")
    public List<String> docsWithTag(@PathVariable("tag") String tag) {
        return tags.listDocsWithTag(tag);
    }
}
