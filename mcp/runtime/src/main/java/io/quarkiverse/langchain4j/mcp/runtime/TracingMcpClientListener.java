package io.quarkiverse.langchain4j.mcp.runtime;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpDiscoverResult;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpMetaSupplier;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.progress.McpProgressNotification;
import dev.langchain4j.mcp.protocol.McpCallToolParams;
import dev.langchain4j.mcp.protocol.McpCancellationParams;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpClientMethod;
import dev.langchain4j.mcp.protocol.McpClientNotification;
import dev.langchain4j.mcp.protocol.McpClientParams;
import dev.langchain4j.mcp.protocol.McpClientRequest;
import dev.langchain4j.mcp.protocol.McpGetPromptParams;
import dev.langchain4j.mcp.protocol.McpInitializeParams;
import dev.langchain4j.mcp.protocol.McpReadResourceParams;
import dev.langchain4j.mcp.protocol.McpSubscribeResourceParams;
import dev.langchain4j.mcp.protocol.McpUnsubscribeResourceParams;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpTransportType;
import io.quarkus.arc.Arc;

/**
 * Creates OpenTelemetry spans for MCP client operations and propagates the trace context
 * to the MCP server through the {@code _meta} property bag of every outgoing message.
 * <p>
 * Follows the OpenTelemetry semantic conventions for MCP: a {@code CLIENT} span is created for each
 * request or notification sent by the client and a {@code SERVER} span for each request or
 * notification initiated by the server.
 * <p>
 * Spans are never made current, because the MCP client may report the completion of an operation
 * on a different thread than the one that started it (for example with asynchronous tool
 * execution). Instead, as the {@link McpMetaSupplier} of the client, this class finds the span that
 * an outgoing message belongs to and injects its context into the message.
 *
 * @see <a href="https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/mcp.md">
 *      Semantic conventions for MCP</a>
 */
public class TracingMcpClientListener implements McpClientListener, McpMetaSupplier {

    static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");
    static final AttributeKey<String> MCP_PROTOCOL_VERSION = AttributeKey.stringKey("mcp.protocol.version");
    static final AttributeKey<String> MCP_SESSION_ID = AttributeKey.stringKey("mcp.session.id");
    static final AttributeKey<String> MCP_RESOURCE_URI = AttributeKey.stringKey("mcp.resource.uri");
    static final AttributeKey<String> JSONRPC_REQUEST_ID = AttributeKey.stringKey("jsonrpc.request.id");
    static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    static final AttributeKey<String> GEN_AI_PROMPT_NAME = AttributeKey.stringKey("gen_ai.prompt.name");
    static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    static final AttributeKey<String> RPC_RESPONSE_STATUS_CODE = AttributeKey.stringKey("rpc.response.status_code");
    static final AttributeKey<String> NETWORK_TRANSPORT = AttributeKey.stringKey("network.transport");
    static final AttributeKey<String> NETWORK_PROTOCOL_NAME = AttributeKey.stringKey("network.protocol.name");
    static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");
    static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");

    private static final String PROTOCOL_VERSION_META_KEY = "io.modelcontextprotocol/protocolVersion";

    private final Tracer tracer;
    private final TextMapPropagator propagator;
    private final String networkTransport;
    private final String networkProtocolName;
    private final String serverAddress;
    private final Long serverPort;
    private final Supplier<String> sessionIdSupplier;
    private final Logger log = Logger.getLogger(TracingMcpClientListener.class);

    /**
     * Spans of the operations in progress, keyed by the identity of the context the MCP client
     * reports them with.
     */
    private final Map<McpCallContext, ActiveSpan> activeSpans = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Spans of the operations started by the current thread. Some operations send more than one
     * message (list operations fetch every page with a separate request, and multi round-trip
     * requests are retried with a new request), each with its own context that the listener is not
     * told about. These messages are sent by the thread that started the operation, so they are
     * matched with the most recent span of this thread that has the same method.
     */
    private final ThreadLocal<Deque<ActiveSpan>> spansStartedByThread = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * The protocol version the client speaks with the server, for the messages that don't carry it
     * themselves (all messages of the legacy protocol, which agrees on the version during initialization).
     */
    private volatile String protocolVersion;

