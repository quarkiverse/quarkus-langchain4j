package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

class SystemMessageNonExistingResourceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(e -> {
                assertThat(e).isInstanceOf(UncheckedIOException.class);
                var cause = e.getCause();
                assertThat(cause).isInstanceOf(FileNotFoundException.class);
                assertThat(cause.getMessage()).isEqualTo("Resource not found: /non-existing-resource");
            });

    @RegisterAiService
    interface Assistant {
        @SystemMessage(fromResource = "non-existing-resource")
        String chat(String message);
    }

    @Inject
    Assistant assistant;

    @Test
    void test_init_assistent_with_non_existing_resource() {
        fail("Should not be called, see assertException");
    }

}
