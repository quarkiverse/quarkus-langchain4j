package io.quarkiverse.langchain4j.mockapi.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpec.EndpointKey;

public final class ProviderSpecs {

    private static final String OPENAI_PREFIX = "quarkus.langchain4j.openai";

    public static final ProviderSpec OPENAI = new ProviderSpec("openai", OPENAI_PREFIX, "",
            List.of(new EndpointKey("base-url", "/v1")),
            Map.of("api-key", "mock-api-key"),
            Map.of());

    private static final String AZURE_PREFIX = "quarkus.langchain4j.azure-openai";
    private static final String AZURE_SUFFIX = "/openai/deployments/mock-deployment";

    public static final ProviderSpec AZURE_OPENAI = new ProviderSpec("azure-openai", AZURE_PREFIX, "",
            List.of(new EndpointKey("endpoint", AZURE_SUFFIX),
                    new EndpointKey("chat-model.endpoint", AZURE_SUFFIX),
                    new EndpointKey("embedding-model.endpoint", AZURE_SUFFIX),
                    new EndpointKey("image-model.endpoint", AZURE_SUFFIX),
                    new EndpointKey("audio-transcription-model.endpoint", AZURE_SUFFIX)),
            Map.of("api-key", "mock-api-key", "deployment-name", "mock-deployment"),
            // the Azure client refuses to start when both api-key and ad-token are set
            Map.of("api-key", List.of("ad-token", "chat-model.ad-token", "embedding-model.ad-token", "image-model.ad-token",
                    "audio-transcription-model.ad-token")));

    private static final String ANTHROPIC_PREFIX = "quarkus.langchain4j.anthropic";

    public static final ProviderSpec ANTHROPIC = new ProviderSpec("anthropic", ANTHROPIC_PREFIX, "/anthropic",
            List.of(new EndpointKey("base-url", "/v1")),
            Map.of("api-key", "mock-api-key"),
            Map.of());

    private static final String GOOGLE_GENAI_PREFIX = "quarkus.langchain4j.google.genai";

    // the Google GenAI SDK appends the API version (v1beta) to the base URL itself
    public static final ProviderSpec GOOGLE_GENAI = new ProviderSpec("google-genai", GOOGLE_GENAI_PREFIX, "/google-genai",
            List.of(new EndpointKey("base-url", "")),
            Map.of("api-key", "mock-api-key"),
            Map.of());

    private static final String AI_GEMINI_PREFIX = "quarkus.langchain4j.ai.gemini";

    public static final ProviderSpec AI_GEMINI = new ProviderSpec("ai-gemini", AI_GEMINI_PREFIX, "/ai-gemini",
            List.of(new EndpointKey("base-url", "/v1beta")),
            Map.of("api-key", "mock-api-key"),
            Map.of());

    /**
     * The access token the Vertex AI providers send to the mock instead of one from Google credentials.
     */
    public static final String GOOGLE_ACCESS_TOKEN = "mock-access-token";
    private static final String VERTEX_AI_GEMINI_PREFIX = "quarkus.langchain4j.vertexai.gemini";

    // the client appends v1/projects/{project}/locations/{location}/publishers/{publisher}/models/... to the base URL
    public static final ProviderSpec VERTEX_AI_GEMINI = new ProviderSpec("vertexai-gemini", VERTEX_AI_GEMINI_PREFIX,
            "/vertexai-gemini",
            List.of(new EndpointKey("base-url", "")),
            Map.of("project-id", "mock-project", "location", "us-central1"),
            Map.of());

    private static final String VERTEX_AI_PREFIX = "quarkus.langchain4j.vertexai";

    public static final ProviderSpec VERTEX_AI = new ProviderSpec("vertexai", VERTEX_AI_PREFIX, "/vertexai",
            List.of(new EndpointKey("base-url", "")),
            Map.of("project-id", "mock-project", "location", "us-central1"),
            Map.of())
            .withCredentialsBean("io.quarkiverse.langchain4j.mockapi.internal.MockVertexAiAuthProvider");

    private static final String MISTRAL_AI_PREFIX = "quarkus.langchain4j.mistralai";

    public static final ProviderSpec MISTRAL_AI = new ProviderSpec("mistralai", MISTRAL_AI_PREFIX, "/mistralai",
            List.of(new EndpointKey("base-url", "/v1")),
            Map.of("api-key", "mock-api-key"),
            Map.of());

    private static final String OPENSHIFT_AI_PREFIX = "quarkus.langchain4j.openshift-ai";

    // the Caikit endpoint is {base-url}/v1/task/text-generation with a base-url ending in /api; the recorder refuses to
    // start with the default (dummy) model id
    public static final ProviderSpec OPENSHIFT_AI = new ProviderSpec("openshift-ai", OPENSHIFT_AI_PREFIX, "/openshift-ai",
            List.of(new EndpointKey("base-url", "/api")),
            Map.of("chat-model.model-id", "mock-model"),
            Map.of());