    private static final class ActiveSpan {
        final Span span;
        final Context context;
        final McpClientMethod method;
        volatile String requestId;
        volatile String protocolVersion;
        volatile boolean ended;

        ActiveSpan(Span span, Context context, McpClientMethod method) {
            this.span = span;
            this.context = context;
            this.method = method;
        }
    }

    /**
     * Used by the MCP client recorder, which creates the listener reflectively, so that
     * the OpenTelemetry classes are only touched when OpenTelemetry is present.
     *
     * @param transportType the transport the MCP client uses
     * @param url the URL of the MCP server, or {@code null} for the stdio transport
     * @param sessionIdSupplier supplies the MCP session ID, or {@code null} if the transport has no sessions
     */
    public TracingMcpClientListener(McpTransportType transportType, String url, Supplier<String> sessionIdSupplier) {
        this(Arc.container().select(Tracer.class).get(),
                Arc.container().select(OpenTelemetry.class).get().getPropagators().getTextMapPropagator(),
                transportType, url, sessionIdSupplier);
    }

    public TracingMcpClientListener(Tracer tracer, TextMapPropagator propagator, McpTransportType transportType,
            String url, Supplier<String> sessionIdSupplier) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.sessionIdSupplier = sessionIdSupplier;
        if (transportType == McpTransportType.STDIO) {
            this.networkTransport = "pipe";
            this.networkProtocolName = null;
        } else {
            this.networkTransport = "tcp";
            this.networkProtocolName = transportType == McpTransportType.WEBSOCKET ? "websocket" : "http";
        }
        URI uri = url != null ? URI.create(url) : null;
        if (uri != null && uri.getHost() != null) {
            this.serverAddress = uri.getHost();
            this.serverPort = (long) (uri.getPort() != -1 ? uri.getPort() : defaultPort(uri.getScheme()));
        } else {
            this.serverAddress = null;
            this.serverPort = null;
        }
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    // ========== initialize (legacy protocol) ==========

    @Override
    public void beforeInitialize(McpCallContext context) {
        ActiveSpan activeSpan = startClientSpan(context, null, false, null);
        if (requestParams(context) instanceof McpInitializeParams params) {
            activeSpan.protocolVersion = params.getProtocolVersion();
        }
    }

    @Override
    public void afterInitialize(McpCallContext context) {
        if (requestParams(context) instanceof McpInitializeParams params) {
            protocolVersion = params.getProtocolVersion();
        }
        endSpan(context, null);
    }

    @Override
    public void onInitializeError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    // ========== server/discover (2026-07-28 protocol) ==========

    @Override
    public void beforeServerDiscover(McpCallContext context) {
        startClientSpan(context, null, false, null);
    }

    @Override
    public void afterServerDiscover(McpCallContext context, McpDiscoverResult result) {
        endSpan(context, null);
    }

    @Override
    public void onServerDiscoverError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    // ========== tools ==========

    @Override
    public void beforeExecuteTool(McpCallContext context) {
        String toolName = requestParams(context) instanceof McpCallToolParams params ? params.getName() : null;
        startClientSpan(context, toolName, false, builder -> {
            builder.setAttribute(GEN_AI_OPERATION_NAME, "execute_tool");
            if (toolName != null) {
                builder.setAttribute(GEN_AI_TOOL_NAME, toolName);
            }
        });
    }

