package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.options;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.trace;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.GRANT_TYPE;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.IAM_200_RESPONSE;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PORT_COS_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PORT_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PORT_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PORT_WX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_GENERATE_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.VERSION;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionRequest;

import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.spi.JsonProvider;

public abstract class WireMockAbstract {

    static WireMockServer watsonxServer;
    static WireMockServer wxServer;
    static WireMockServer cosServer;
    static WireMockServer iamServer;
    static ObjectMapper mapper;

    @Inject
    LangChain4jWatsonxConfig langchain4jWatsonConfig;

    @BeforeAll
    static void beforeAll() {
        mapper = JsonProvider.MAPPER;

        watsonxServer = new WireMockServer(options().port(PORT_WATSONX_SERVER));
        watsonxServer.start();

        wxServer = new WireMockServer(options().port(PORT_WX_SERVER));
        wxServer.start();

        cosServer = new WireMockServer(options().port(PORT_COS_SERVER));
        cosServer.start();

        iamServer = new WireMockServer(options().port(PORT_IAM_SERVER));
        iamServer.start();
    }

    @AfterAll
    static void afterAll() {
        watsonxServer.stop();
        wxServer.stop();
        cosServer.stop();
        iamServer.stop();
    }

    @BeforeEach
    void beforeEach() throws Exception {
        watsonxServer.resetAll();
        wxServer.resetAll();
        cosServer.resetAll();
        iamServer.resetAll();
        handlerBeforeEach();
    }

    void handlerBeforeEach() throws Exception {
    }

    /**
     * Builder to mock the IAM server.
     */
    public IAMBuilder mockIAMBuilder(int status) {
        return new IAMBuilder(status);
    }

    /**
     * Builder to mock the wx server.
     */
    public WxBuilder mockWxBuilder(String apiURL, int status) {
        return new WxBuilder(apiURL, status);
    }

    /**
     * Builder to mock the wx server.
     */
    public WxBuilder mockWxBuilder(String apiURL, int status, String version) {
        return new WxBuilder(apiURL, status, version);
    }

    /**
     * Builder to mock the wx server.
     */
    public WxBuilder mockWxBuilder(RequestMethod method, String apiURL, int status, String version) {
        return new WxBuilder(method, apiURL, status, version);
    }

    /**
     * Builder to mock the Watsonx.ai server.
     */
    public WatsonxBuilder mockWatsonxBuilder(String apiURL, int status) {
        return new WatsonxBuilder(apiURL, status);
    }

    /**
     * Builder to mock the Watsonx.ai server.
     */
    public WatsonxBuilder mockWatsonxBuilder(String apiURL, int status, String version) {
        return new WatsonxBuilder(apiURL, status, version);
    }

    /**
     * Builder to mock the Watsonx.ai server.
     */
    public WatsonxBuilder mockWatsonxBuilder(RequestMethod method, String apiURL, int status, String version) {
        return new WatsonxBuilder(method, apiURL, status, version);
    }

    /**
     * Builder to mock the Watsonx.ai server for the text extraction api.
     */
    public TextExtractionBuilder mockTextExtractionBuilder(RequestMethod method, String url, int status) {
        return new TextExtractionBuilder(method, url, status);
    }

    /**
     * Builder to mock the Cloud Object Storage server.
     */
    public CosBuilder mockCosBuilder(RequestMethod method, String bucketName, String fileName, int status) {
        return new CosBuilder(method, "/%s/%s".formatted(bucketName, fileName), status, "");
    }

    public static abstract class ServerBuilder {

        private MappingBuilder builder;
        private String token = BEARER_TOKEN;
        private String responseMediaType = MediaType.APPLICATION_JSON;
        private String response;
        private int status;

        protected ServerBuilder(RequestMethod method, String apiURL, int status, String version) {
            this.builder = switch (method.getName()) {
                case "GET" -> get(urlPathMatching(apiURL));
                case "POST" -> post(urlPathMatching(apiURL));
                case "PUT" -> put(urlPathMatching(apiURL));
                case "DELETE" -> delete(urlPathMatching(apiURL));
                case "PATCH" -> patch(urlPathMatching(apiURL));
                case "OPTIONS" -> options(urlPathMatching(apiURL));
                case "HEAD" -> head(urlPathMatching(apiURL));
                case "TRACE" -> trace(urlPathMatching(apiURL));
                default -> throw new IllegalArgumentException("Unknown request method: " + method);
            };
            if (nonNull(version) && !version.isBlank())
                builder.withQueryParam("version", matching("\\d{4}-\\d{2}-\\d{2}"));
            this.status = status;
        }

