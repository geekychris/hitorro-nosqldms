package com.hitorro.dms.spring.golden;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Golden REST test: every controller's JSON output, and the JSON the DMS stores in its Lucene index
 * ({@code _source}), must be exactly what it produced on Jackson 2 (Spring Boot 4.1.3 with the
 * spring-boot-jackson2 bridge, master before the migration).
 *
 * <p>{@code src/test/resources/golden/jackson2-rest.txt} was recorded by running this same class on master
 * with {@code -Dgolden.record=true}. Random document/version ids are numbered in order of first
 * appearance and {@code Instant.now()} timestamps are masked; lists that come out of hash maps keyed by
 * random ids are compared in any order.
 *
 * <p>Controllers covered: TypesController, DocumentsController, RenditionsController, FoldersController,
 * ReferencesController, AclsController, TagsController, SearchController, plus actuator (health, info,
 * metrics) and a probe of the MVC JSON converter.
 */
@SpringBootTest(properties = {"dms.lucene-enabled=true", "golden.rest=true"})
@AutoConfigureMockMvc
@DisplayName("Golden REST responses (recorded on Jackson 2)")
class GoldenRestTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/jackson2-rest.txt");
    private static Path home;

    @DynamicPropertySource
    static void tmpHome(DynamicPropertyRegistry reg) throws IOException {
        home = Files.createTempDirectory("dms-golden-");
        reg.add("dms.home", home::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    private GoldenRecorder g;
    private final Map<String, String> ids = new LinkedHashMap<>();

    @Test
    @DisplayName("every endpoint returns the Jackson 2 JSON")
    void endpointsMatchJackson2() throws Exception {
        Pattern id = Pattern.compile("\\b(doc|ver)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
        g = new GoldenRecorder(GOLDEN)
                .normalizer(s -> {
                    Matcher m = id.matcher(s);
                    StringBuilder sb = new StringBuilder();
                    while (m.find()) {
                        String n = ids.computeIfAbsent(m.group(), k -> m.group(1) + "-#" + (ids.size() + 1));
                        m.appendReplacement(sb, n);
                    }
                    return m.appendTail(sb).toString();
                })
                // Instant.now() values: ISO strings in REST, decimal epoch seconds in the Lucene _source
                .normalizer(s -> s.replaceAll("(\"[A-Za-z]*At\"\\s*:\\s*)\"[0-9][^\"]*\"", "$1\"~\""))
                .normalizer(s -> s.replaceAll("(\"[A-Za-z]*At\"\\s*:\\s*)[0-9][0-9.eE+-]*", "$1~"))
                .replace(home.toString(), "<HOME>")
                .normalizer(s -> s.replaceAll("(\"statistic\":\"[A-Z_]+\",\"value\":)[0-9.Ee+-]+", "$10"));

        types();
        String a = documents();
        String b = id(g.rest(mvc, "document create second", json(post("/api/documents"),
                "{\"title\":\"Second\",\"contentType\":\"wiki-page\",\"createdBy\":\"bob\"}")), "canonicalId");
        renditions(a);
        folders(a, b);
        references(a, b);
        acls(a);
        tags(a, b);
        search();
        g.rest(mvc, "document tombstone", delete("/api/documents/" + b));
        g.rest(mvc, "document after tombstone", get("/api/documents/" + b));
        g.restAnyOrder(mvc, "documents after tombstone", get("/api/documents"));
        g.rest(mvc, "document tombstone missing", delete("/api/documents/doc-none"));
        g.rest(mvc, "actuator health", get("/actuator/health"));
        g.rest(mvc, "actuator info", get("/actuator/info"));
        g.rest(mvc, "actuator metric jvm.threads.daemon", get("/actuator/metrics/jvm.threads.daemon").param("tag", "nope:x"));
        g.restUnordered(mvc, "actuator links", get("/actuator"));
        g.rest(mvc, "actuator health liveness", get("/actuator/health/liveness"));
        g.rest(mvc, "actuator metric jvm.threads.daemon value", get("/actuator/metrics/jvm.threads.daemon"));
        g.rest(mvc, "actuator metric missing", get("/actuator/metrics/nope.nope"));
        lucene();
        ConverterProbe.run(ConverterProbe.jsonConverter(adapter), g);

        List<String> diffs = g.finish();
        assertTrue(diffs.isEmpty(), diffs.size() + " responses differ from Jackson 2:\n" + String.join("\n", diffs));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String id(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!m.find()) throw new IllegalStateException("no " + key + " in " + body);
        return m.group(1);
    }

    // ------------------------------------------------------------------ TypesController

    private void types() throws Exception {
        g.rest(mvc, "types", get("/api/types"));
        for (String t : List.of("dms_document", "dms_wiki_page", "dms_task", "dms_contact", "dms_folder", "dms_content_ref")) {
            g.rest(mvc, "type " + t, get("/api/types/" + t));
        }
        g.rest(mvc, "type missing", get("/api/types/nope"));
    }

    // ------------------------------------------------------------------ DocumentsController

    private String documents() throws Exception {
        String v1 = g.rest(mvc, "document create", json(post("/api/documents"),
                "{\"title\":\"Golden Spec\",\"body\":\"The body mentions goldenkeyword and \\u00e9t\\u00e9.\","
                        + "\"description\":\"desc\",\"contentType\":\"wiki-page\",\"typeName\":\"dms_wiki_page\","
                        + "\"createdBy\":\"user:alice\",\"typeFields\":{\"priority\":3,\"ratio\":1.10,\"done\":false,"
                        + "\"tags\":[\"a\",\"b\"],\"nested\":{\"k\":null,\"n\":[1,{\"x\":\"y\"}]},\"when\":\"2026-11-20\"},"
                        + "\"renditions\":{\"primary\":{\"role\":\"primary\",\"mime\":\"text/plain\",\"bytes\":\"SGVsbG8gZ29sZGVu\"}},"
                        + "\"unknownField\":1}"));
        String a = id(v1, "canonicalId");
        String ver1 = id(v1, "versionId");
        g.rest(mvc, "document create minimal", json(post("/api/documents"), "{}"));
        g.rest(mvc, "document create task", json(post("/api/documents"),
                "{\"title\":\"Task\",\"contentType\":\"task\",\"typeName\":\"dms_task\",\"typeFields\":{\"due\":\"2026-12-01\",\"assignee\":\"bob\"}}"));
        g.rest(mvc, "document create malformed", json(post("/api/documents"), "{\"title\":"));
        g.rest(mvc, "document create bad bytes", json(post("/api/documents"),
                "{\"title\":\"x\",\"renditions\":{\"p\":{\"role\":\"p\",\"bytes\":\"not base64!\"}}}"));
        g.rest(mvc, "document head", get("/api/documents/" + a));
        g.rest(mvc, "document head missing", get("/api/documents/doc-none"));
        g.rest(mvc, "document check in minor", json(post("/api/documents/" + a + "/versions"),
                "{\"title\":\"Golden Spec v2\",\"modifiedBy\":\"user:bob\",\"bumpKind\":\"MINOR\"}"));
        g.rest(mvc, "document check in major qualifier", json(post("/api/documents/" + a + "/versions"),
                "{\"bumpKind\":\"MAJOR\",\"qualifier\":\"alpha3\",\"versionKind\":\"draft\",\"typeFields\":{\"priority\":1}}"));
        g.rest(mvc, "document check in patch", json(post("/api/documents/" + a + "/versions"), "{\"bumpKind\":\"PATCH\"}"));
        g.rest(mvc, "document check in bad kind", json(post("/api/documents/" + a + "/versions"), "{\"bumpKind\":\"HUGE\"}"));
        g.rest(mvc, "document check in missing", json(post("/api/documents/doc-none/versions"), "{\"title\":\"x\"}"));
        g.rest(mvc, "document versions", get("/api/documents/" + a + "/versions"));
        g.rest(mvc, "document version 1.0.0", get("/api/documents/" + a + "/versions/1.0.0"));
        g.rest(mvc, "document version by id", get("/api/documents/" + a + "/versions/" + ver1));
        g.rest(mvc, "document version missing", get("/api/documents/" + a + "/versions/9.9.9"));
        g.restAnyOrder(mvc, "documents", get("/api/documents"));
        return a;
    }

    // ------------------------------------------------------------------ RenditionsController

    private void renditions(String a) throws Exception {
        String head = g.rest(mvc, "renditions head", get("/api/documents/" + a + "/renditions"));
        String ver = id(g.rest(mvc, "rendition document head", get("/api/documents/" + a)), "versionId");
        g.rest(mvc, "rendition attach", put("/api/documents/" + a + "/versions/" + ver + "/renditions/thumbnail")
                .contentType(MediaType.IMAGE_PNG).header("X-Generated-By", "pipeline:thumbs").content(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        g.rest(mvc, "rendition attach text", put("/api/documents/" + a + "/versions/" + ver + "/renditions/extract")
                .contentType(MediaType.TEXT_PLAIN).content("extracted text"));
        g.rest(mvc, "renditions version", get("/api/documents/" + a + "/versions/" + ver + "/renditions"));
        g.rest(mvc, "rendition read", get("/api/documents/" + a + "/renditions/extract"));
        g.rest(mvc, "rendition read version", get("/api/documents/" + a + "/versions/" + ver + "/renditions/thumbnail"));
        g.rest(mvc, "rendition read missing", get("/api/documents/" + a + "/renditions/nope"));
        g.rest(mvc, "rendition delete", delete("/api/documents/" + a + "/versions/" + ver + "/renditions/thumbnail"));
        g.rest(mvc, "renditions after delete", get("/api/documents/" + a + "/renditions"));
        g.rest(mvc, "renditions missing doc", get("/api/documents/doc-none/renditions"));
    }

    // ------------------------------------------------------------------ FoldersController

    private void folders(String a, String b) throws Exception {
        g.rest(mvc, "folder link", json(post("/api/folders/folder-eng/contents"), "{\"child\":\"" + a + "\",\"addedBy\":\"alice\"}"));
        g.rest(mvc, "folder link 2", json(post("/api/folders/folder-eng/contents"), "{\"child\":\"" + b + "\"}"));
        g.rest(mvc, "folder link other", json(post("/api/folders/folder-ops/contents"), "{\"child\":\"" + a + "\",\"position\":\"3\"}"));
        g.rest(mvc, "folder contents", get("/api/folders/folder-eng/contents"));
        g.rest(mvc, "folders for doc", get("/api/folders/for-doc/" + a));
        g.rest(mvc, "folder unlink", delete("/api/folders/folder-eng/contents/" + b));
        g.rest(mvc, "folder contents after unlink", get("/api/folders/folder-eng/contents"));
        g.rest(mvc, "folder contents empty", get("/api/folders/none/contents"));
    }

    // ------------------------------------------------------------------ ReferencesController

    private void references(String a, String b) throws Exception {
        g.rest(mvc, "reference add", json(post("/api/documents/" + a + "/references"),
                "{\"toCanonical\":\"" + b + "\",\"kind\":\"cites\",\"aux\":\"p.3\"}"));
        g.rest(mvc, "reference add pinned", json(post("/api/documents/" + a + "/references"),
                "{\"toCanonical\":\"" + b + "\",\"toVersion\":\"1.0.0\",\"kind\":\"embeds\",\"createdAt\":\"2020-01-02T03:04:05Z\"}"));
        g.rest(mvc, "references outbound", get("/api/documents/" + a + "/references"));
        g.rest(mvc, "references inbound", get("/api/documents/" + b + "/references/inbound"));
        g.rest(mvc, "reference remove", delete("/api/documents/" + a + "/references/" + b + "/cites"));
        g.rest(mvc, "references after remove", get("/api/documents/" + a + "/references"));
    }

    // ------------------------------------------------------------------ AclsController

    private void acls(String a) throws Exception {
        g.rest(mvc, "acl grant", json(post("/api/documents/" + a + "/acls"),
                "{\"principal\":\"user:alice\",\"permission\":\"read\",\"grant\":true}"));
        g.rest(mvc, "acl deny", json(post("/api/documents/" + a + "/acls"),
                "{\"principal\":\"group:ops\",\"permission\":\"write\",\"grant\":false,\"inheritFrom\":\"folder-eng\"}"));
        g.rest(mvc, "acls", get("/api/documents/" + a + "/acls"));
        g.rest(mvc, "acl revoke", delete("/api/documents/" + a + "/acls/user:alice/read"));
        g.rest(mvc, "acls after revoke", get("/api/documents/" + a + "/acls"));
    }

    // ------------------------------------------------------------------ TagsController

    private void tags(String a, String b) throws Exception {
        g.rest(mvc, "tag", post("/api/documents/" + a + "/tags/golden"));
        g.rest(mvc, "tag 2", post("/api/documents/" + a + "/tags/spec"));
        g.rest(mvc, "tag other doc", post("/api/documents/" + b + "/tags/golden"));
        g.restAnyOrder(mvc, "tags", get("/api/documents/" + a + "/tags"));
        g.restAnyOrder(mvc, "docs with tag", get("/api/tags/golden/documents"));
        g.rest(mvc, "untag", delete("/api/documents/" + a + "/tags/spec"));
        g.restAnyOrder(mvc, "tags after untag", get("/api/documents/" + a + "/tags"));
    }

    // ------------------------------------------------------------------ SearchController

    private void search() throws Exception {
        g.rest(mvc, "search body", get("/api/search").param("q", "goldenkeyword"));
        g.rest(mvc, "search title", get("/api/search").param("q", "title:Second"));
        g.rest(mvc, "search all limit", get("/api/search").param("q", "*:*").param("limit", "2"));
        g.rest(mvc, "search typed field", get("/api/search").param("q", "tf.assignee:bob"));
        g.rest(mvc, "search bad syntax", get("/api/search").param("q", "title:(unclosed"));
        g.rest(mvc, "search none", get("/api/search").param("q", "zzzznothing"));
    }

    // ------------------------------------------------------------------ stored JSON

    /** The {@code _source} JSON the DMS stores for every indexed version. */
    private void lucene() throws IOException {
        Path dir = home.resolve("lucene");
        List<String> sources = new ArrayList<>();
        try (var d = FSDirectory.open(dir); DirectoryReader r = DirectoryReader.open(d)) {
            var stored = r.storedFields();
            for (int i = 0; i < r.maxDoc(); i++) {
                String src = stored.document(i).get("_source");
                sources.add(src == null ? "<no _source>" : src);
            }
        }
        // normalize ids in creation order first, then sort for a stable order
        List<String> normalized = new ArrayList<>();
        for (String s : sources) normalized.add(g.normalize(s));
        java.util.Collections.sort(normalized);
        g.put("lucene _source", String.join("\n", normalized));
    }
}
