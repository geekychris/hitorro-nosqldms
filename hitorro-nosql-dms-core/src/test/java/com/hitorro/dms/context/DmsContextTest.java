/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.context;

import com.hitorro.dms.model.Document;
import com.hitorro.dms.service.CreateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DmsContextTest {

    @Test
    void inMemory_wires_everything_up() throws Exception {
        try (DmsContext ctx = DmsContext.inMemory()) {
            CreateRequest r = new CreateRequest();
            r.title = "Hello";
            r.contentType = "wiki-page";
            r.createdBy = "u";
            Document d = ctx.documentService().create(r);
            assertThat(d.versionLabel).isEqualTo("1.0.0");
            assertThat(ctx.documentStore().listCanonicals()).contains(d.canonicalId);
            assertThat(ctx.indexWriter()).isNotNull();     // no-op impl
            assertThat(ctx.indexSearcher()).isNull();      // no Lucene configured
        }
    }

    @Test
    void withLucene_wires_a_real_searcher(@TempDir Path tmp) throws Exception {
        try (DmsContext ctx = DmsContext.builder().withLucene(tmp.resolve("idx")).build()) {
            CreateRequest r = new CreateRequest();
            r.title = "Search me";
            r.body = "distinct body";
            r.createdBy = "u";
            ctx.documentService().create(r);
            var hits = ctx.indexSearcher().search("distinct", 5);
            assertThat(hits).hasSize(1);
        }
    }
}
