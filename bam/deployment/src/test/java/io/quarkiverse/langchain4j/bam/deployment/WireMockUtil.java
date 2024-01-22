package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import jakarta.ws.rs.core.MediaType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;

public class WireMockUtil {

    public static String URL = "http://localhost:8089";
    public static String URL_CHAT_API = "/v2/text/chat?version=%s";
    public static int PORT = 8089;
    public static String API_KEY = "my_super_token";
    public static String VERSION = "2024-01-10";

    WireMockServer server;

    public WireMockUtil(WireMockServer server) {
        this.server = server;
    }

    public Builder mockBuilder(int status) {
        return new Builder(server, status);
    }

    public Builder mockBuilder(int status, String version) {
        return new Builder(server, status, version);
    }

    public static class Builder {

        private MappingBuilder builder;
        private String apikey = API_KEY;
        private String responseMediaType = MediaType.APPLICATION_JSON;
        private String response = "";
        private int status;
        private WireMockServer server;

        protected Builder(WireMockServer server, int status, String version) {
            this.server = server;
            this.status = status;
            this.builder = post(urlEqualTo(WireMockUtil.URL_CHAT_API.formatted(version)));
        }

        protected Builder(WireMockServer server, int status) {
            this.server = server;
            this.status = status;
            this.builder = post(urlEqualTo(WireMockUtil.URL_CHAT_API.formatted(VERSION)));
        }

        public Builder scenario(String currentState, String nextState) {
            builder = builder.inScenario("")
                    .whenScenarioStateIs(currentState)
                    .willSetStateTo(nextState);
            return this;
        }

        public Builder apikey(String apikey) {
            this.apikey = apikey;
            return this;
        }

        public Builder body(String body) {
            builder.withRequestBody(equalToJson(body));
            return this;
        }

        public Builder responseMediaType(String mediaType) {
            this.responseMediaType = mediaType;
            return this;
        }

        public Builder response(String response) {
            this.response = response;
            return this;
        }

        public void build() {
            server.stubFor(builder
                    .withHeader("Authorization", equalTo("Bearer %s".formatted(apikey)))
                    .willReturn(
                            aResponse()
                                    .withHeader("Content-Type", responseMediaType)
                                    .withStatus(status)
                                    .withBody(response)));
        }
    }
}
