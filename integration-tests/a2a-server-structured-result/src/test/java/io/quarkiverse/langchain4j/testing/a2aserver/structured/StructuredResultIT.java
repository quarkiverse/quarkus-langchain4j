package io.quarkiverse.langchain4j.testing.a2aserver.structured;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs the same assertions against the native executable, which is the only place a type the build failed to
 * register shows up: the model's answer cannot be parsed into it, and the task fails instead of completing.
 */
@QuarkusIntegrationTest
public class StructuredResultIT extends StructuredResultTest {
}
