package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The error handler of an agent used as a sub-agent is never invoked, the one of the root agent handles the failures
 * of the whole agentic system: the build warns about a handler declared by a sub-agent, or inherited by it, and not
 * about the one of a root agent.
 */
public class ErrorHandlerOnSubAgentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class);
                }
            })
            .setLogRecordPredicate(new Predicate<LogRecord>() {
                @Override
                public boolean test(LogRecord record) {
                    return record.getLevel().intValue() >= Level.WARNING.intValue()
                            && record.getLoggerName() != null
                            && record.getLoggerName().contains("langchain4j.agentic");
                }
            })
            .assertLogRecords(new Consumer<List<LogRecord>>() {
                @Override
                public void accept(List<LogRecord> records) {
                    List<String> warnings = new ArrayList<>();
                    for (LogRecord record : records) {
                        if (record.getMessage().contains("@ErrorHandler")) {
                            warnings.add(record.getMessage());
                        }
                    }
                    assertThat(warnings).hasSize(2);
                    assertThat(warnings).filteredOn(new Predicate<String>() {
                        @Override
                        public boolean test(String warning) {
                            return warning.contains(Greeting.class.getName() + "'")
                                    && warning.contains("'onGreetingError'");
                        }
                    }).hasSize(1);
                    assertThat(warnings).filteredOn(new Predicate<String>() {
                        @Override
                        public boolean test(String warning) {
                            return warning.contains(Farewell.class.getName() + "'")
                                    && warning.contains("'onError'");
                        }
                    }).hasSize(1);
                }
            });

    @Test
    void test() {
    }

    public static class Greeter {

        @Agent(description = "Greets", outputKey = "greeting")
        public static String greet(@V("name") String name) {
            return "Hello " + name;
        }
    }

    public static class Dismisser {

        @Agent(description = "Says goodbye", outputKey = "farewell")
        public static String dismiss(@V("name") String name) {
            return "Goodbye " + name;
        }
    }

    /** A sub-agent declaring an error handler. */
    public interface Greeting {

        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);

        @ErrorHandler
        static ErrorRecoveryResult onGreetingError(ErrorContext errorContext) {
            return ErrorRecoveryResult.throwException();
        }
    }

    public interface Recoverable {

        @ErrorHandler
        static ErrorRecoveryResult onError(ErrorContext errorContext) {
            return ErrorRecoveryResult.throwException();
        }
    }

    /** A sub-agent inheriting an error handler. */
    public interface Farewell extends Recoverable {

        @SequenceAgent(outputKey = "farewell", subAgents = Dismisser.class)
        String dismiss(@V("name") String name);
    }

    /** The root agent: its error handler is the one that runs. */
    public interface Conversation {

        @SequenceAgent(outputKey = "farewell", subAgents = { Greeting.class, Farewell.class })
        String converse(@V("name") String name);

        @ErrorHandler
        static ErrorRecoveryResult onConversationError(ErrorContext errorContext) {
            return ErrorRecoveryResult.throwException();
        }
    }
}
