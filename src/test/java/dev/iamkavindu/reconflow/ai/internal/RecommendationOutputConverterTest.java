package dev.iamkavindu.reconflow.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RecommendationOutputConverterTest {

    @Test
    void advertisesAnExplicitNullAlternativeWithoutRemovingTheResolutionEnum() {
        var converter = new RecommendationOutputConverter();
        var schema = JsonMapper.builder().build().readTree(converter.getJsonSchema());
        var alternatives = schema.get("properties").get("suggestedResolution").get("anyOf");
        assertThat(alternatives.size()).isEqualTo(2);
        assertThat(alternatives.get(1).get("type").asString()).isEqualTo("null");
        assertThat(converter.getJsonSchema()).contains("INTERNAL_RECORD_ERROR", "ACCEPTED_FEE_VARIANCE");
        assertThat(converter.getFormat()).contains("anyOf", "null");
    }
}
