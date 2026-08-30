/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Wire via {@code application.yml} under the {@code dms.} prefix. */
@ConfigurationProperties(prefix = "dms")
public class DmsProperties {

    /** Root directory for persistent storage. Defaults to
     *  {@code ${user.home}/.hitorro/dms}. Lucene index will be at
     *  {@code {home}/lucene} unless {@link #luceneDir} is set. */
    private String home = System.getProperty("user.home") + "/.hitorro/dms";

    /** Override for the Lucene index directory. */
    private String luceneDir;

    /** Whether to enable Lucene indexing at all (default true). */
    private boolean luceneEnabled = true;

    public String getHome() { return home; }
    public void setHome(String home) { this.home = home; }
    public String getLuceneDir() { return luceneDir; }
    public void setLuceneDir(String v) { this.luceneDir = v; }
    public boolean isLuceneEnabled() { return luceneEnabled; }
    public void setLuceneEnabled(boolean v) { this.luceneEnabled = v; }
}
