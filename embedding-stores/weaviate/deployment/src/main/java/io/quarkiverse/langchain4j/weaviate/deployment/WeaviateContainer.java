package io.quarkiverse.langchain4j.weaviate.deployment;

import java.util.OptionalInt;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.devservices.common.ConfigureUtil;

public class WeaviateContainer extends org.testcontainers.weaviate.WeaviateContainer {

    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-weaviate";
    private static final int WEAVIATE_PORT = 8080;

    private final OptionalInt fixedExposedPort;
    private final boolean useSharedNetwork;

    private String hostName = null;

    public WeaviateContainer(DockerImageName dockerImageName,
            OptionalInt fixedExposedPort,
            String serviceName,
            boolean useSharedNetwork) {
        super(dockerImageName);
        this.fixedExposedPort = fixedExposedPort;
        this.useSharedNetwork = useSharedNetwork;

        if (serviceName != null) {
            withLabel(DEV_SERVICE_LABEL, serviceName);
        }
        withEnv("QUERY_DEFAULTS_LIMIT", "25");
        withEnv("DEFAULT_VECTORIZER_MODULE", "none");
        withEnv("CLUSTER_HOSTNAME", "node1");
        waitingFor(Wait.forHttp("/v1/.well-known/ready").forPort(8080));
        withStartupAttempts(2);
    }

    @Override
    protected void configure() {
        super.configure();

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, "weaviate");
            return;
        }

        if (fixedExposedPort.isPresent()) {
            addFixedExposedPort(fixedExposedPort.getAsInt(), WEAVIATE_PORT);
        } else {
            addExposedPort(WEAVIATE_PORT);
        }
    }

    public int getPort() {
        if (useSharedNetwork) {
            return WEAVIATE_PORT;
        }

        if (fixedExposedPort.isPresent()) {
            return fixedExposedPort.getAsInt();
        }
        return super.getFirstMappedPort();
    }

    @Override
    public String getHost() {
        return useSharedNetwork ? hostName : super.getHost();
    }
}