    private static final String OLLAMA_PREFIX = "quarkus.langchain4j.ollama";

    // the clients append /api/chat and /api/embed to the base URL; Ollama has no credentials
    public static final ProviderSpec OLLAMA = new ProviderSpec("ollama", OLLAMA_PREFIX, "/ollama",
            List.of(new EndpointKey("base-url", "")),
            Map.of(),
            Map.of());

    private static final String COHERE_PREFIX = "quarkus.langchain4j.cohere";

    // the client appends /v2/rerank to the base URL; the recorder refuses to start with the default (dummy) api-key
    public static final ProviderSpec COHERE = new ProviderSpec("cohere", COHERE_PREFIX, "/cohere",
            List.of(new EndpointKey("base-url", "")),
            Map.of("api-key", "mock-api-key"),
            Map.of());

    private static final String HUGGING_FACE_PREFIX = "quarkus.langchain4j.huggingface";

    // each model kind posts to a full endpoint URL of its own; the client sends the api-key as a bearer token only when
    // it is not the default (dummy)
    public static final ProviderSpec HUGGING_FACE = new ProviderSpec("huggingface", HUGGING_FACE_PREFIX, "/huggingface",
            List.of(new EndpointKey("chat-model.inference-endpoint-url", "/chat"),
                    new EndpointKey("embedding-model.inference-endpoint-url", "/embeddings")),
            Map.of("api-key", "mock-api-key"),
            Map.of());

    private static final String TYPESAFE_PREFIX = "quarkus.langchain4j.typesafe";

    // the client appends /v1/systemone to the base URL; the recorder requires an api-key only with the default base-url,
    // so none is filled in and no Authorization header is sent
    public static final ProviderSpec TYPESAFE = new ProviderSpec("typesafe", TYPESAFE_PREFIX, "/typesafe",
            List.of(new EndpointKey("base-url", "")),
            Map.of(),
            Map.of());

    private static final String WATSONX_PREFIX = "quarkus.langchain4j.watsonx";

    // the clients append /ml/v1/..., /ml/gateway/v1/... and /identity/token to their base URLs, so the IAM token request
    // is answered by the mock too, which makes the placeholder api-key work; the chat, embedding, scoring and moderation
    // models refuse to start without a project-id or a space-id. The cos-url keys are not overridden: setting one would
    // turn on its optional group (text-extraction, text-classification, schema.create), which then needs more config.
    // built-in-tool.base-url is not overridden either: it exists only for the default model, and an endpoint key is
    // also published for every named model
    public static final ProviderSpec WATSONX = new ProviderSpec("watsonx", WATSONX_PREFIX, "/watsonx",
            List.of(new EndpointKey("base-url", ""),
                    new EndpointKey("iam.base-url", "")),
            Map.of("api-key", "mock-api-key", "project-id", "mock-project"),
            Map.of("project-id", List.of("space-id")));

    /**
     * The name of the bean that gives the Bedrock clients fixed AWS credentials for the mock.
     */
    public static final String AWS_CREDENTIALS_BEAN = "mockApiAwsCredentials";

    private static final String BEDROCK_PREFIX = "quarkus.langchain4j.bedrock";

    // the AWS SDK appends /model/{modelId}/converse, .../converse-stream and .../invoke to the endpoint override. The
    // recorder reads each aws.* key of a model's chat-model or embedding-model group first and falls back to the
    // provider level, so the endpoint is overridden at every level (a user's per-model value would otherwise win) while
    // the region (SigV4 signs with one) and the credentials provider bean name are filled in at the provider level only
    public static final ProviderSpec BEDROCK = new ProviderSpec("bedrock", BEDROCK_PREFIX, "/bedrock",
            List.of(new EndpointKey("aws.endpoint-override", ""),
                    new EndpointKey("chat-model.aws.endpoint-override", ""),
                    new EndpointKey("embedding-model.aws.endpoint-override", "")),
            Map.of("aws.region", "us-east-1", "aws.credentials-provider", AWS_CREDENTIALS_BEAN),
            Map.of())
            .withCredentialsBean("io.quarkiverse.langchain4j.mockapi.internal.MockAwsCredentialsProvider");

    private static final List<ProviderSpec> ALL = List.of(OPENAI, AZURE_OPENAI, ANTHROPIC, GOOGLE_GENAI, AI_GEMINI,
            VERTEX_AI_GEMINI, VERTEX_AI, MISTRAL_AI, OPENSHIFT_AI, OLLAMA, COHERE, HUGGING_FACE, TYPESAFE, WATSONX,
            BEDROCK);

    private ProviderSpecs() {
    }

    public static List<ProviderSpec> all() {
        return ALL;
    }

    public static List<ProviderSpec> byIds(Collection<String> ids) {
        return ALL.stream().filter(s -> ids.contains(s.id())).toList();
    }
}
