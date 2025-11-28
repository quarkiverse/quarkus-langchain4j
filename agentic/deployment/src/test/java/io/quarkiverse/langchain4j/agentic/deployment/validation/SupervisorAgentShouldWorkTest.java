package io.quarkiverse.langchain4j.agentic.deployment.validation;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class SupervisorAgentShouldWorkTest {

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

    public interface WithdrawAgent {
        @SystemMessage("""
                You are a banker that can only withdraw US dollars (USD) from a user account.
                """)
        @UserMessage("""
                Withdraw {{amountInUSD}} USD from {{withdrawUser}}'s account and return the new balance.
                """)
        @Agent("A banker that withdraw USD from an account")
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

    public interface ExchangeAgent {
        @UserMessage("""
                You are an operator exchanging money in different currencies.
                Use the tool to exchange {{amount}} {{originalCurrency}} into {{targetCurrency}}
                returning only the final amount provided by the tool as it is and nothing else.
                """)
        @Agent(outputKey = "exchange", description = "A money exchanger that converts a given amount of money from the original to the target currency")
        Double exchange(@V("originalCurrency") String originalCurrency, @V("amount") Double amount,
                @V("targetCurrency") String targetCurrency);
    }

    public interface SupervisorBanker {

        @SupervisorAgent(responseStrategy = SupervisorResponseStrategy.SUMMARY, subAgents = { WithdrawAgent.class,
                CreditAgent.class, ExchangeAgent.class })
        String invoke(@V("request") String request);
    }
}
