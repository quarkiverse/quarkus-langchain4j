package io.quarkiverse.langchain4j.qdrant;

import java.util.OptionalInt;

import org.testcontainers.utility.DockerImageName;

import io.quarkus.devservices.common.ConfigureUtil;

public class QdrantContainer extends org.testcontainers.qdrant.QdrantContainer {

    private final OptionalInt fixedExposedPort;
    private final boolean useSharedNetwork;
    private String hostName = null;

    public QdrantContainer(
            String image,
            OptionalInt fixedExposedPort,
            String serviceName,
            boolean useSharedNetwork) {

        super(DockerImageName.parse(image).asCompatibleSubstituteFor("qdrant/qdrant"));

        if (serviceName != null) {
            withLabel(QdrantDevServices.DEV_SERVICE_LABEL, serviceName);
        }

        this.fixedExposedPort = fixedExposedPort;
        this.useSharedNetwork = useSharedNetwork;
    }

    @Override
    protected void configure() {
        super.configure();

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, "qdrant");
            return;
        }

        if (fixedExposedPort.isPresent()) {
            addFixedExposedPort(fixedExposedPort.getAsInt(), QdrantDevServices.QDRANT_PORT);
        } else {
            addExposedPort(QdrantDevServices.QDRANT_PORT);
        }
    }

    public int getPort() {
        if (useSharedNetwork) {
            return QdrantDevServices.QDRANT_PORT;
        }

        if (fixedExposedPort.isPresent()) {
            return fixedExposedPort.getAsInt();
        }

        return super.getMappedPort(QdrantDevServices.QDRANT_PORT);
    }

    @Override
    public String getHost() {
        return useSharedNetwork ? hostName : super.getHost();
    }
}
