package io.quarkiverse.langchain4j.chroma.deployment;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.ComparableVersion;
import org.testcontainers.utility.DockerImageName;

/**
 * Overriding the ChromaDBContainer from Testcontainers 1.20.x to support the v2 chroma api.
 * FIXME: This is temporary until we upgrade to Testcontainers 1.21.
 */
public class ChromaDBContainer extends GenericContainer<org.testcontainers.chromadb.ChromaDBContainer> {

    private static final DockerImageName DEFAULT_DOCKER_IMAGE = DockerImageName.parse("chromadb/chroma");

    private static final DockerImageName GHCR_DOCKER_IMAGE = DockerImageName.parse("ghcr.io/chroma-core/chroma");

    public ChromaDBContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public ChromaDBContainer(DockerImageName dockerImageName) {
        this(dockerImageName, isVersion2(dockerImageName.getVersionPart()));
    }

    public ChromaDBContainer(DockerImageName dockerImageName, boolean isVersion2) {
        super(dockerImageName);
        String apiPath = isVersion2 ? "/api/v2/heartbeat" : "/api/v1/heartbeat";
        dockerImageName.assertCompatibleWith(DEFAULT_DOCKER_IMAGE, GHCR_DOCKER_IMAGE);
        withExposedPorts(8000);
        waitingFor(Wait.forHttp(apiPath));
    }

    public String getEndpoint() {
        return "http://" + getHost() + ":" + getFirstMappedPort();
    }

    private static boolean isVersion2(String version) {
        if (version.equals("latest")) {
            return true;
        }

        ComparableVersion comparableVersion = new ComparableVersion(version);
        if (comparableVersion.isGreaterThanOrEqualTo("1.0.0")) {
            return true;
        }

        return false;
    }
}
