package io.quarkiverse.langchain4j.testing.a2aserver;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs the same assertions against the native executable, where the protobuf messages this binding parses into
 * are reachable only through the reflection the extension registers.
 */
@QuarkusIntegrationTest
public class RestTransportIT extends RestTransportTest {
}
