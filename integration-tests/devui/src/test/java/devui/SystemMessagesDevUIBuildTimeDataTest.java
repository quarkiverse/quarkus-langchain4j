package devui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.test.QuarkusDevModeTest;
import tools.jackson.databind.JsonNode;

/**
 * Verifies the "systemMessages" build-time data that pre-fills the Dev UI Chat page: both inline messages and
 * messages declared with {@code fromResource} are resolved to their text.
 */
public class SystemMessagesDevUIBuildTimeDataTest extends DevUIBuildTimeDataTest {

    private static final String RESOURCE_PATH = "devui/poet-system-message.txt";
    private static final String RESOURCE_TEXT = "You are a poet who answers in rhymes.";
    private static final String INLINE_TEXT = "You are a translator.";

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(InlineService.class, ResourceService.class)
                    .addAsResource(new StringAsset(RESOURCE_TEXT), RESOURCE_PATH)
                    .addAsResource(new StringAsset("quarkus.langchain4j.openai.api-key=test\n"),
                            "application.properties"));

    public SystemMessagesDevUIBuildTimeDataTest() {
        super("io.quarkiverse.langchain4j.quarkus-langchain4j-core");
    }

    @Test
    public void systemMessagesAreResolvedFromAnnotationsAndResources() throws Exception {
        JsonNode systemMessages = getBuildTimeData("systemMessages");
        assertNotNull(systemMessages);
        assertTrue(systemMessages.isArray());

        List<String> texts = new ArrayList<>();
        for (JsonNode message : systemMessages) {
            texts.add(message.asText());
        }
        assertTrue(texts.contains(INLINE_TEXT), "inline system message missing from " + texts);
        assertTrue(texts.contains(RESOURCE_TEXT), "fromResource system message missing from " + texts);
    }

    @RegisterAiService
    @SystemMessage(INLINE_TEXT)
    public interface InlineService {
        String chat(String message);
    }

    @RegisterAiService
    @SystemMessage(fromResource = RESOURCE_PATH)
    public interface ResourceService {
        String chat(String message);
    }
}
