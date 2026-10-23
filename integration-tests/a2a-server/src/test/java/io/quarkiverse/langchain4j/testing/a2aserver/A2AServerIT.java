package io.quarkiverse.langchain4j.testing.a2aserver;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs the same assertions against the native executable, where the Agent Card is serialized by reflection the
 * extension has to register.
 */
@QuarkusIntegrationTest
public class A2AServerIT extends A2AServerTest {
}
