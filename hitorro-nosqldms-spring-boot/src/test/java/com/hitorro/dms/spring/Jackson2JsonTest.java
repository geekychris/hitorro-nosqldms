/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
 * Boot 4 defaults HTTP JSON to Jackson 3 (tools.jackson). The DMS keeps
 * Jackson 2 (spring-boot-jackson2 + spring.http.converters.preferred-json-mapper=jackson2).
 * A Jackson 3 converter would write a com.fasterxml JsonNode as a bean
 * ({"array":false,"nodeType":"OBJECT",...}), so these tests pin the body.
 */
@SpringBootTest(properties = { "dms.lucene-enabled=false" })
@AutoConfigureMockMvc
@Import(Jackson2JsonTest.ProbeConfig.class)
class Jackson2JsonTest {

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
    void jackson2ConverterIsTheJsonWriter() {
        HttpMessageConverter<?> firstJson = adapter.getMessageConverters().stream()
                .filter(c -> c.canWrite(ObjectNode.class, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertThat(firstJson).isInstanceOf(MappingJackson2HttpMessageConverter.class);
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
                ObjectNode n = new ObjectMapper().createObjectNode();
                n.put("name", "dms");
                n.put("count", 3);
                n.putObject("nested").put("ok", true);
                n.putArray("list").add(1).add(2);
                return n;
            }
        }
    }
}
