package com.ibm.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.MediaType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

public class WireMockUtil {

    public static final int PORT_WATSONX_SERVER = 8089;
    public static final String URL_WATSONX_SERVER = "http://localhost:8089";
    public static final String URL_WATSONX_CHAT_API = "/ml/v1/text/generation?version=%s";
    public static final String URL_WATSONX_EMBEDDING_API = "/ml/v1/text/embeddings?version=%s";

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

    // ------- SCENARIO -------
    final String ERROR_401_TOKEN_EXPIRED = "ERROR_401_TOKEN_EXPIRED";
    final String GENERATE_TOKEN = "GENERATE_TOKEN";
    final String ERROR = "ERROR";
    // ------------------------

    private static final String IAM_200_RESPONSE = """
            {
                "access_token": "%s",
                "refresh_token": "not_supported",
                "token_type": "Bearer",
                "expires_in": 3600,
                "expiration": %d,
                "scope": "ibm openid"
            }
            """;

    WireMockServer iamServer;
    WireMockServer watsonServer;

    public WireMockUtil(WireMockServer watsonServer, WireMockServer iamServer) {
        this.watsonServer = watsonServer;
        this.iamServer = iamServer;
    }

    public void scenario_401_with_retry_token_expired(String finalReponse, String finalToken) {

        //
        // The first call to the IAMServer will return correctly a token, but it is
        // expired.
        //
        new IAMBuilder(iamServer, 200)
                .scenario(Scenario.STARTED, GENERATE_TOKEN)
                .response("tokenExpired", new Date())
                .build();

        //
        // The second call to the watsonServer will return an error message 401 -
        // authentication_token_expired
        //
        new WatsonxBuilder(watsonServer, URL_WATSONX_CHAT_API, 401)
                .scenario(Scenario.STARTED, ERROR_401_TOKEN_EXPIRED)
                .token("tokenExpired")
                .response("""
                            {
                                "errors": [
                                    {
                                        "code": "authentication_token_expired",
                                        "message": "Failed to authenticate the request due to an expired token"
                                    }
                                ],
                                "trace": "94d47ee87c07c8d7913b4566bd9336ea",
                                "status_code": 401
                            }
                        """)
                .build();

        //
        // The third call to the IAMServer will return correctly a token, which will
        // expire in an hour.
        //
        new IAMBuilder(iamServer, 200)
                .scenario(GENERATE_TOKEN, ERROR)
                .response(finalToken, new Date())
                .build();

        //
        // The last call to the watsonServer will return correctly a response.
        //
        new WatsonxBuilder(watsonServer, URL_WATSONX_CHAT_API, 200)
                .scenario(ERROR_401_TOKEN_EXPIRED, ERROR)
                .token(BEARER_TOKEN)
                .response("""
                            {
                                "model_id": "meta-llama/llama-2-70b-chat",
                                "created_at": "2024-01-19T20:30:14.326Z",
                                "results": [
                                    {
                                            "generated_text": "I'm an AI",
                                            "generated_token_count": 5,
                                            "input_token_count": 50,
                                            "stop_reason": "eos_token",
                                            "seed": 3058414515
                                    }
                                ]
                            }
                        """)
                .build();

        // This shouldn't be happen.
        new IAMBuilder(iamServer, 500)
                .scenario(ERROR, Scenario.STARTED)
                .build();

        new WatsonxBuilder(watsonServer, URL_WATSONX_CHAT_API, 500)
                .scenario(ERROR, Scenario.STARTED)
                .build();
    }

    public void error_401_authorization_rejected() {

        //
        // The first call to the IAMServer will return correctly wrong token
        //
        new IAMBuilder(iamServer, 200)
                .scenario(Scenario.STARTED, GENERATE_TOKEN)
                .response("tokenNotCorrect", new Date())
                .build();

        //
        // The second call to the watsonServer will return an error message 401 -
        // authentication_token_expired
        //
        new WatsonxBuilder(watsonServer, URL_WATSONX_CHAT_API, 401)
                .scenario(Scenario.STARTED, ERROR_401_TOKEN_EXPIRED)
                .token("tokenNotCorrect")
                .response(
                        """
                                    {
                                        "trace": "87c3e3f0d4473ebdd2dcf5a755634e33",
                                        "errors": [{
                                          "code": "authorization_rejected",
                                          "message": "Expected token [xxxxxx] to be composed of 2 or 3 parts separated by dots.",
                                          "target": {
                                            "type": "header",
                                            "name": "Authorization"
                                          }
                                        }],
                                        "status_code": "401"
                                      }
                                """)
                .build();

        // This shouldn't be happen.
        new IAMBuilder(iamServer, 500)
                .scenario(ERROR, Scenario.STARTED)
                .build();

        new WatsonxBuilder(watsonServer, URL_WATSONX_CHAT_API, 500)
                .scenario(ERROR, Scenario.STARTED)
                .build();
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
