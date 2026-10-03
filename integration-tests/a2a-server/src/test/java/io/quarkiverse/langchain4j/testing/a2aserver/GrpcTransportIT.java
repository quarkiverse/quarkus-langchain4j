package io.quarkiverse.langchain4j.testing.a2aserver;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs the same assertions against the native executable. This transport carries protobuf on the wire rather than
 * only as the parsing format behind a JSON request.
 */
@QuarkusIntegrationTest
public class GrpcTransportIT extends GrpcTransportTest {
}
