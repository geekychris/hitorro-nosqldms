/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring;

import com.hitorro.dms.model.Document;
import com.hitorro.dms.service.CreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full Spring Boot app on a random port, hits every REST
 * surface end-to-end: create → check-in → attach rendition → read
 * rendition → link folder → grant ACL → search.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "dms.lucene-enabled=true" })
@AutoConfigureMockMvc
class DmsApplicationIntegrationTest {

    /** Point dms.home at a fresh temp dir per JVM so parallel runs don't collide
     *  and Lucene doesn't share state across test classes. */
    @DynamicPropertySource
    static void tmpHome(DynamicPropertyRegistry reg) {
        reg.add("dms.home", () -> {
            try { return Files.createTempDirectory("dms-integ-").toString(); }
            catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    @Autowired TestRestTemplate rest;
    @LocalServerPort int port;

    private String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void create_and_get_head_and_check_in_new_version() {
        CreateRequest create = new CreateRequest();
        create.title = "Integration Spec";
        create.body = "The body describes uniqueintegkey behaviour.";
        create.contentType = "wiki-page";
        create.createdBy = "user:integ";
        Document v1 = rest.postForObject(url("/api/documents"), create, Document.class);
        assertThat(v1).isNotNull();
        assertThat(v1.versionLabel).isEqualTo("1.0.0");

        Document head = rest.getForObject(url("/api/documents/" + v1.canonicalId), Document.class);
        assertThat(head.versionId).isEqualTo(v1.versionId);

        // Check-in a minor bump
        Map<String, Object> bump = Map.of("title", "Integration Spec (v2)", "bumpKind", "MINOR");
        Document v2 = rest.postForObject(url("/api/documents/" + v1.canonicalId + "/versions"),
                bump, Document.class);
        assertThat(v2.versionLabel).isEqualTo("1.1.0");
        assertThat(v2.isHead).isTrue();
    }

    @Test
    void attach_rendition_via_PUT_bytes() {
        CreateRequest create = new CreateRequest();
        create.title = "Photo doc";
        create.contentType = "photo";
        create.createdBy = "u";
        Document v1 = rest.postForObject(url("/api/documents"), create, Document.class);

        // PUT bytes as an attached rendition
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.set("X-Generated-By", "user");
        byte[] body = "FAKE-JPEG-BYTES".getBytes();
        ResponseEntity<Document> put = rest.exchange(
                url("/api/documents/" + v1.canonicalId + "/versions/" + v1.versionId + "/renditions/primary"),
                HttpMethod.PUT, new HttpEntity<>(body, headers), Document.class);
        assertThat(put.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(put.getBody().contentRefs).extracting(c -> c.role).contains("primary");

        // GET them back
        ResponseEntity<byte[]> get = rest.getForEntity(
                url("/api/documents/" + v1.canonicalId + "/renditions/primary"), byte[].class);
        assertThat(get.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(new String(get.getBody())).isEqualTo("FAKE-JPEG-BYTES");
    }

    @Test
    void folder_link_and_list() {
        CreateRequest create = new CreateRequest();
        create.title = "Folder me"; create.contentType = "wiki-page"; create.createdBy = "u";
        Document d = rest.postForObject(url("/api/documents"), create, Document.class);

        rest.postForEntity(url("/api/folders/folder-eng/contents"),
                Map.of("child", d.canonicalId, "addedBy", "alice"), Void.class);

        @SuppressWarnings("unchecked")
        var list = rest.getForObject(url("/api/folders/folder-eng/contents"), java.util.List.class);
        assertThat(list).isNotEmpty();
    }

    @Test
    void grant_and_list_acls() {
        CreateRequest create = new CreateRequest();
        create.title = "acl me"; create.contentType = "wiki-page"; create.createdBy = "u";
        Document d = rest.postForObject(url("/api/documents"), create, Document.class);

        Map<String, Object> grant = Map.of("principal", "user:alice", "permission", "read", "grant", true);
        rest.postForEntity(url("/api/documents/" + d.canonicalId + "/acls"), grant, Void.class);

        @SuppressWarnings("unchecked")
        var list = rest.getForObject(url("/api/documents/" + d.canonicalId + "/acls"), java.util.List.class);
        assertThat(list).isNotEmpty();
    }

    @Test
    void search_finds_by_body_text() {
        CreateRequest create = new CreateRequest();
        create.title = "Searchable"; create.body = "distinctivebodykeyword"; create.contentType = "wiki-page"; create.createdBy = "u";
        rest.postForObject(url("/api/documents"), create, Document.class);

        ResponseEntity<String> resp = rest.getForEntity(url("/api/search?q=distinctivebodykeyword"), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // The response is a JSON array of SearchHit records — must include the
        // canonicalId + versionLabel keys and at least one hit for our unique doc.
        assertThat(resp.getBody())
                .contains("canonicalId", "versionLabel")
                .contains("Searchable");    // title is stored + returned in the hit
    }
}
