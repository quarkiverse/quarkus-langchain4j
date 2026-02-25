package io.quarkiverse.langchain4j.agentic.deployment.validation;

import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class NonAiAgentShouldWorkTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Test
    void test() {
        // we don't need to do anything as we just wanted to make sure that validation doesn't fail the build
    }

    public interface SequenceWorkflow {

        @SequenceAgent(outputKey = "workflowResult", subAgents = { CreateListAgent.class, PlainProcessListAgent.class })
        String execute();

        @Output
        static String SummaryResult(String output) {
            return output;
        }

    }

    public interface CreateListAgent {

        @SystemMessage("You just always create a List with random Strings.")
        @Agent(description = "Create a list with random strings", outputKey = "aList")
        List<String> createList();
    }

    public class PlainProcessListAgent {

        @Agent(description = "GetSummaries", outputKey = "output")
        public String getFirst(List<String> aList) {
            return aList.get(0);
        }
    }
}
