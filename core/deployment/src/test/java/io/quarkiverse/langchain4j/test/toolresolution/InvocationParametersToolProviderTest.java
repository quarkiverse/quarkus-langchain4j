package io.quarkiverse.langchain4j.test.toolresolution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify that InvocationParameters are propagated to ToolProvider,
 * allowing dynamic tool filtering based on parameters passed at invocation time.
 */
public class InvocationParametersToolProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestAiSupplier.class,
                            TestAiModel.class,
                            RoleBasedToolProvider.class,
                            RoleBasedToolProviderSupplier.class,
                            ServiceWithRoleBasedTools.class));

    @ApplicationScoped
    public static class RoleBasedToolProvider implements ToolProvider {

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            InvocationParameters params = request.invocationParameters();
            String role = params.get("role");

            var builder = ToolProviderResult.builder();

            if ("admin".equals(role)) {
                ToolSpecification writeSpec = ToolSpecification.builder()
                        .name("write_data")
                        .description("Writes data")
                        .build();
                ToolExecutor writeExecutor = (t, m) -> "WRITE";
                builder.add(writeSpec, writeExecutor);
            }

            ToolSpecification readSpec = ToolSpecification.builder()
                    .name("read_data")
                    .description("Reads data")
                    .build();
            ToolExecutor readExecutor = (t, m) -> "READ";
            builder.add(readSpec, readExecutor);

            return builder.build();
        }
    }

    @ApplicationScoped
    public static class RoleBasedToolProviderSupplier implements Supplier<ToolProvider> {

        @Inject
        RoleBasedToolProvider provider;

        @Override
        public ToolProvider get() {
            return provider;
        }
    }

    @RegisterAiService(toolProviderSupplier = RoleBasedToolProviderSupplier.class, chatLanguageModelSupplier = TestAiSupplier.class)
    interface ServiceWithRoleBasedTools {

        String chat(@UserMessage String msg, @MemoryId Object id, InvocationParameters params);
    }

    @Inject
    ServiceWithRoleBasedTools service;

    @Test
    @ActivateRequestContext
    void testAdminGetsWriteTool() {
        InvocationParameters params = new InvocationParameters();
        params.put("role", "admin");
        String answer = service.chat("hello", 1, params);
        assertEquals("WRITE", answer);
    }

    @Test
    @ActivateRequestContext
    void testUserGetsReadOnlyTool() {
        InvocationParameters params = new InvocationParameters();
        params.put("role", "user");
        String answer = service.chat("hello", 2, params);
        assertEquals("READ", answer);
    }
}
