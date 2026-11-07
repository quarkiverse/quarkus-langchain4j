package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusUnitTest;

public class NonObjectReturnTypeToolSupplierTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WithdrawAgent.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("Object"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface WithdrawAgent {
        @SystemMessage("""
                You are a banker that can only withdraw US dollars (USD) from a user account.
                """)
        @UserMessage("""
                Withdraw {{amountInUSD}} USD from {{user}}'s account and return the new balance.
                """)
        @Agent("A banker that withdraw USD from an account")
        String withdraw(@V("user") String user, @V("amountInUSD") Double amount);

        @ToolsSupplier
        static void tools() {

        }
    }
}
