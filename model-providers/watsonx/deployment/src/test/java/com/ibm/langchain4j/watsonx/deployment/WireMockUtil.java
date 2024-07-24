package com.ibm.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.core.MediaType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;

public class WireMockUtil {

    public static final int PORT_WATSONX_SERVER = 8089;
    public static final String URL_WATSONX_SERVER = "http://localhost:8089";
    public static final String URL_WATSONX_CHAT_API = "/ml/v1/text/generation?version=%s";
    public static final String URL_WATSONX_CHAT_STREAMING_API = "/ml/v1/text/generation_stream?version=%s";
    public static final String URL_WATSONX_EMBEDDING_API = "/ml/v1/text/embeddings?version=%s";
    public static final String URL_WATSONX_TOKENIZER_API = "/ml/v1/text/tokenization?version=%s";

    public static final int PORT_IAM_SERVER = 8090;
    public static final String URL_IAM_SERVER = "http://localhost:8090";
    public static final String URL_IAM_GENERATE_TOKEN = "/identity/token";

    public static final String API_KEY = "my_super_api_key";
    public static final String BEARER_TOKEN = "my_super_token";
    public static final String PROJECT_ID = "123123321321";
    public static final String GRANT_TYPE = "urn:ibm:params:oauth:grant-type:apikey";
    public static final String VERSION = "2024-03-14";
    public static final String DEFAULT_CHAT_MODEL = "ibm/granite-20b-multilingual";
    public static final String DEFAULT_EMBEDDING_MODEL = "ibm/slate-125m-english-rtrvr";
    public static final String IAM_200_RESPONSE = """
            {
                "access_token": "%s",
                "refresh_token": "not_supported",
                "token_type": "Bearer",
                "expires_in": 3600,
                "expiration": %d,
                "scope": "ibm openid"
            }
            """;
    public static String RESPONSE_WATSONX_CHAT_API = """
            {
                "model_id": "meta-llama/llama-2-70b-chat",
                "created_at": "2024-01-21T17:06:14.052Z",
                "results": [
                    {
                        "generated_text": "AI Response",
                        "generated_token_count": 5,
                        "input_token_count": 50,
                        "stop_reason": "eos_token",
                        "seed": 2123876088
                    }
                ]
            }
            """;

    public static String RESPONSE_WATSONX_EMBEDDING_API = """
            {
                "model_id": "%s",
                "results": [
                  {
                    "embedding": [
                      -0.006929283,
                      -0.005336422,
                      -0.024047505
                    ]
                  }
                ],
                "created_at": "2024-02-21T17:32:28Z",
                "input_token_count": 10
            }
            """;
    public static String RESPONSE_WATSONX_STREAMING_API = """
            id: 1
            event: message
            data: {}

            id: 2
            event: message
            data: {"modelId":"ibm/granite-13b-chat-v2","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.162Z","results":[{"generated_text":"","generated_token_count":0,"input_token_count":2,"stop_reason":"not_finished"}]}

            id: 3
            event: message
            data: {"model_id":"ibm/granite-13b-chat-v2","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.203Z","results":[{"generated_text":". ","generated_token_count":2,"input_token_count":0,"stop_reason":"not_finished"}]}

            id: 4
            event: message
            data: {"model_id":"ibm/granite-13b-chat-v2","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.223Z","results":[{"generated_text":"I'","generated_token_count":3,"input_token_count":0,"stop_reason":"not_finished"}]}

            id: 5
            event: message
            data: {"model_id":"ibm/granite-13b-chat-v2","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.243Z","results":[{"generated_text":"m ","generated_token_count":4,"input_token_count":0,"stop_reason":"not_finished"}]}

            id: 6
            event: message
            data: {"model_id":"ibm/granite-13b-chat-v2","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.262Z","results":[{"generated_text":"a beginner","generated_token_count":5,"input_token_count":0,"stop_reason":"max_tokens"}]}

            id: 7
            event: close
            data: {}
            """;
    public static final String RESPONSE_WATSONX_TOKENIZER_API = """
              {
              "model_id": "%s",
              "result": {
                "token_count": 11,
                "tokens": [
                  "Write",
                  "a",
                  "tag",
                  "line",
                  "for",
                  "an",
                  "alumni",
                  "associ",
                  "ation:",
                  "Together",
                  "we"
                ]
              }
            }
            """;

    WireMockServer iamServer;
    WireMockServer watsonServer;

    public WireMockUtil(WireMockServer watsonServer, WireMockServer iamServer) {
        this.watsonServer = watsonServer;
        this.iamServer = iamServer;
    }

    public IAMBuilder mockIAMBuilder(int status) {
        return new IAMBuilder(iamServer, status);
    }

    public WatsonxBuilder mockWatsonxBuilder(String apiURL, int status) {
        return new WatsonxBuilder(watsonServer, apiURL, status);
    }

    public WatsonxBuilder mockWatsonxBuilder(String apiURL, int status, String version) {
        return new WatsonxBuilder(watsonServer, apiURL, status, version);
    }

    public static StreamingResponseHandler<AiMessage> streamingResponseHandler(AtomicReference<AiMessage> streamingResponse) {
        return new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
            }

            @Override
            public void onError(Throwable error) {
                fail("Streaming failed: %s".formatted(error.getMessage()), error);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                streamingResponse.set(response.content());
            }
        };
    }

    public static class WatsonxBuilder {

        private MappingBuilder builder;
        private String token = BEARER_TOKEN;
        private String responseMediaType = MediaType.APPLICATION_JSON;
        private String response;
        private int status;
        private WireMockServer watsonServer;

        protected WatsonxBuilder(WireMockServer watsonServer, String apiURL, int status, String version) {
            this.watsonServer = watsonServer;
            this.status = status;
            this.builder = post(urlEqualTo(apiURL.formatted(version)));
        }

        protected WatsonxBuilder(WireMockServer watsonServer, String apiURL, int status) {
            this.watsonServer = watsonServer;
            this.status = status;
            this.builder = post(urlEqualTo(apiURL.formatted(VERSION)));
        }

        public WatsonxBuilder scenario(String currentState, String nextState) {
            builder = builder.inScenario("")
                    .whenScenarioStateIs(currentState)
                    .willSetStateTo(nextState);
            return this;
        }

        public WatsonxBuilder body(String body) {
            builder.withRequestBody(equalToJson(body));
            return this;
        }

        public WatsonxBuilder token(String token) {
            this.token = token;
            return this;
        }

        public WatsonxBuilder responseMediaType(String mediaType) {
            this.responseMediaType = mediaType;
            return this;
        }

        public WatsonxBuilder response(String response) {
            this.response = response;
            return this;
        }

        public void build() {
            watsonServer.stubFor(
                    builder
                            .withHeader("Authorization", equalTo("Bearer %s".formatted(token)))
                            .willReturn(aResponse()
                                    .withStatus(status)
                                    .withHeader("Content-Type", responseMediaType)
                                    .withBody(response)));
        }
    }

    public static class IAMBuilder {

        private MappingBuilder builder;
        private String apikey = API_KEY;
        private String grantType = GRANT_TYPE;
        private String responseMediaType = MediaType.APPLICATION_JSON;
        private String response = "";
        private int status;
        private WireMockServer iamServer;

        protected IAMBuilder(WireMockServer iamServer, int status) {
            this.iamServer = iamServer;
            this.status = status;
            this.builder = post(urlEqualTo(WireMockUtil.URL_IAM_GENERATE_TOKEN));
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
            this.grantType = grantType;
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
}
