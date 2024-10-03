package io.quarkiverse.langchain4j.tavily;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

public class ShadowSensitiveDataTest {

    @Test
    public void testShadowLongApiKey() {

        String body = """
                {
                    "api_key":"tvly-Lv123456789QWERTYUIOPlZXCVBNMASD"
                }
                """;

        final Buffer buffer = Buffer.buffer(body);
        final String shadowedBody = ShadowSensitiveData.process(buffer, "api_key");

        assertThat(shadowedBody)
                .isEqualTo("{\"api_key\":\"tvly-Lv******************************\"}");
    }

    @Test
    public void testShadowSmallApiKey() {
        String body = """
                {
                    "api_key":"tvly"
                }
                """;

        final Buffer buffer = Buffer.buffer(body);
        final String shadowedBody = ShadowSensitiveData.process(buffer, "api_key");

        assertThat(shadowedBody)
                .isEqualTo("{\"api_key\":\"****\"}");
    }

    @Test
    public void testNoFieldtoShadow() {
        String body = """
                {
                    "api":"tvly"
                }
                """;

        final Buffer buffer = Buffer.buffer(body);
        final String shadowedBody = ShadowSensitiveData.process(buffer, "api_key");

        assertThat(shadowedBody)
                .isEqualTo("{\"api\":\"tvly\"}");
    }

}
