package io.quarkiverse.langchain4j.deployment.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.service.SystemMessage;

class SystemMessageCollectorTest {

    private static final String POET_RESOURCE_TEXT = "You are a poet who answers in rhymes.\n";

    private ClassLoader originalContextClassLoader;

    @BeforeEach
    void installLeadingSlashTolerantContextClassLoader() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new LeadingSlashTolerantClassLoader(originalContextClassLoader));
    }

    @AfterEach
    void restoreContextClassLoader() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void inlineValueIsCollected() throws IOException {
        assertThat(collect(InlineService.class)).containsExactly("You are a poet.");
    }

    @Test
    void methodLevelAnnotationIsCollected() throws IOException {
        assertThat(collect(MethodLevelService.class)).containsExactly("You are a translator.");
    }

    @Test
    void multiLineValueIsJoinedWithTheDelimiterUsedAtRuntime() throws IOException {
        assertThat(collect(MultiLineService.class)).containsExactly("First line\nSecond line");
    }

    @Test
    void fromResourceIsReadFromTheClasspath() throws IOException {
        assertThat(collect(FromResourceService.class)).containsExactly(POET_RESOURCE_TEXT);
    }

    @Test
    void fromResourceWinsOverAnEmptyExplicitValue() throws IOException {
        assertThat(collect(FromResourceWithEmptyValueService.class))
                .containsExactly(POET_RESOURCE_TEXT);
    }

    @Test
    void servicesWithoutSystemMessageContributeNothing() throws IOException {
        assertThat(collect(NoSystemMessageService.class)).isEmpty();
    }

    @Test
    void messagesOfSeveralServicesAreCollectedInOrder() throws IOException {
        assertThat(collect(InlineService.class, FromResourceService.class))
                .containsExactly("You are a poet.", POET_RESOURCE_TEXT);
    }

    private static List<String> collect(Class<?>... services) throws IOException {
        Index index = Index.of(services);
        List<ClassInfo> classes = new ArrayList<>();
        for (Class<?> service : services) {
            classes.add(index.getClassByName(DotName.createSimple(service.getName())));
        }
        return LangChain4jDevUIProcessor.collectSystemMessages(classes);
    }

    /**
     * Mimics the Quarkus class loader, which accepts resource names with a leading slash like the one
     * {@code TemplateUtil} asks for, unlike a plain application class loader.
     */
    private static final class LeadingSlashTolerantClassLoader extends ClassLoader {

        LeadingSlashTolerantClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return super.getResourceAsStream(name.startsWith("/") ? name.substring(1) : name);
        }
    }

    @SystemMessage("You are a poet.")
    interface InlineService {
        String chat(String message);
    }

    interface MethodLevelService {
        @SystemMessage("You are a translator.")
        String translate(String message);
    }

    @SystemMessage({ "First line", "Second line" })
    interface MultiLineService {
        String chat(String message);
    }

    @SystemMessage(fromResource = "devui/poet-system-message.txt")
    interface FromResourceService {
        String chat(String message);
    }

    @SystemMessage(value = "", fromResource = "devui/poet-system-message.txt")
    interface FromResourceWithEmptyValueService {
        String chat(String message);
    }

    interface NoSystemMessageService {
        String chat(String message);
    }
}
