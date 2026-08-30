/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.web;

import com.hitorro.dms.index.IndexSearcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Lucene-backed full-text search. Query grammar is Lucene's classic
 * QueryParser — {@code field:value AND another:value} etc.
 *
 * <p>Nullable via ObjectProvider so the module boots even with
 * Lucene disabled ({@code dms.lucene-enabled=false}).</p>
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final ObjectProvider<IndexSearcher> searcher;

    public SearchController(ObjectProvider<IndexSearcher> searcher) { this.searcher = searcher; }

    @GetMapping
    public ResponseEntity<?> search(@RequestParam("q") String q,
                                     @RequestParam(value = "limit", defaultValue = "20") int limit) {
        IndexSearcher s = searcher.getIfAvailable();
        if (s == null) {
            return ResponseEntity.status(503).body(Map.of("error", "search is disabled"));
        }
        List<IndexSearcher.SearchHit> hits = s.search(q, Math.min(200, Math.max(1, limit)));
        return ResponseEntity.ok(hits);
    }
}
