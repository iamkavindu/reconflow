package dev.iamkavindu.reconflow.ai.internal;

import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Keep the model-facing schema consistent with the optional advisory resolution. */
class RecommendationOutputConverter extends BeanOutputConverter<RecommendationDraft> {

    RecommendationOutputConverter() {
        super(RecommendationDraft.class);
    }

    @Override
    protected String generateSchema() {
        var json = JsonMapper.builder().build();
        var schema = (ObjectNode) json.readTree(super.generateSchema());
        var properties = (ObjectNode) schema.get("properties");
        var resolution = properties.get("suggestedResolution");
        var nullable = json.createObjectNode();
        nullable.putArray("anyOf").add(resolution).addObject().put("type", "null");
        properties.set("suggestedResolution", nullable);
        return json.writeValueAsString(schema);
    }
}