    @Override
    public void afterExecuteTool(McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {
        endSpan(context, null, result != null && result.isError() ? "tool_error" : null);
    }

    @Override
    public void onExecuteToolError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforeToolsList(McpCallContext context) {
        startListSpan(context);
    }

    @Override
    public void afterToolsList(McpCallContext context, List<ToolSpecification> tools) {
        endSpan(context, null);
    }

    @Override
    public void onToolsListError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    // ========== resources ==========

    @Override
    public void beforeResourceGet(McpCallContext context) {
        String uri = requestParams(context) instanceof McpReadResourceParams params ? params.getUri() : null;
        startClientSpan(context, null, false, builder -> setResourceUri(builder, uri));
    }

    @Override
    public void afterResourceGet(McpCallContext context, McpReadResourceResult result, Map<String, Object> rawResult) {
        endSpan(context, null);
    }

    @Override
    public void onResourceGetError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforeResourcesList(McpCallContext context) {
        startListSpan(context);
    }

    @Override
    public void afterResourcesList(McpCallContext context, List<McpResource> resources) {
        endSpan(context, null);
    }

    @Override
    public void onResourcesListError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforeResourceTemplatesList(McpCallContext context) {
        startListSpan(context);
    }

    @Override
    public void afterResourceTemplatesList(McpCallContext context, List<McpResourceTemplate> templates) {
        endSpan(context, null);
    }

    @Override
    public void onResourceTemplatesListError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforeResourceSubscribe(McpCallContext context) {
        String uri = requestParams(context) instanceof McpSubscribeResourceParams params ? params.getUri() : null;
        startClientSpan(context, null, false, builder -> setResourceUri(builder, uri));
    }

    @Override
    public void afterResourceSubscribe(McpCallContext context) {
        endSpan(context, null);
    }

    @Override
    public void onResourceSubscribeError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforeResourceUnsubscribe(McpCallContext context) {
        String uri = requestParams(context) instanceof McpUnsubscribeResourceParams params ? params.getUri() : null;
        startClientSpan(context, null, false, builder -> setResourceUri(builder, uri));
    }

    @Override
    public void afterResourceUnsubscribe(McpCallContext context) {
        endSpan(context, null);
    }

    @Override
    public void onResourceUnsubscribeError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforeResourcesSubscribe(McpCallContext context, List<String> uris) {
        // the span covers the subscriptions/listen request until the server acknowledges it,
        // not the whole lifetime of the subscription
        startClientSpan(context, null, false, null);
    }

    @Override
    public void afterResourcesSubscribe(McpCallContext context, long subscriptionId, List<String> uris) {
        endSpan(context, null);
    }

    @Override
    public void onResourcesSubscribeError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    // beforeResourcesUnsubscribe/afterResourcesUnsubscribe: unsubscribing sends a notifications/cancelled
    // notification (or no message at all, if the transport closes the stream instead),
    // which gets its span when its _meta is supplied, see apply()

    // ========== prompts ==========

    @Override
    public void beforePromptGet(McpCallContext context) {
        String promptName = requestParams(context) instanceof McpGetPromptParams params ? params.getName() : null;
        startClientSpan(context, promptName, false, builder -> {
            if (promptName != null) {
                builder.setAttribute(GEN_AI_PROMPT_NAME, promptName);
            }
        });
    }

    @Override
    public void afterPromptGet(McpCallContext context, McpGetPromptResult result, Map<String, Object> rawResult) {
        endSpan(context, null);
    }

    @Override
    public void onPromptGetError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    @Override
    public void beforePromptsList(McpCallContext context) {
        startListSpan(context);
    }

    @Override
    public void afterPromptsList(McpCallContext context, List<McpPrompt> prompts) {
        endSpan(context, null);
    }

    @Override
    public void onPromptsListError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    // ========== ping (with the 2026-07-28 protocol, the client checks health with server/discover) ==========

    @Override
    public void beforePing(McpCallContext context) {
        startClientSpan(context, null, false, null);
    }

    @Override
    public void afterPing(McpCallContext context) {
        endSpan(context, null);
    }

    @Override
    public void onPingError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    // onRootsListChanged: the notification gets its span when its _meta is supplied, see apply()

    // ========== server-initiated notifications and requests ==========

    @Override
    public void onNotificationToolsListChanged() {
        recordServerSpan("notifications/tools/list_changed", null, null);
    }

    @Override
    public void onNotificationResourcesListChanged() {
        recordServerSpan("notifications/resources/list_changed", null, null);
    }

    @Override
    public void onNotificationPromptsListChanged() {
        recordServerSpan("notifications/prompts/list_changed", null, null);
    }

    @Override
    public void onNotificationResourceUpdated(String uri) {
        recordServerSpan("notifications/resources/updated", null, builder -> setResourceUri(builder, uri));
    }

    @Override
    public void onNotificationMessage(McpLogMessage message) {
        recordServerSpan("notifications/message", null, null);
    }

    @Override
    public void onNotificationProgress(McpProgressNotification notification) {
        // the client uses the ID of the request as its progress token
        recordServerSpan("notifications/progress", findActiveSpanByRequestId(notification.progressToken()), null);
    }

    @Override
    public void onNotificationCancelled(long requestId, String reason) {
        recordServerSpan("notifications/cancelled", findActiveSpanByRequestId(String.valueOf(requestId)), null);
    }

    @Override
    public void onServerPing() {
        recordServerSpan("ping", null, null);
    }

    @Override
    public void onServerRootsList() {
        recordServerSpan("roots/list", null, null);
    }

    // ========== trace context propagation ==========

    /**
     * Supplies the {@code _meta} entries carrying the trace context of the span that the
     * outgoing message belongs to. Called by the MCP client right before it sends a message.
     */
    @Override
    public Map<String, Object> apply(McpCallContext callContext) {
        Context context;
        McpClientMessage message = callContext != null ? callContext.message() : null;
        if (message instanceof McpClientNotification notification) {
            // the listener is not told about notifications before they are sent,
            // so this is the only chance to create their span in time to propagate it
            context = recordNotificationSpan(notification);
        } else {
            ActiveSpan activeSpan = callContext != null ? activeSpans.get(callContext) : null;
            if (activeSpan == null && message != null) {
                activeSpan = spanStartedByCurrentThread(message.method);
            }
            if (activeSpan != null) {
                onMessageSent(activeSpan, message);
                context = activeSpan.context;
            } else {
                context = Context.current();
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        propagator.inject(context, meta, (carrier, key, value) -> carrier.put(key, value));
        return meta;
    }

    private void onMessageSent(ActiveSpan activeSpan, McpClientMessage message) {
        if (message == null) {
            return;
        }
        if (activeSpan.requestId == null && message instanceof McpClientRequest && message.getId() != null) {
            activeSpan.requestId = String.valueOf(message.getId());
            activeSpan.span.setAttribute(JSONRPC_REQUEST_ID, activeSpan.requestId);
        }
        String messageProtocolVersion = protocolVersionFromMeta(message);
        if (messageProtocolVersion != null) {
            activeSpan.protocolVersion = messageProtocolVersion;
            protocolVersion = messageProtocolVersion;
        }
    }

    private Context recordNotificationSpan(McpClientNotification notification) {
        // a cancellation belongs to the span of the request it cancels
        ActiveSpan parent = notification.getParams() instanceof McpCancellationParams params
                ? findActiveSpanByRequestId(params.getRequestId() != null ? String.valueOf(params.getRequestId()) : null)
                : null;
        Context parentContext = parent != null ? parent.context : Context.current();
        String method = methodName(notification.method);
        String messageProtocolVersion = protocolVersionFromMeta(notification);
        if (messageProtocolVersion != null) {
            protocolVersion = messageProtocolVersion;
        }
        SpanBuilder builder = tracer.spanBuilder(method)
                .setSpanKind(SpanKind.CLIENT)
                .setParent(parentContext);
        setCommonAttributes(builder, method);
        Span span = builder.startSpan();
        setEndAttributes(span, messageProtocolVersion);
        span.end();
        return parentContext.with(span);
    }

    // ========== helpers ==========

    private void startListSpan(McpCallContext context) {
        // The request in the context of list operations is never sent, the client sends a separate request
        // for every page instead. The ID of the request for the first page is recorded when it's sent.
        startClientSpan(context, null, true, null);
    }

    /**
     * @param requestNotSent whether the message in the context is never sent, so its ID must not be recorded
     */
    private ActiveSpan startClientSpan(McpCallContext context, String target, boolean requestNotSent,
            Consumer<SpanBuilder> customizer) {
        McpClientMessage message = context.message();
        String method = methodName(message.method);
        Context parentContext = Context.current();
        SpanBuilder builder = tracer.spanBuilder(target != null ? method + " " + target : method)
                .setSpanKind(SpanKind.CLIENT)
                .setParent(parentContext);
        setCommonAttributes(builder, method);
        String requestId = !requestNotSent && message.getId() != null ? String.valueOf(message.getId()) : null;
        if (requestId != null) {
            builder.setAttribute(JSONRPC_REQUEST_ID, requestId);
        }
        if (customizer != null) {
            customizer.accept(builder);
        }
        Span span = builder.startSpan();
        ActiveSpan activeSpan = new ActiveSpan(span, parentContext.with(span), message.method);
        activeSpan.requestId = requestId;
        activeSpans.put(context, activeSpan);
        Deque<ActiveSpan> threadSpans = spansStartedByThread.get();
        removeEndedSpans(threadSpans);
        threadSpans.push(activeSpan);
        return activeSpan;
    }

    private void recordServerSpan(String method, ActiveSpan parent, Consumer<SpanBuilder> customizer) {
        SpanBuilder builder = tracer.spanBuilder(method).setSpanKind(SpanKind.SERVER);
        if (parent != null) {
            builder.setParent(parent.context);
        }
        setCommonAttributes(builder, method);
        if (customizer != null) {
            customizer.accept(builder);
        }
        Span span = builder.startSpan();
        setEndAttributes(span, null);
        span.end();
    }

    private void setCommonAttributes(SpanBuilder builder, String method) {
        builder.setAttribute(MCP_METHOD_NAME, method);
        builder.setAttribute(NETWORK_TRANSPORT, networkTransport);
        if (networkProtocolName != null) {
            builder.setAttribute(NETWORK_PROTOCOL_NAME, networkProtocolName);
        }
        if (serverAddress != null) {
            builder.setAttribute(SERVER_ADDRESS, serverAddress);
            builder.setAttribute(SERVER_PORT, serverPort);
        }
    }

    private void setEndAttributes(Span span, String spanProtocolVersion) {
        String version = spanProtocolVersion != null ? spanProtocolVersion : protocolVersion;
        if (version != null) {
            span.setAttribute(MCP_PROTOCOL_VERSION, version);
        }
        String sessionId = sessionIdSupplier != null ? sessionIdSupplier.get() : null;
        if (sessionId != null) {
            span.setAttribute(MCP_SESSION_ID, sessionId);
        }
    }

    private static void setResourceUri(SpanBuilder builder, String uri) {
        if (uri != null) {
            builder.setAttribute(MCP_RESOURCE_URI, uri);
        }
    }

    private void endSpan(McpCallContext context, Throwable error) {
        endSpan(context, error, null);
    }

    private void endSpan(McpCallContext context, Throwable error, String errorType) {
        ActiveSpan activeSpan = activeSpans.remove(context);
        if (activeSpan == null) {
            log.warn("Unknown MCP call context: " + context.message().getId());
            return;
        }
        activeSpan.ended = true;
        // only finds the span if the operation ends on the thread that started it,
        // otherwise it's removed from the stack of that thread when it starts the next operation
        spansStartedByThread.get().remove(activeSpan);
        Span span = activeSpan.span;
        try {
            setEndAttributes(span, activeSpan.protocolVersion);
            if (error != null) {
                recordError(span, error);
            } else if (errorType != null) {
                span.setAttribute(ERROR_TYPE, errorType);
                span.setStatus(StatusCode.ERROR);
            }
        } finally {
            span.end();
        }
    }

    private static void recordError(Span span, Throwable error) {
        Throwable cause = unwrap(error);
        Integer errorCode = null;
        String errorMessage = cause.getMessage();
        if (cause instanceof McpException mcpException) {
            errorCode = mcpException.errorCode();
            errorMessage = mcpException.errorMessage();
        } else if (cause instanceof ToolExecutionException toolExecutionException
                && toolExecutionException.errorCode() != null) {
            errorCode = toolExecutionException.errorCode();
            if (toolExecutionException.getCause() != null) {
                errorMessage = toolExecutionException.getCause().getMessage();
            }
        }
        if (errorCode != null) {
            // all JSON-RPC error codes are considered errors on the client side
            span.setAttribute(RPC_RESPONSE_STATUS_CODE, String.valueOf(errorCode));
            span.setAttribute(ERROR_TYPE, String.valueOf(errorCode));
        } else {
            span.setAttribute(ERROR_TYPE, cause.getClass().getName());
        }
        if (errorMessage != null) {
            span.setStatus(StatusCode.ERROR, errorMessage);
        } else {
            span.setStatus(StatusCode.ERROR);
        }
        span.recordException(error);
    }

    /**
     * Strips the exceptions that the MCP client wraps the actual error in.
     */
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current && isWrapper(current)) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isWrapper(Throwable error) {
        return error instanceof ExecutionException
                || error instanceof CompletionException
                || error.getClass() == RuntimeException.class
                || (error.getClass() == ToolExecutionException.class
                        && ((ToolExecutionException) error).errorCode() == null);
    }

    private ActiveSpan spanStartedByCurrentThread(McpClientMethod method) {
        Deque<ActiveSpan> threadSpans = spansStartedByThread.get();
        removeEndedSpans(threadSpans);
        for (ActiveSpan activeSpan : threadSpans) {
            if (activeSpan.method == method && !activeSpan.ended) {
                return activeSpan;
            }
        }
        return null;
    }

    private static void removeEndedSpans(Deque<ActiveSpan> threadSpans) {
        while (!threadSpans.isEmpty() && threadSpans.peek().ended) {
            threadSpans.pop();
        }
    }

    private ActiveSpan findActiveSpanByRequestId(String requestId) {
        if (requestId == null) {
            return null;
        }
        List<ActiveSpan> spans;
        synchronized (activeSpans) {
            spans = new ArrayList<>(activeSpans.values());
        }
        for (ActiveSpan activeSpan : spans) {
            if (requestId.equals(activeSpan.requestId)) {
                return activeSpan;
            }
        }
        return null;
    }

    private static McpClientParams requestParams(McpCallContext context) {
        return context.message() instanceof McpClientRequest request ? request.getParams() : null;
    }

    private static String protocolVersionFromMeta(McpClientMessage message) {
        McpClientParams params = null;
        if (message instanceof McpClientRequest request) {
            params = request.getParams();
        } else if (message instanceof McpClientNotification notification) {
            params = notification.getParams();
        }
        Map<String, Object> meta = params != null ? params.getMeta() : null;
        Object version = meta != null ? meta.get(PROTOCOL_VERSION_META_KEY) : null;
        return version != null ? version.toString() : null;
    }

    static String methodName(McpClientMethod method) {
        if (method == null) {
            return "unknown";
        }
        return switch (method) {
            case INITIALIZE -> "initialize";
            case TOOLS_CALL -> "tools/call";
            case TOOLS_LIST -> "tools/list";
            case NOTIFICATION_CANCELLED -> "notifications/cancelled";
            case NOTIFICATION_INITIALIZED -> "notifications/initialized";
            case PING -> "ping";
            case RESOURCES_LIST -> "resources/list";
            case RESOURCES_READ -> "resources/read";
            case RESOURCES_TEMPLATES_LIST -> "resources/templates/list";
            case PROMPTS_LIST -> "prompts/list";
            case PROMPTS_GET -> "prompts/get";
            case NOTIFICATION_ROOTS_LIST_CHANGED -> "notifications/roots/list_changed";
            case RESOURCES_SUBSCRIBE -> "resources/subscribe";
            case RESOURCES_UNSUBSCRIBE -> "resources/unsubscribe";
            case SERVER_DISCOVER -> "server/discover";
            case SUBSCRIPTIONS_LISTEN -> "subscriptions/listen";
            default -> method.name().toLowerCase(Locale.ROOT);
        };
    }
}
