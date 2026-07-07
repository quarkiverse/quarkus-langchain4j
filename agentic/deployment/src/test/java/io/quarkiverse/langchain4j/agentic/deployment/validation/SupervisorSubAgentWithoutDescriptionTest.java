package io.quarkiverse.langchain4j.agentic.deployment.validation;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class SupervisorSubAgentWithoutDescriptionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .setLogRecordPredicate(SupervisorSubAgentWithoutDescriptionTest::isAgenticWarning)
            .assertLogRecords(SupervisorSubAgentWithoutDescriptionTest::assertWarnsAboutMissingDescription);

    private static boolean isAgenticWarning(LogRecord record) {
        return record.getLevel().intValue() >= Level.WARNING.intValue()
                && record.getLoggerName() != null
                && record.getLoggerName().contains("langchain4j.agentic");
    }

    private static void assertWarnsAboutMissingDescription(List<LogRecord> records) {
        Assertions.assertThat(records).anySatisfy(record -> Assertions.assertThat(record.getMessage())
                .contains("no description")
                .contains("WithdrawAgent"));
    }

    @Test
    void test() {
    }

    public interface WithdrawAgent {
        @SystemMessage("""
                You are a banker that can only withdraw US dollars (USD) from a user account.
                """)
        @UserMessage("""
                Withdraw {{amountInUSD}} USD from {{withdrawUser}}'s account and return the new balance.
                """)
        @Agent
        String withdraw(@V("withdrawUser") String withdrawUser, @V("amountInUSD") Double amountInUSD);
    }

    public interface CreditAgent {
        @SystemMessage("""
                You are a banker that can only credit US dollars (USD) to a user account.
                """)
        @UserMessage("""
                Credit {{amountInUSD}} USD to {{creditUser}}'s account and return the new balance.
                """)
        @Agent("A banker that credit USD to an account")
        String credit(@V("creditUser") String creditUser, @V("amountInUSD") Double amountInUSD);
    }

    public interface SupervisorBanker {

        @SupervisorAgent(subAgents = { WithdrawAgent.class, CreditAgent.class })
        String invoke(@V("request") String request);
    }
}
