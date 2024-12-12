package io.quarkiverse.langchain4j.test.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
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

    @Test
    @ActivateRequestContext
    void testFromWorkerThread() {
        // We are on a worker thread.
        List<String> list = service.hi("123", "Say hello").collect().asList().await().indefinitely();
        // We cannot guarantee the order, as we do not have a context.
        assertThat(list).containsExactlyInAnyOrder("Hi!", " ", "World!");
    }

    @BeforeEach
    void cleanup() {
        StreamTestUtils.FakeMemoryStore.DC_DATA = null;
    }

    @Test
    void testFromDuplicatedContextThread() {
        Context context = vertx.getOrCreateContext();
        context.executeBlocking(v -> {
            System.out.println("Running on " + Thread.currentThread().getName());
            Arc.container().requestContext().activate();
            var value = UUID.randomUUID().toString();
            StreamTestUtils.FakeMemoryStore.DC_DATA = value;
            Vertx.currentContext().putLocal("DC_DATA", value);
            List<String> list = service.hi("123", "Say hello").collect().asList().await().indefinitely();
            assertThat(list).containsExactly("Hi!", " ", "World!");
            Vertx.currentContext().removeLocal("DC_DATA");
            Arc.container().requestContext().deactivate();
        }, false);
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

        Multi<String> hi(String id, @UserMessage String query);

    }

}
