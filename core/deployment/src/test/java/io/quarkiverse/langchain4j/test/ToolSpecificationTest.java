package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.test.QuarkusUnitTest;

public class ToolSpecificationTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    public static class Base {
        @JsonIgnore
        public String ignoredBaseField;

        public String baseField;

        public static String staticBaseField;
    }

    public static class Sub extends Base {
        public String subField;

        public static String staticSubField;

        @JsonIgnore
        public String ignoredSubField;
    }

    public static class TestTool {
        @Tool("Tool description")
        public void toolCall(Sub sub) {

        }
    }

    public static class MetadataTool {
        @Tool(name = "metadataTool", value = "Tool with metadata", metadata = "{\"foo\": \"bar\", \"baz\": 123}")
        public void toolCall() {

        }

        @Tool(name = "emptyMetadataTool", value = "Tool with empty metadata", metadata = "")
        public void toolCallEmptyMetadata() {

        }
    }

    @Test
    void testComplexSchema() {
        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(TestTool.class.getName());
        assertNotNull(methodCreateInfos);
        assertFalse(methodCreateInfos.isEmpty());
        assertThat(methodCreateInfos).hasSize(1);

        ToolSpecification toolSpecification = methodCreateInfos.get(0).toolSpecification();
        JsonObjectSchema schema = (JsonObjectSchema) toolSpecification.parameters().properties().get("sub");
        assertThat(schema.properties()).hasSize(2);
        assertThat(schema.properties().get("subField")).isNotNull();
        assertThat(schema.properties().get("staticSubField")).isNull();
        assertThat(schema.properties().get("ignoredSubField")).isNull();
        assertThat(schema.properties().get("baseField")).isNotNull();
        assertThat(schema.properties().get("staticBaseField")).isNull();
        assertThat(schema.properties().get("ignoredBaseField")).isNull();
    }

    @Test
    void testMetadata() {
        List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.getMetadata().get(MetadataTool.class.getName());
        assertNotNull(methodCreateInfos);
        assertThat(methodCreateInfos).hasSize(2);

        ToolSpecification metadataToolSpecification = methodCreateInfos.get(0).toolSpecification();
        assertThat(metadataToolSpecification.metadata()).containsEntry("foo", "bar");
        assertThat(metadataToolSpecification.metadata()).containsEntry("baz", 123);

        ToolSpecification emptyMetadataToolSpecification = methodCreateInfos.get(1).toolSpecification();
        assertThat(emptyMetadataToolSpecification.metadata()).isEmpty();
    }

}
