/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.config;

import com.hitorro.dms.blob.BlobStore;
import com.hitorro.dms.context.DmsContext;
import com.hitorro.dms.index.IndexSearcher;
import com.hitorro.dms.index.IndexWriter;
import com.hitorro.dms.service.DocumentService;
import com.hitorro.dms.service.TypeRegistry;
import com.hitorro.dms.store.AclStore;
import com.hitorro.dms.store.DocumentStore;
import com.hitorro.dms.store.FolderStore;
import com.hitorro.dms.store.ReferenceStore;
import com.hitorro.dms.store.TagStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Spring wiring for the DMS. Contributes one {@link DmsContext} bean
 * and exposes each service as its own bean for convenience — controllers
 * can @Autowire the specific interface they need or the whole context.
 *
 * <p>Storage impls default to in-memory (phase 1); swap them by
 * providing your own bean of {@link DocumentStore}, {@link BlobStore},
 * etc. — Spring's normal autowiring precedence takes over.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DmsProperties.class)
public class DmsAutoConfiguration implements DisposableBean {

    private DmsContext ctx;

    @Bean
    public DmsContext dmsContext(DmsProperties props) throws IOException {
        DmsContext.Builder b = DmsContext.builder();
        if (props.isLuceneEnabled()) {
            Path luceneDir = props.getLuceneDir() != null
                    ? Path.of(props.getLuceneDir())
                    : Path.of(props.getHome(), "lucene");
            b.withLucene(luceneDir);
        }
        Path typesDir = props.getTypesDir() != null
                ? Path.of(props.getTypesDir())
                : Path.of(props.getHome(), "types");
        b.withTypesDir(typesDir);
        this.ctx = b.build();
        return ctx;
    }

    @Bean public DocumentService documentService(DmsContext c) { return c.documentService(); }
    @Bean public TypeRegistry    typeRegistry(DmsContext c)    { return c.typeRegistry(); }
    @Bean public DocumentStore   documentStore(DmsContext c)   { return c.documentStore(); }
    @Bean public ReferenceStore  referenceStore(DmsContext c)  { return c.referenceStore(); }
    @Bean public FolderStore     folderStore(DmsContext c)     { return c.folderStore(); }
    @Bean public AclStore        aclStore(DmsContext c)        { return c.aclStore(); }
    @Bean public TagStore        tagStore(DmsContext c)        { return c.tagStore(); }
    @Bean public BlobStore       blobStore(DmsContext c)       { return c.blobStore(); }
    // NOTE: LuceneIndex implements BOTH IndexWriter AND IndexSearcher, so
    // publishing both as beans creates a NoUniqueBeanDefinitionException
    // for any @Autowired IndexSearcher. Publish only the searcher — users
    // needing IndexWriter can pull it from DmsContext.indexWriter().
    @Bean public IndexSearcher   indexSearcher(DmsContext c)   { return c.indexSearcher(); }

    @Override
    public void destroy() throws Exception {
        if (ctx != null) ctx.close();
    }
}
