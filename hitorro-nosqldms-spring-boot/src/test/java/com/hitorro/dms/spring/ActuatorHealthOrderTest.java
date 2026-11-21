package com.hitorro.dms.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.jackson.EndpointJsonMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Actuator health JSON is deterministic in every context: a fresh context per repetition, components and
 * details shown, and Boot's isolated endpoint mapper switched back on, so the app's {@code @Primary}
 * endpoint mapper has to win and its mix-in has to put each component's {@code status} before
 * {@code details} (Jackson orders those two getters by {@code getDeclaredMethods()}, which varies).
 */
@SpringBootTest(properties = {
        "dms.lucene-enabled=false",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.show-components=always",
        "management.endpoints.jackson.isolated-json-mapper=true"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Actuator health JSON order is fixed in every context")
class ActuatorHealthOrderTest {

    @org.springframework.test.context.DynamicPropertySource
    static void tmpHome(org.springframework.test.context.DynamicPropertyRegistry reg) throws java.io.IOException {
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("dms-health-");
        reg.add("dms.home", home::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EndpointJsonMapper endpointMapper;

    @RepeatedTest(3)
    void statusFirstEverywhere() throws Exception {
        assertThat(endpointMapper.getClass().getName()).contains("JsonConfig");
        String body = mvc.perform(get("/actuator/health")).andReturn().getResponse().getContentAsString();
        assertThat(body).startsWith("{\"status\":").contains("\"components\":{");
        // every component object starts with its status; none starts with details
        assertThat(body).doesNotContain("{\"details\":");
        assertThat(body.split("\\{\"status\":", -1).length - 1).isGreaterThan(1);
    }
}
