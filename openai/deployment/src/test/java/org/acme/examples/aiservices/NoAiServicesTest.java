package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkus.test.QuarkusUnitTest;

public class NoAiServicesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    public void test() {
        assertThat(AiServicesRecorder.getMetadata()).isEmpty();
    }

    interface Assistant {

        String chat(String message);
    }
}