        protected ServerBuilder(String apiURL, int status, String version) {
            this(RequestMethod.POST, apiURL, status, version);
        }

        protected ServerBuilder(String apiURL, int status) {
            this(RequestMethod.POST, apiURL, status, VERSION);
        }

        public ServerBuilder scenario(String currentState, String nextState) {
            builder = builder.inScenario("")
                    .whenScenarioStateIs(currentState)
                    .willSetStateTo(nextState);
            return this;
        }

        public ServerBuilder bodyIgnoreOrder(String body) {
            builder.withRequestBody(equalToJson(body, true, false));
            return this;
        }

        public ServerBuilder body(String body) {
            builder.withRequestBody(equalToJson(body));
            return this;
        }

        public ServerBuilder body(StringValuePattern stringValuePattern) {
            builder.withRequestBody(stringValuePattern);
            return this;
        }

        public ServerBuilder token(String token) {
            this.token = token;
            return this;
        }

        public ServerBuilder responseMediaType(String mediaType) {
            this.responseMediaType = mediaType;
            return this;
        }

        public ServerBuilder response(String response) {
            this.response = response;
            return this;
        }

        public abstract StubMapping build();
    }

    public static class IAMBuilder {

        private MappingBuilder builder;
        private String apikey = API_KEY;
        private String grantType = GRANT_TYPE;
        private String responseMediaType = MediaType.APPLICATION_JSON;
        private String response = "";
        private int status;

        protected IAMBuilder(int status) {
            this.status = status;
            this.builder = post(urlEqualTo(URL_IAM_GENERATE_TOKEN));
        }

        public IAMBuilder scenario(String currentState, String nextState) {
            builder = builder.inScenario("")
                    .whenScenarioStateIs(currentState)
                    .willSetStateTo(nextState);
            return this;
        }

        public IAMBuilder apikey(String apikey) {
            this.apikey = apikey;
            return this;
        }

        public IAMBuilder grantType(String grantType) {
            this.grantType = isNull(grantType) ? GRANT_TYPE : grantType;
            return this;
        }

        public IAMBuilder responseMediaType(String mediaType) {
            this.responseMediaType = mediaType;
            return this;
        }

        public IAMBuilder response(String response) {
            this.response = response;
            return this;
        }

        public IAMBuilder response(String token, Date expiration) {
            this.response = IAM_200_RESPONSE.formatted(token, TimeUnit.MILLISECONDS.toSeconds(expiration.getTime()));
            return this;
        }

        public void build() {
            iamServer.stubFor(
                    builder.withHeader("Content-Type", equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                            .withFormParam("apikey", equalTo(apikey))
                            .withFormParam("grant_type", equalTo(grantType))
                            .willReturn(aResponse()
                                    .withStatus(status)
                                    .withHeader("Content-Type", responseMediaType)
                                    .withBody(response)));
        }
    }

    public static class WatsonxBuilder extends ServerBuilder {

        protected WatsonxBuilder(RequestMethod method, String apiURL, int status, String version) {
            super(method, apiURL, status, version);
        }

        protected WatsonxBuilder(String apiURL, int status, String version) {
            super(RequestMethod.POST, apiURL, status, version);
        }

        protected WatsonxBuilder(String apiURL, int status) {
            super(RequestMethod.POST, apiURL, status, VERSION);
        }

        @Override
        public StubMapping build() {
            return watsonxServer.stubFor(
                    super.builder
                            .withHeader("Authorization", equalTo("Bearer %s".formatted(super.token)))
                            .willReturn(aResponse()
                                    .withStatus(super.status)
                                    .withHeader("Content-Type", super.responseMediaType)
                                    .withBody(super.response)));
        }
    }

    public static class WxBuilder extends ServerBuilder {

        protected WxBuilder(RequestMethod method, String apiURL, int status, String version) {
            super(method, apiURL, status, version);
        }

        protected WxBuilder(String apiURL, int status, String version) {
            super(RequestMethod.POST, apiURL, status, version);
        }

