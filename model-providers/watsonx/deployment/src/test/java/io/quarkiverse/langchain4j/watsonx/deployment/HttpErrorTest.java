package io.quarkiverse.langchain4j.watsonx.deployment;

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
import io.quarkiverse.langchain4j.watsonx.bean.WatsonxError;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;
import io.quarkus.test.QuarkusUnitTest;

public class HttpErrorTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    ChatLanguageModel chatModel;

    @Test
    void error_404_model_not_supported() {

        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 404)
                .responseMediaType(MediaType.APPLICATION_JSON)
                .response("""
                        {
                            "errors": [
                                {
                                    "code": "model_not_supported",
                                    "message": "Model 'meta-llama/llama-2-70b-chats' is not supported"
                                }
                            ],
                            "trace": "xxx",
                            "status_code": 404
                        }
                        """)
                .build();

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.generate("message"));
        assertEquals(404, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals(WatsonxError.Code.MODEL_NOT_SUPPORTED, ex.details().errors().get(0).code());
    }

    @Test
    void error_400_json_validation_error() {

        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 400)
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

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.generate("message"));
        assertNotNull(ex.details());
        assertNotNull(ex.details().trace());
        assertEquals(400, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals(WatsonxError.Code.JSON_VALIDATION_ERROR, ex.details().errors().get(0).code());
    }

    @Test
    void error_400_invalid_request_entity() {

        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 400)
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

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.generate("message"));
        assertNotNull(ex.details());
        assertNotNull(ex.details().trace());
        assertEquals(400, ex.details().statusCode());
        assertNotNull(ex.details().errors());
        assertEquals(1, ex.details().errors().size());
        assertEquals(WatsonxError.Code.INVALID_REQUEST_ENTITY, ex.details().errors().get(0).code());
        assertEquals("Missing either space_id or project_id or wml_instance_crn", ex.details().errors().get(0).message());
    }

    @Test
    void error_500() {

        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 500)
                .response("{")
                .build();

        WatsonxException ex = assertThrowsExactly(WatsonxException.class, () -> chatModel.generate("message"));
        assertEquals(500, ex.statusCode());
    }

    @Test
    void error_500_iam_generic() {

        mockServers.mockIAMBuilder(500)
                .response("{")
                .build();

        ClientWebApplicationException ex = assertThrows(ClientWebApplicationException.class,
                () -> chatModel.generate("message"));
        assertEquals(500, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("HTTP 500 Server Error"));
    }

    @Test
    void error_400_iam_server() {
        mockServers.mockIAMBuilder(400)
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
                () -> chatModel.generate("message"));
        assertEquals(400, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("\"quarkus.langchain4j.watsonx.api-key\" is incorrect"));
    }

    @Test
    void error_500_iam_server() {
        mockServers.mockIAMBuilder(500)
                .responseMediaType(MediaType.TEXT_PLAIN)
                .response("SUPER FATAL ERROR!")
                .build();
        WebApplicationException ex = assertThrowsExactly(ClientWebApplicationException.class,
                () -> chatModel.generate("message"));
        assertEquals(500, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("SUPER FATAL ERROR!"));
    }
}
