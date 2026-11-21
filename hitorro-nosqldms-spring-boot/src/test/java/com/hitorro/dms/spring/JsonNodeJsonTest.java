/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring;

import tools.jackson.databind.JsonNode;
import com.hitorro.util.json.JsonMappers;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP JSON is Boot 4's own Jackson 3 (tools.jackson) converter, and the DMS and HiTorro libraries
 * use Jackson 3 too. Before 4.0.0 the DMS ran on Jackson 2 through spring-boot-jackson2, where a
 * Jackson 3 converter would have written a com.fasterxml JsonNode as a bean
 * ({"array":false,"nodeType":"OBJECT",...}). These tests pin the body and the converter.
 */
@SpringBootTest(properties = { "dms.lucene-enabled=false" })
@AutoConfigureMockMvc
@Import(JsonNodeJsonTest.ProbeConfig.class)
class JsonNodeJsonTest {

    @DynamicPropertySource
    static void tmpHome(DynamicPropertyRegistry reg) {
        reg.add("dms.home", () -> {
            try { return Files.createTempDirectory("dms-json-").toString(); }
            catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    @Autowired MockMvc mvc;
    @Autowired RequestMappingHandlerAdapter adapter;

    @Test
    void jsonNodeIsSerializedAsPlainJson() throws Exception {
        mvc.perform(get("/test/json-node").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"name\":\"dms\",\"count\":3,\"nested\":{\"ok\":true},\"list\":[1,2]}", true));
    }

    @Test
    void jacksonConverterIsTheJsonWriter() {
        HttpMessageConverter<?> firstJson = adapter.getMessageConverters().stream()
                .filter(c -> c.canWrite(ObjectNode.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertThat(firstJson).isInstanceOf(JacksonJsonHttpMessageConverter.class);
    }

    @Test
    void documentEndpointWritesIsoInstants() throws Exception {
        String body = "{\"title\":\"json check\",\"contentType\":\"wiki-page\",\"createdBy\":\"u\"}";
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("json check"))
                .andExpect(jsonPath("$.versionLabel").value("1.0.0"))
                // Boot's Jackson 2 setup: java.time as ISO-8601 strings, not epoch numbers.
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @TestConfiguration
    static class ProbeConfig {
        @RestController
        static class JsonNodeProbeController {
            @GetMapping("/test/json-node")
            JsonNode jsonNode() {
                ObjectNode n = JsonMappers.json().createObjectNode();
                n.put("name", "dms");
                n.put("count", 3);
                n.putObject("nested").put("ok", true);
                n.putArray("list").add(1).add(2);
                return n;
            }
        }
    }
}