        protected WxBuilder(String apiURL, int status) {
            super(RequestMethod.POST, apiURL, status, null);
        }

        @Override
        public StubMapping build() {
            return wxServer.stubFor(
                    super.builder
                            .withHeader("Authorization", equalTo("Bearer %s".formatted(super.token)))
                            .willReturn(aResponse()
                                    .withStatus(super.status)
                                    .withHeader("Content-Type", super.responseMediaType)
                                    .withBody(super.response)));
        }
    }

    public static class CosBuilder extends ServerBuilder {

        protected CosBuilder(RequestMethod method, String apiURL, int status, String version) {
            super(method, apiURL, status, version);
            super.responseMediaType = MediaType.APPLICATION_XML;
        }

        @Override
        public StubMapping build() {
            return cosServer.stubFor(
                    super.builder
                            .withHeader("Authorization", equalTo("Bearer %s".formatted(super.token)))
                            .willReturn(aResponse()
                                    .withStatus(super.status)
                                    .withHeader("Content-Type", super.responseMediaType)
                                    .withBody(super.response)));
        }
    }

    public static class TextExtractionBuilder extends ServerBuilder {

        protected TextExtractionBuilder(RequestMethod method, String apiURL, int status) {
            super(method, apiURL, status, VERSION);
        }

        public TextExtractionBuilder body(TextExtractionRequest request) {
            try {
                super.body(mapper.writeValueAsString(request));
                return this;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public TextExtractionBuilder failResponse(TextExtractionRequest request, String id) {
            super.response("""
                    {
                          "metadata": {
                            "id": "%s",
                            "created_at": "2023-05-02T16:27:51Z",
                            "project_id": "%s",
                            "name": "extract"
                          },
                          "entity": {
                            "document_reference": {
                              "type": "connection_asset",
                              "connection": {
                                "id": "%s"
                              },
                              "location": {
                                "file_name": "%s"
                              }
                            },
                            "results_reference": {
                              "type": "connection_asset",
                              "connection": {
                                "id": "%s"
                              },
                              "location": {
                                "file_name": "%s"
                              }
                            },
                            "results": {
                                "error": {
                                    "code": "file_download_error",
                                    "message": "error message"
                                },
                                "number_pages_processed": 0,
                                "status": "failed"
                            }
                          }
                        }""".formatted(
                    id,
                    request.projectId(),
                    request.documentReference().connection().id(),
                    request.documentReference().location().fileName(),
                    request.resultsReference().connection().id(),
                    request.resultsReference().location().fileName()));
            return this;
        }

        public TextExtractionBuilder response(TextExtractionRequest request, String id, String status) {
            super.response("""
                    {
                      "metadata": {
                        "id": "%s",
                        "created_at": "2023-05-02T16:27:51Z",
                        "project_id": "%s",
                        "name": "extract"
                      },
                      "entity": {
                        "document_reference": {
                          "type": "connection_asset",
                          "connection": {
                            "id": "%s"
                          },
                          "location": {
                            "file_name": "%s",
                            "bucket": "%s"
                          }
                        },
                        "results_reference": {
                          "type": "connection_asset",
                          "connection": {
                            "id": "%s"
                          },
                          "location": {
                            "file_name": "%s",
                            "bucket": "%s"
                          }
                        },
                        "results": {
                          "status": "%s",
                          "number_pages_processed": 1,
                          "running_at": "2023-05-02T16:28:03Z",
                          "completed_at": "2023-05-02T16:28:03Z"
                        }
                      }
                    }""".formatted(
                    id,
                    request.projectId(),
                    request.documentReference().connection().id(),
                    request.documentReference().location().fileName(),
                    request.documentReference().location().bucket(),
                    request.resultsReference().connection().id(),
                    request.resultsReference().location().fileName(),
                    request.resultsReference().location().bucket(),
                    status));
            return this;
        }

        @Override
        public StubMapping build() {
            return watsonxServer.stubFor(
                    super.builder
                            .withHeader("Authorization", equalTo("Bearer %s".formatted(super.token)))
                            .willReturn(aResponse()
                                    .withStatus(super.status)
                                    .withHeader("Content-Type", super.responseMediaType)
                                    .withBody(super.response)));
        }
    }
}
