package io.quarkiverse.langchain4j.mockapi.internal;

import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import io.vertx.core.Vertx;

/**
 * Resets the mock's scripted replies and request journal before each {@code @QuarkusTest} method. No-op when the mock
 * is not enabled.
 */
public class MockApiResetCallback implements QuarkusTestBeforeEachCallback {

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        Optional<String> url = ConfigProvider.getConfig().getOptionalValue("quarkus.langchain4j.mock-api.url", String.class);
        ArcContainer container = Arc.container();
        if (url.isEmpty() || container == null) {
            return;
        }
        InstanceHandle<MockModelServer> bean = container.instance(MockModelServer.class);
        if (bean.isAvailable()) {
            // the application-scoped bean; it is closed with the application, not here
            bean.get().reset();
            return;
        }
        // the bean is registered whenever the mock is enabled, so this is only a safety net; it borrows the
        // application's Vert.x instance rather than creating one, so the only cost is a short-lived HTTP client
        Vertx vertx = container.instance(Vertx.class).get();
        try (MockModelServer m = MockModelServer.connect(vertx, url.get())) {
            m.reset();
        }
    }
}
