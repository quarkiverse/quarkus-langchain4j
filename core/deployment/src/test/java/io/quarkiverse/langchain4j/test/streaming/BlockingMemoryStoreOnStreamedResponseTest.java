package io.quarkiverse.langchain4j.test.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

public class BlockingMemoryStoreOnStreamedResponseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamTestUtils.class));

    @Inject
    MyAiService service;

    @RepeatedTest(100) // Verify that the order is preserved.
    @ActivateRequestContext
    void testFromWorkerThread() {
        // We are on a worker thread.
        List<String> list = service.hi("123", "Say hello").collect().asList().await().indefinitely();
        // We cannot guarantee the order, as we do not have a context.
        assertThat(list).containsExactly("Hi!", " ", "World!");

        list = service.hi("123", "Second message").collect().asList().await().indefinitely();
        assertThat(list).containsExactly("OK!");
    }

    @BeforeEach
    void cleanup() {
        StreamTestUtils.FakeMemoryStore.DC_DATA = null;
    }

    @RepeatedTest(10)
    void testFromDuplicatedContextThread() throws InterruptedException {
        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        CountDownLatch latch = new CountDownLatch(1);
        context.executeBlocking(v -> {
            try {
                Arc.container().requestContext().activate();
                var value = UUID.randomUUID().toString();
                StreamTestUtils.FakeMemoryStore.DC_DATA = value;
                Vertx.currentContext().putLocal("DC_DATA", value);
                List<String> list = service.hi("123", "Say hello").collect().asList().await().indefinitely();
                assertThat(list).containsExactly("Hi!", " ", "World!");
                Arc.container().requestContext().deactivate();

                Arc.container().requestContext().activate();

                list = service.hi("123", "Second message").collect().asList().await().indefinitely();
                assertThat(list).containsExactly("OK!");
                latch.countDown();

            } finally {
                Arc.container().requestContext().deactivate();
                Vertx.currentContext().removeLocal("DC_DATA");

            }
        }, false);
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Inject
    Vertx vertx;

    @Test
    void testFromEventLoopThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Context context = vertx.getOrCreateContext();
        context.runOnContext(v -> {
            Arc.container().requestContext().activate();
            try {
                service.hi("123", "Say hello").collect().asList()
                        .subscribe().asCompletionStage();
            } catch (Exception e) {
                assertThat(e)
                        .isNotNull()
                        .hasMessageContaining("Expected to be able to block");
            } finally {
                Arc.container().requestContext().deactivate();
                latch.countDown();
            }
        });
        latch.await();
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = StreamTestUtils.FakeStreamedChatModelSupplier.class, chatMemoryProviderSupplier = StreamTestUtils.FakeMemoryProviderSupplier.class)
    public interface MyAiService {

        Multi<String> hi(@MemoryId String id, @UserMessage String query);

    }

}
