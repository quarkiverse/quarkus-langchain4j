package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_IAM_GENERATE_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;

import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.QuarkusHttpException;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class HttpErrorTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

    @Inject
    ChatModel chatModel;

    @Test
    void not_registered_error() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).reply(500, """
                {
                    "errors": [
                        {
                            "code": "xxx",
                            "message": "yyyy"
                        }
                    ],
                    "trace": "xxx",
                    "status_code": 500
                }
                """);

        // An error code that is not part of WatsonxError.Code is mapped on the http status code.
        InternalServerException ex = assertThrowsExactly(InternalServerException.class, () -> chatModel.chat("message"));
        assertEquals("yyyy", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertEquals(500, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("xxx", details.errors().get(0).code());
        assertEquals("yyyy", details.errors().get(0).message());
        assertNotRetried();
    }

    @Test
    void error_400_model_no_support_for_function() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).reply(404, """
                {
                    "errors": [
                        {
                            "code": "model_no_support_for_function",
                            "message": "Model 'ibm/granite-7b-lab' does not support function 'function_text_chat'",
                            "more_info": "https://cloud.ibm.com/apidocs/watsonx-ai"
                        }
                    ],
                    "trace": "xxx",
                    "status_code": 400
                }
                """);

        LangChain4jException ex = assertThrowsExactly(LangChain4jException.class, () -> chatModel.chat("message"));
        assertEquals("Model 'ibm/granite-7b-lab' does not support function 'function_text_chat'", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertEquals(400, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("model_no_support_for_function", details.errors().get(0).code());
        assertNotRetried();
    }

    @Test
    void error_400_json_type_error() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).reply(400,
                """
                        {
                            "errors": [
                                {
                                    "code": "json_type_error",
                                    "message": "Json field type error: response_format must be of type schemas.TextChatPropertyResponseFormat",
                                    "more_info": "https://cloud.ibm.com/apidocs/watsonx-ai"
                                }
                            ],
                            "trace": "xxx",
                            "status_code": 400
                        }
                        """);

        InvalidRequestException ex = assertThrowsExactly(InvalidRequestException.class, () -> chatModel.chat("message"));
        assertEquals("Json field type error: response_format must be of type schemas.TextChatPropertyResponseFormat",
                ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertNotNull(details);
        assertNotNull(details.trace());
        assertEquals(400, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("json_type_error", details.errors().get(0).code());
        assertNotRetried();
    }

    @Test
    void error_404_model_not_supported() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).reply(400, """
                {
                    "errors": [
                        {
                            "code": "model_not_supported",
                            "message": "Model 'meta-llama/llama-3-1-70b-instructs' is not supported",
                            "more_info": "https://cloud.ibm.com/apidocs/watsonx-ai"
                        }
                    ],
                    "trace": "91c784e9f44da953ebafc25933809817",
                    "status_code": 404
                }
                """);

        ModelNotFoundException ex = assertThrowsExactly(ModelNotFoundException.class, () -> chatModel.chat("message"));
        assertEquals("Model 'meta-llama/llama-3-1-70b-instructs' is not supported", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertNotNull(details);
        assertNotNull(details.trace());
        assertEquals(404, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("model_not_supported", details.errors().get(0).code());
        assertNotRetried();
    }

    @Test
    void error_400_json_validation_error() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).reply(400, """
                {
                    "errors": [
                        {
                            "code": "json_validation_error",
                            "message": "Json document validation error: project_id should be a version 4 uuid"
                        }
                    ],
                    "trace": "xxx",
                    "status_code": 400
                }
                """);

        InvalidRequestException ex = assertThrowsExactly(InvalidRequestException.class, () -> chatModel.chat("message"));
        assertEquals("Json document validation error: project_id should be a version 4 uuid", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertNotNull(details);
        assertNotNull(details.trace());
        assertEquals(400, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("json_validation_error", details.errors().get(0).code());
        assertNotRetried();
    }

    @Test
    void error_400_invalid_request_entity() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).reply(400, """
                {
                    "errors": [
                        {
                            "code": "invalid_request_entity",
                            "message": "Missing either space_id or project_id or wml_instance_crn"
                        }
                    ],
                    "trace": "xxx",
                    "status_code": 400
                }
                """);

        InvalidRequestException ex = assertThrowsExactly(InvalidRequestException.class, () -> chatModel.chat("message"));
        assertEquals("Missing either space_id or project_id or wml_instance_crn", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertNotNull(details);
        assertNotNull(details.trace());
        assertEquals(400, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("invalid_request_entity", details.errors().get(0).code());
        assertEquals("Missing either space_id or project_id or wml_instance_crn", details.errors().get(0).message());
        assertNotRetried();
    }

    @Test
    void error_500() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).reply(500, "{");

        assertThrowsExactly(RuntimeException.class, () -> chatModel.chat("message"));
        assertNotRetried();
    }

    @Test
    void error_500_iam_generic() {

        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN).reply(500, "{");

        assertThrows(RuntimeException.class, () -> chatModel.chat("message"));
        assertThat(iamRequests()).isNotEmpty();
        assertThat(apiRequests()).isEmpty();
    }

    @Test
    void error_400_iam_server() {
        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN).reply(400, """
                {
                    "errorCode": "BXNIM0415E",
                    "errorMessage": "Provided API key could not be found.",
                    "errorDetails": "details",
                    "context": {
                        "requestId": "xxx"
                    }
                }
                """);

        // "BXNIM0415E" is not part of WatsonxError.Code, so the exception is mapped on the http status code.
        InvalidRequestException ex = assertThrowsExactly(InvalidRequestException.class, () -> chatModel.chat("message"));
        assertEquals("Provided API key could not be found.", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var detail = watsonxEx.details().orElseThrow().errors().get(0);
        assertEquals(400, watsonxEx.statusCode());
        assertEquals("BXNIM0415E", detail.code());
        assertEquals("Provided API key could not be found.", detail.message());
        assertEquals("details", detail.moreInfo());
        assertThat(iamRequests()).hasSize(1);
        assertThat(apiRequests()).isEmpty();
    }

    @Test
    void error_429_exposes_response_headers() {

        mockIamToken();

        mock.raw().on("POST", URL_WATSONX_CHAT_API).replies(RawResponse.of(429,
                Map.of("Content-Type", "application/json", "Retry-After", "7"), """
                        {
                            "errors": [
                                {
                                    "code": "token_quota_reached",
                                    "message": "Token quota reached"
                                }
                            ],
                            "trace": "xxx",
                            "status_code": 429
                        }
                        """));

        RateLimitException ex = assertThrowsExactly(RateLimitException.class, () -> chatModel.chat("message"));
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var httpEx = assertInstanceOf(QuarkusHttpException.class, watsonxEx.getCause());
        assertEquals(429, httpEx.statusCode());
        assertEquals(Optional.of("7"), httpEx.header("Retry-After"));
        // a 429 is retried
        assertThat(apiRequests()).isNotEmpty()
                .allSatisfy(request -> assertWatsonxRequest(request, URL_WATSONX_CHAT_API, BEARER_TOKEN));
    }

    @Test
    void error_500_iam_server() {
        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN)
                .replies(RawResponse.of(500, Map.of("Content-Type", "text/plain"), "SUPER FATAL ERROR!"));

        LangChain4jException ex = assertThrowsExactly(LangChain4jException.class, () -> chatModel.chat("message"));
        assertTrue(ex.getMessage().contains("SUPER FATAL ERROR!"));
        assertThat(iamRequests()).hasSize(1);
        assertThat(apiRequests()).isEmpty();
    }

    /**
     * A token that expires at once, so that every test requests its own.
     */
    private void mockIamToken() {
        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN).replies(iamToken(BEARER_TOKEN, new Date()));
    }

    private void assertNotRetried() {
        assertThat(iamRequests()).hasSize(1);
        assertThat(apiRequests()).singleElement()
                .satisfies(request -> assertWatsonxRequest(request, URL_WATSONX_CHAT_API, BEARER_TOKEN));
    }
}
