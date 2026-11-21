package com.hitorro.dms.spring;

import com.hitorro.dms.spring.config.JsonConfig;
import com.hitorro.dms.spring.golden.ConverterProbe;
import com.hitorro.util.json.JsonMappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot's Jackson 3 mapper has the settings that keep the Jackson 2 format (see {@link JsonConfig}). */
@SpringBootTest(properties = "dms.lucene-enabled=false")
@DisplayName("Boot's JSON mapper keeps the Jackson 2 settings")
class JsonConfigTest {

    @org.springframework.test.context.DynamicPropertySource
    static void tmpHome(org.springframework.test.context.DynamicPropertyRegistry reg) throws java.io.IOException {
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("dms-json-config-");
        reg.add("dms.home", home::toString);
    }

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    @Test
    @DisplayName("MVC writes and reads JSON with Boot's JsonMapper, not a Jackson 2 bridge")
    void mvcUsesBootsMapper() {
        var converter = ConverterProbe.jsonConverter(adapter);
        assertThat(converter).isInstanceOf(JacksonJsonHttpMessageConverter.class);
        assertThat(((JacksonJsonHttpMessageConverter) converter).getMapper()).isSameAs(mapper);
    }

    @Test
    @DisplayName("Jackson 2 mapper settings with Boot's date and leniency defaults")
    void mapperFeatures() {
        assertThat(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)).isFalse();
        assertThat(mapper.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)).isFalse();
        assertThat(mapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
        assertThat(mapper.isEnabled(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isTrue();
    }

    @Test
    @DisplayName("Jackson 2.18 stream limits (nesting 1000, strings 20M), as hitorro-core's JsonMappers")
    void streamLimits() {
        StreamReadConstraints r = mapper.tokenStreamFactory().streamReadConstraints();
        StreamWriteConstraints w = mapper.tokenStreamFactory().streamWriteConstraints();
        StreamReadConstraints wantR = JsonMappers.JACKSON2_READ_CONSTRAINTS;
        assertThat(r.getMaxNestingDepth()).isEqualTo(wantR.getMaxNestingDepth()).isEqualTo(1000);
        assertThat(r.getMaxStringLength()).isEqualTo(wantR.getMaxStringLength()).isEqualTo(20_000_000);
        assertThat(r.getMaxNumberLength()).isEqualTo(wantR.getMaxNumberLength());
        assertThat(r.getMaxNameLength()).isEqualTo(wantR.getMaxNameLength());
        assertThat(r.getMaxDocumentLength()).isEqualTo(wantR.getMaxDocumentLength());
        assertThat(r.getMaxTokenCount()).isEqualTo(wantR.getMaxTokenCount());
        assertThat(w.getMaxNestingDepth()).isEqualTo(JsonMappers.JACKSON2_WRITE_CONSTRAINTS.getMaxNestingDepth()).isEqualTo(1000);
    }

    @Autowired
    private org.springframework.boot.actuate.endpoint.jackson.EndpointJsonMapper endpointMapper;

    @Test
    @DisplayName("actuator JSON: declaration order, ISO dates, nulls left out (Boot's former Jackson 2 endpoint mapper)")
    void endpointMapper() {
        record Health(String status, String detail, java.time.Instant at) {}
        assertThat(endpointMapper.get().writeValueAsString(new Health("UP", null, java.time.Instant.ofEpochSecond(1_700_000_000L))))
                .isEqualTo("{\"status\":\"UP\",\"at\":\"2023-11-14T22:13:20Z\"}");
        assertThat(endpointMapper.get().tokenStreamFactory().streamReadConstraints().getMaxNestingDepth()).isEqualTo(1000);
    }
}
