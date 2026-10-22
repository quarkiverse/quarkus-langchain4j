package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.restassured.response.ValidatableResponse;

/**
 * Builds the A2A JSON-RPC requests the tests send, and posts them the way a client of protocol version 1.0 does.
 */
public final class JsonRpc {

    public static final String WEATHER_QUESTION = "what is the weather in LA?";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonRpc() {
    }

    public static ValidatableResponse post(String body) {
        return post("/", body);
    }

    /**
     * The path matters to the JSON-RPC binding: whatever follows the root names the tenant the request is for.
     */
    public static ValidatableResponse post(String path, String body) {
        return given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body(body)
                .when().post(path)
                .then()
                .statusCode(200);
    }

    public static MessageRequest sendMessage(String text) {
        return new MessageRequest("SendMessage", text);
    }

    public static MessageRequest sendStreamingMessage(String text) {
        return new MessageRequest("SendStreamingMessage", text);
    }

    public static String cancelTask(String taskId) {
        ObjectNode request = request("CancelTask");
        request.putObject("params").put("id", taskId);
        return request.toString();
    }

    public static String getTask(String taskId) {
        ObjectNode request = request("GetTask");
        request.putObject("params").put("id", taskId);
        return request.toString();
    }

    public static String getExtendedAgentCard() {
        ObjectNode request = request("GetExtendedAgentCard");
        request.putObject("params");
        return request.toString();
    }

    private static ObjectNode request(String method) {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", method);
        return request;
    }

    public static final class MessageRequest {

        private final String method;
        private final String text;
        private String contextId = "context-1";
        private String messageId = "message-1";
        private boolean returnImmediately;
        private String tenant;

        private MessageRequest(String method, String text) {
            this.method = method;
            this.text = text;
        }

        public MessageRequest contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        public MessageRequest messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        /**
         * Asks for the task back while the agent is still working on it, instead of once it is done.
         */
        public MessageRequest returnImmediately() {
            this.returnImmediately = true;
            return this;
        }

        public MessageRequest tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        public String json() {
            ObjectNode request = request(method);
            ObjectNode params = request.putObject("params");
            if (tenant != null) {
                params.put("tenant", tenant);
            }
            ObjectNode message = params.putObject("message");
            message.put("messageId", messageId);
            message.put("contextId", contextId);
            message.put("role", "ROLE_USER");
            message.putArray("parts").addObject().put("text", text);
            params.putObject("configuration").put("returnImmediately", returnImmediately);
            return request.toString();
        }

        public ValidatableResponse post() {
            return JsonRpc.post(json());
        }
    }
}
