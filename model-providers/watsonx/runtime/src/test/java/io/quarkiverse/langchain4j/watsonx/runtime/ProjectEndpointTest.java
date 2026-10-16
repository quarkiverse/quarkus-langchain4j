package io.quarkiverse.langchain4j.watsonx.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectEndpointTest {

    @ParameterizedTest
    @CsvSource({
            "https://us-south.ml.cloud.ibm.com, https://api.dataplatform.cloud.ibm.com",
            "https://eu-de.ml.cloud.ibm.com/, https://api.eu-de.dataplatform.cloud.ibm.com",
            "https://ap-south-1.aws.wxai.ibm.com, https://api.ap-south-1.aws.data.ibm.com",
            "https://custom.example/watsonx, https://custom.example/watsonx"
    })
    void resolvesPlatformEndpoint(String mlEndpoint, String expectedEndpoint) {
        assertThat(WatsonxRecorder.resolveProjectBaseUrl(URI.create(mlEndpoint), null))
                .isEqualTo(URI.create(expectedEndpoint));
    }

    @ParameterizedTest
    @CsvSource({ "https://us-south.ml.cloud.ibm.com", "https://custom.example/watsonx" })
    void explicitEndpointTakesPrecedence(String mlEndpoint) {
        URI projectsEndpoint = URI.create("https://projects.example/platform");
        assertThat(WatsonxRecorder.resolveProjectBaseUrl(URI.create(mlEndpoint), projectsEndpoint))
                .isEqualTo(projectsEndpoint);
    }
}
