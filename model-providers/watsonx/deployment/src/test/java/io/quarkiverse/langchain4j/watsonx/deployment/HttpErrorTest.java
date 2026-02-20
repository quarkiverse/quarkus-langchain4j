package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.exception.WatsonxException;

import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.QuarkusUnitTest;

public class HttpErrorTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    ChatModel chatModel;

    @Test
    void not_registered_error() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 500)
                .responseMediaType(MediaType.APPLICATION_JSON)
                .response("""
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
                        """)
                .build();

        LangChain4jException ex = assertThrowsExactly(LangChain4jException.class, () -> chatModel.chat("message"));
        assertEquals("yyyy", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertEquals(500, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("xxx", details.errors().get(0).code());
        assertEquals("yyyy", details.errors().get(0).message());
    }

    @Test
    void error_400_model_no_support_for_function() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 404)
                .responseMediaType(MediaType.APPLICATION_JSON)
                .response("""
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
                        """)
                .build();

        LangChain4jException ex = assertThrowsExactly(LangChain4jException.class, () -> chatModel.chat("message"));
        assertEquals("Model 'ibm/granite-7b-lab' does not support function 'function_text_chat'", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertEquals(400, details.statusCode());
        assertNotNull(details.errors());
        assertEquals(1, details.errors().size());
        assertEquals("model_no_support_for_function", details.errors().get(0).code());
    }

    @Test
    void error_400_json_type_error() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 400)
                .response(
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
                                """)
                .build();

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
    }

    @Test
    void error_404_model_not_supported() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 400)
                .response("""
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
                        """)
                .build();

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
    }

    @Test
    void error_400_json_validation_error() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 400)
                .response("""
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
                        """)
                .build();

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
    }

    @Test
    void error_400_invalid_request_entity() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 400)
                .response("""
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
                        """)
                .build();

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
    }

    @Test
    void error_500() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 500)
                .response("{")
                .build();

        assertThrowsExactly(RuntimeException.class, () -> chatModel.chat("message"));
    }

    @Test
    void error_500_iam_generic() {

        mockIAMBuilder(500)
                .response("{")
                .build();

        assertThrows(RuntimeException.class, () -> chatModel.chat("message"));
    }

    @Test
    void error_400_iam_server() {
        mockIAMBuilder(400)
                .response("""
                        {
                            "errorCode": "BXNIM0415E",
                            "errorMessage": "Provided API key could not be found.",
                            "errorDetails": "details",
                            "context": {
                                "requestId": "xxx"
                            }
                        }
                        """)
                .build();

        LangChain4jException ex = assertThrowsExactly(LangChain4jException.class, () -> chatModel.chat("message"));
        assertEquals("Provided API key could not be found.", ex.getMessage());
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var detail = watsonxEx.details().orElseThrow().errors().get(0);
        assertEquals(400, watsonxEx.statusCode());
        assertEquals("BXNIM0415E", detail.code());
        assertEquals("Provided API key could not be found.", detail.message());
        assertEquals("details", detail.moreInfo());
    }

    @Test
    void error_500_iam_server() {
        mockIAMBuilder(500)
                .responseMediaType(MediaType.TEXT_PLAIN)
                .response("SUPER FATAL ERROR!")
                .build();
        RuntimeException ex = assertThrowsExactly(RuntimeException.class,
                () -> chatModel.chat("message"));
        assertTrue(ex.getMessage().contains("SUPER FATAL ERROR!"));
    }
}
