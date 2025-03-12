package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_GENERATION_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;
import io.quarkus.test.QuarkusUnitTest;

public class HttpErrorTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    ChatLanguageModel chatModel;

    @Test
    void not_registered_error() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 500)
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

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.chat("message"));
        assertEquals(500, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals("xxx", ex.details().errors().get(0).code());
        assertEquals("yyyy", ex.details().errors().get(0).message());
    }

    @Test
    void error_400_model_no_support_for_function() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 404)
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

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.chat("message"));
        assertEquals(400, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals("model_no_support_for_function", ex.details().errors().get(0).code());
    }

    @Test
    void error_400_json_type_error() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 400)
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

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.chat("message"));
        assertNotNull(ex.details());
        assertNotNull(ex.details().trace());
        assertEquals(400, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals("json_type_error", ex.details().errors().get(0).code());
    }

    @Test
    void error_404_model_not_supported() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 400)
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

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.chat("message"));
        assertNotNull(ex.details());
        assertNotNull(ex.details().trace());
        assertEquals(404, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals("model_not_supported", ex.details().errors().get(0).code());
    }

    @Test
    void error_400_json_validation_error() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 400)
                .response("""
                        {
                            "errors": [
                                {
                                    "code": "json_validation_error",
                                    "message": "Json document validation error: project_id should be a version 4 uuid "
                                }
                            ],
                            "trace": "xxx",
                            "status_code": 400
                        }
                        """)
                .build();

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.chat("message"));
        assertNotNull(ex.details());
        assertNotNull(ex.details().trace());
        assertEquals(400, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals("json_validation_error", ex.details().errors().get(0).code());
    }

    @Test
    void error_400_invalid_request_entity() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 400)
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

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.chat("message"));
        assertNotNull(ex.details());
        assertNotNull(ex.details().trace());
        assertEquals(400, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals("invalid_request_entity", ex.details().errors().get(0).code());
        assertEquals("Missing either space_id or project_id or wml_instance_crn", ex.details().errors().get(0).message());
    }

    @Test
    void error_500() {

        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();

        mockWatsonxBuilder(URL_WATSONX_GENERATION_API, 500)
                .response("{")
                .build();

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.chat("message"));
        assertEquals(500, ex.statusCode());
    }

    @Test
    void error_500_iam_generic() {

        mockIAMBuilder(500)
                .response("{")
                .build();

        ClientWebApplicationException ex = assertThrows(ClientWebApplicationException.class,
                () -> chatModel.chat("message"));
        assertEquals(500, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("HTTP 500 Server Error"));
    }

    @Test
    void error_400_iam_server() {
        mockIAMBuilder(400)
                .response("""
                        {
                            "errorCode": "BXNIM0415E",
                            "errorMessage": "Provided API key could not be found.",
                            "context": {
                                "requestId": "xxx"
                            }
                        }
                        """)
                .build();

        ClientWebApplicationException ex = assertThrowsExactly(
                ClientWebApplicationException.class,
                () -> chatModel.chat("message"));
        assertEquals(400, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("\"quarkus.langchain4j.watsonx.api-key\" is incorrect"));
    }

    @Test
    void error_500_iam_server() {
        mockIAMBuilder(500)
                .responseMediaType(MediaType.TEXT_PLAIN)
                .response("SUPER FATAL ERROR!")
                .build();
        WebApplicationException ex = assertThrowsExactly(ClientWebApplicationException.class,
                () -> chatModel.chat("message"));
        assertEquals(500, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("SUPER FATAL ERROR!"));
    }
}
