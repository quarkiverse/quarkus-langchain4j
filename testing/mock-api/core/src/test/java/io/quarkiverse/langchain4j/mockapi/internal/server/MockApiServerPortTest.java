package io.quarkiverse.langchain4j.mockapi.internal.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;

class MockApiServerPortTest {

    @Test
    void busyPortFailsWithClearMessageAndReleasesVertx() throws Exception {
        try (ServerSocket taken = new ServerSocket(0, 50, InetAddress.getByName("localhost"))) {
            int port = taken.getLocalPort();
            MockApiServer server = new MockApiServer(OptionalInt.of(port), "x", 4, DialectRegistry.load());
            assertThatThrownBy(server::start)
                    .hasMessageContaining(String.valueOf(port))
                    .hasMessageContaining("quarkus.langchain4j.mock-api.port");
            assertThat(server.vertx()).isNull();
        }
    }
}
