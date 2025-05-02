package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public class WireMockUtil {

    public static final Long DEFAULT_TIME_LIMIT = 10000l;

    public static final int PORT_WATSONX_SERVER = 8089;
    public static final String URL_WATSONX_SERVER = "http://localhost:8089";
    public static final String URL_WATSONX_CHAT_API = "/ml/v1/text/chat?version=%s";
    public static final String URL_WATSONX_CHAT_STREAMING_API = "/ml/v1/text/chat_stream?version=%s";
    public static final String URL_WATSONX_GENERATION_API = "/ml/v1/text/generation?version=%s";
    public static final String URL_WATSONX_GENERATION_STREAMING_API = "/ml/v1/text/generation_stream?version=%s";
    public static final String URL_WATSONX_SCORING_API = "/ml/v1/text/rerank?version=%s";
    public static final String URL_WATSONX_EMBEDDING_API = "/ml/v1/text/embeddings?version=%s";
    public static final String URL_WATSONX_TOKENIZER_API = "/ml/v1/text/tokenization?version=%s";
    public static final String URL_WATSONX_TEXT_EXTRACTION_START_API = "/ml/v1/text/extractions?version=%s";
    public static final String URL_WATSONX_TEXT_EXTRACTION_RESULT_API = "/ml/v1/text/extractions/%s?project_id=%s&version=%s";

    public static final int PORT_IAM_SERVER = 8090;
    public static final String URL_IAM_SERVER = "http://localhost:8090";
    public static final String URL_IAM_GENERATE_TOKEN = "/identity/token";

    public static final int PORT_WX_SERVER = 8091;
    public static final String URL_WX_SERVER = "http://localhost:8091";
    public static final String URL_WX_AGENT_TOOL_RUN = "/v1-beta/utility_agent_tools/run";

    public static final int PORT_COS_SERVER = 8092;
    public static final String URL_COS_SERVER = "http://localhost:8092";

    public static final String API_KEY = "my_super_api_key";
    public static final String BEARER_TOKEN = "my_super_token";
    public static final String PROJECT_ID = "123123321321";
    public static final String GRANT_TYPE = "urn:ibm:params:oauth:grant-type:apikey";
    public static final String VERSION = "2025-04-23";
    public static final String DEFAULT_CHAT_MODEL = "meta-llama/llama-4-maverick-17b-128e-instruct-fp8";
    public static final String DEFAULT_EMBEDDING_MODEL = "ibm/granite-embedding-278m-multilingual";
    public static final String DEFAULT_SCORING_MODEL = "cross-encoder/ms-marco-minilm-l-12-v2";
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
    public static String RESPONSE_WATSONX_GENERATION_API = """
            {
                "model_id": "mistralai/mistral-large",
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
    public static String RESPONSE_WATSONX_CHAT_API = """
            {
                "id": "cmpl-15475d0dea9b4429a55843c77997f8a9",
                "model_id": "mistralai/mistral-large",
                "created": 1689958352,
                "created_at": "2023-07-21T16:52:32.190Z",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "AI Response"
                    },
                    "finish_reason": "stop"
                }],
                "usage": {
                    "completion_tokens": 47,
                    "prompt_tokens": 59,
                    "total_tokens": 106
                }
            }""";
    public static String RESPONSE_WATSONX_SCORING_API = """
            {
            "model_id": "cross-encoder/ms-marco-minilm-l-12-v2",
            "created_at": "2024-10-18T06:57:42.032Z",
            "results": [
                {
                    "index": 5,
                    "score": 9.720501899719238
                },
                {
                    "index": 1,
                    "score": 8.770895957946777
                },
                {
                    "index": 0,
                    "score": -2.5847978591918945
                },
                {
                    "index": 3,
                    "score": -3.349348306655884
                },
                {
                    "index": 4,
                    "score": -3.920926570892334
                },
                {
                    "index": 2,
                    "score": -4.939967155456543
                }
            ],
            "input_token_count": 318
            }""";
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
    public static String RESPONSE_WATSONX_CHAT_STREAMING_API = """
            id: 1
            event: message
            data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"role":"assistant"}}],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.072Z","usage":{"prompt_tokens":88,"total_tokens":88},"system":{"warnings":[{"message":"This model is a Non-IBM Product governed by a third-party license that may impose use restrictions and other obligations. By using this model you agree to its terms as identified in the following URL.","id":"disclaimer_warning","more_info":"https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx"},{"message":"The value of 'time_limit' for this model must be larger than 0 and not larger than 10m0s; it was set to 10m0s","id":"time_limit_out_of_range","additional_properties":{"limit":600000,"new_value":600000,"parameter":"time_limit","value":999000}}]}}

            id: 2
            event: message
            data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"content":" He"}}],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.073Z","usage":{"completion_tokens":1,"prompt_tokens":88,"total_tokens":89}}

            id: 3
            event: message
            data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":"stop","delta":{"content":"llo"}}],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.090Z","usage":{"completion_tokens":2,"prompt_tokens":88,"total_tokens":90}}

            id: 4
            event: message
            data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.715Z","usage":{"completion_tokens":36,"prompt_tokens":88,"total_tokens":124}}

            id: 5
            event: close
            data: {}
            """;
    public static String RESPONSE_WATSONX_GENERATION_STREAMING_API = """
            id: 1
            event: message
            data: {}

            id: 2
            event: message
            data: {"modelId":"mistralai/mistral-large","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.162Z","results":[{"generated_text":"","generated_token_count":0,"input_token_count":2,"stop_reason":"not_finished"}]}

            id: 3
            event: message
            data: {"model_id":"mistralai/mistral-large","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.203Z","results":[{"generated_text":". ","generated_token_count":2,"input_token_count":0,"stop_reason":"not_finished"}]}

            id: 4
            event: message
            data: {"model_id":"mistralai/mistral-large","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.223Z","results":[{"generated_text":"I'","generated_token_count":3,"input_token_count":0,"stop_reason":"not_finished"}]}

            id: 5
            event: message
            data: {"model_id":"mistralai/mistral-large","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.243Z","results":[{"generated_text":"m ","generated_token_count":4,"input_token_count":0,"stop_reason":"not_finished"}]}

            id: 6
            event: message
            data: {"model_id":"mistralai/mistral-large","model_version":"2.1.0","created_at":"2024-05-04T14:29:19.262Z","results":[{"generated_text":"a beginner","generated_token_count":5,"input_token_count":0,"stop_reason":"max_tokens"}]}

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
    public static final String RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API = """
            id: 1
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"role":"assistant"}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.490Z","usage":{"prompt_tokens":84,"total_tokens":84},"system":{"warnings":[{"message":"This model is a Non-IBM Product governed by a third-party license that may impose use restrictions and other obligations. By using this model you agree to its terms as identified in the following URL.","id":"disclaimer_warning","more_info":"https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx"},{"message":"The value of 'max_tokens' for this model was set to value 1024","id":"unspecified_max_token","additional_properties":{"limit":0,"new_value":1024,"parameter":"max_tokens","value":0}}]}}

            id: 2
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"id":"chatcmpl-tool-7cf5dfd7c52441e59a7585243b22a86a","type":"function","function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.546Z","usage":{"completion_tokens":4,"prompt_tokens":84,"total_tokens":88}}

            id: 3
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"sum","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.620Z","usage":{"completion_tokens":8,"prompt_tokens":84,"total_tokens":92}}

            id: 4
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":"{\\\"first\\\": 1"}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.768Z","usage":{"completion_tokens":16,"prompt_tokens":84,"total_tokens":100}}

            id: 5
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.786Z","usage":{"completion_tokens":17,"prompt_tokens":84,"total_tokens":101}}

            id: 6
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.805Z","usage":{"completion_tokens":18,"prompt_tokens":84,"total_tokens":102}}

            id: 7
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.823Z","usage":{"completion_tokens":19,"prompt_tokens":84,"total_tokens":103}}

            id: 8
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.842Z","usage":{"completion_tokens":20,"prompt_tokens":84,"total_tokens":104}}

            id: 9
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.861Z","usage":{"completion_tokens":21,"prompt_tokens":84,"total_tokens":105}}

            id: 10
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":", \\\"second\\\": 1"}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.879Z","usage":{"completion_tokens":22,"prompt_tokens":84,"total_tokens":106}}

            id: 11
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.897Z","usage":{"completion_tokens":23,"prompt_tokens":84,"total_tokens":107}}

            id: 12
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":""}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.916Z","usage":{"completion_tokens":24,"prompt_tokens":84,"total_tokens":108}}

            id: 13
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":"tool_calls","delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":"}"}}]}}],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.934Z","usage":{"completion_tokens":25,"prompt_tokens":84,"total_tokens":109}}

            id: 14
            event: message
            data: {"id":"chat-188595e69470446fb1740c98acfdfe12","model_id":"mistralai/mistral-large","choices":[],"created":1728811250,"model_version":"2.0.0","created_at":"2024-10-13T09:20:50.935Z","usage":{"completion_tokens":25,"prompt_tokens":84,"total_tokens":109}}

            id: 15
            event: close
            """;

    public static StreamingChatResponseHandler streamingChatResponseHandler(AtomicReference<ChatResponse> streamingResponse) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                streamingResponse.set(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                fail(error);
            }
        };
    }
}
