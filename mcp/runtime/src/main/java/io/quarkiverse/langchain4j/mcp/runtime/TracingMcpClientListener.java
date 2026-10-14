package io.quarkiverse.langchain4j.mcp.runtime;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_PROMPT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_TOOL_NAME;
import static io.opentelemetry.semconv.incubating.JsonrpcIncubatingAttributes.JSONRPC_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_RESOURCE_URI;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_SESSION_ID;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.ErrorAttributes.ErrorTypeValues;
import io.opentelemetry.semconv.NetworkAttributes.NetworkTransportValues;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues;
import io.opentelemetry.semconv.incubating.McpIncubatingAttributes.McpMethodNameIncubatingValues;
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

    private static final int MAX_SUBSCRIPTION_CONTEXTS = 1000;

    /**
     * The longest time between two checks for abandoned spans.
     */
    private static final long MAX_ABANDONED_SPANS_CHECK_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

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
     * Spans of the operations in progress, keyed by the JSON-RPC ID of the message in the context the
     * MCP client reports them with. The ID is unique per client, even for the list operations, whose
     * message is never sent.
     */
    private final Map<Long, ActiveSpan> activeSpans = new ConcurrentHashMap<>();

    /**
     * Contexts of the spans of the subscriptions/listen requests of the active resource subscriptions, keyed by
     * the subscription ID (the ID of the request). The span ends when the server acknowledges the subscription,
     * but the notifications/cancelled notification that ends the subscription still belongs to it.
     * <p>
     * A subscription that ends without the client unsubscribing (because the server closes it, or the client is
     * closed) is never removed, so the number of entries is limited, and the oldest ones are dropped.
     */
    private final Map<String, Context> subscriptionContexts = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Context> eldest) {
                    return size() > MAX_SUBSCRIPTION_CONTEXTS;
                }
            });

    /**
     * Spans of the operations started by the current thread. Some operations send more than one
     * message (list operations fetch every page with a separate request, and multi round-trip
     * requests are retried with a new request), each with its own context that the listener is not
     * told about. These messages are sent by the thread that started the operation, so they are
     * matched with the most recent span of this thread that has the same method.
     */
    private final ThreadLocal<Deque<ActiveSpan>> spansStartedByThread = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * How long a span may stay in progress before it's considered abandoned. The MCP client doesn't report the
     * end of an operation in some error cases, and such spans would otherwise never end.
     */
    private final long maxSpanAgeNanos;

    /**
     * When to next look for abandoned spans, in {@link System#nanoTime()}.
     */
    private final AtomicLong nextAbandonedSpansCheck;

    /**
     * The protocol version the client speaks with the server, for the messages that don't carry it
     * themselves (all messages of the legacy protocol, which agrees on the version during initialization).
     */
    private volatile String protocolVersion;

    private static final class ActiveSpan {
        final Span span;
        final Context context;
        final McpClientMethod method;
        /**
         * The ID of the first request sent for the operation, recorded as the {@code jsonrpc.request.id} attribute.
         */
        volatile String requestId;
        /**
         * The IDs of all requests sent for the operation (for every page of a list operation, and every retry of a
         * multi round-trip request), so that a cancellation of any of them is attached to this span.
         */
        final Set<String> sentRequestIds = ConcurrentHashMap.newKeySet();
        volatile String protocolVersion;
        volatile boolean ended;
        final long startNanos = System.nanoTime();

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
     * @param maxSpanAge how long a span may stay in progress before it's ended as abandoned
     */
    public TracingMcpClientListener(McpTransportType transportType, String url, Supplier<String> sessionIdSupplier,
            Duration maxSpanAge) {
        this(Arc.container().select(Tracer.class).get(),
                Arc.container().select(OpenTelemetry.class).get().getPropagators().getTextMapPropagator(),
                transportType, url, sessionIdSupplier, maxSpanAge);
    }

    public TracingMcpClientListener(Tracer tracer, TextMapPropagator propagator, McpTransportType transportType,
            String url, Supplier<String> sessionIdSupplier, Duration maxSpanAge) {
        this.tracer = tracer;
        this.maxSpanAgeNanos = maxSpanAge.toNanos();
        this.nextAbandonedSpansCheck = new AtomicLong(System.nanoTime() + abandonedSpansCheckIntervalNanos());
        this.propagator = propagator;
        this.sessionIdSupplier = sessionIdSupplier;
        if (transportType == McpTransportType.STDIO) {
            this.networkTransport = NetworkTransportValues.PIPE;
            this.networkProtocolName = null;
        } else {
            this.networkTransport = NetworkTransportValues.TCP;
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

    private long abandonedSpansCheckIntervalNanos() {
        return Math.min(maxSpanAgeNanos, MAX_ABANDONED_SPANS_CHECK_INTERVAL_NANOS);
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
            builder.setAttribute(GEN_AI_OPERATION_NAME, GenAiOperationNameIncubatingValues.EXECUTE_TOOL);
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
        ActiveSpan activeSpan = activeSpans.get(context.message().getId());
        if (activeSpan != null) {
            subscriptionContexts.put(String.valueOf(subscriptionId), activeSpan.context);
        }
        endSpan(context, null);
    }

    @Override
    public void onResourcesSubscribeError(McpCallContext context, Throwable error) {
        endSpan(context, error);
    }

    // beforeResourcesUnsubscribe: unsubscribing sends a notifications/cancelled notification (or no message at all,
    // if the transport closes the stream instead), which gets its span when its _meta is supplied, see apply()

    @Override
    public void afterResourcesUnsubscribe(McpCallContext context, long subscriptionId) {
        subscriptionContexts.remove(String.valueOf(subscriptionId));
    }

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

    // beforePing/afterPing/onPingError: health checks (ping, or server/discover with the 2026-07-28 protocol,
    // which the client also reports as a ping) are not traced. They are sent periodically and would produce
    // a large number of spans of little value.

    // onRootsListChanged: the notification gets its span when its _meta is supplied, see apply()

    // ========== server-initiated notifications and requests ==========

    @Override
    public void onNotificationToolsListChanged() {
        recordServerSpan(McpMethodNameIncubatingValues.NOTIFICATIONS_TOOLS_LIST_CHANGED, null, null);
    }

    @Override
    public void onNotificationResourcesListChanged() {
        recordServerSpan(McpMethodNameIncubatingValues.NOTIFICATIONS_RESOURCES_LIST_CHANGED, null, null);
    }

    @Override
    public void onNotificationPromptsListChanged() {
        recordServerSpan(McpMethodNameIncubatingValues.NOTIFICATIONS_PROMPTS_LIST_CHANGED, null, null);
    }

    @Override
    public void onNotificationResourceUpdated(String uri) {
        recordServerSpan(McpMethodNameIncubatingValues.NOTIFICATIONS_RESOURCES_UPDATED, null,
                builder -> setResourceUri(builder, uri));
    }

    @Override
    public void onNotificationMessage(McpLogMessage message) {
        recordServerSpan(McpMethodNameIncubatingValues.NOTIFICATIONS_MESSAGE, null, null);
    }

    @Override
    public void onNotificationProgress(McpProgressNotification notification) {
        // the client uses the ID of the request as its progress token
        recordServerSpan(McpMethodNameIncubatingValues.NOTIFICATIONS_PROGRESS,
                findActiveSpanByRequestId(notification.progressToken()), null);
    }

    @Override
    public void onNotificationCancelled(long requestId, String reason) {
        recordServerSpan(McpMethodNameIncubatingValues.NOTIFICATIONS_CANCELLED,
                findActiveSpanByRequestId(String.valueOf(requestId)), null);
    }

    @Override
    public void onServerPing() {
        recordServerSpan(McpMethodNameIncubatingValues.PING, null, null);
    }

    @Override
    public void onServerRootsList() {
        recordServerSpan(McpMethodNameIncubatingValues.ROOTS_LIST, null, null);
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
            ActiveSpan activeSpan = message != null && message.getId() != null ? activeSpans.get(message.getId()) : null;
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
        if (message instanceof McpClientRequest && message.getId() != null) {
            String id = String.valueOf(message.getId());
            activeSpan.sentRequestIds.add(id);
            if (activeSpan.requestId == null) {
                activeSpan.requestId = id;
                activeSpan.span.setAttribute(JSONRPC_REQUEST_ID, id);
            }
        }
        String messageProtocolVersion = protocolVersionFromMeta(message);
        if (messageProtocolVersion != null) {
            activeSpan.protocolVersion = messageProtocolVersion;
            protocolVersion = messageProtocolVersion;
        }
    }

    private Context recordNotificationSpan(McpClientNotification notification) {
        // a cancellation belongs to the span of the request it cancels
        Context parentContext = Context.current();
        if (notification.getParams() instanceof McpCancellationParams params && params.getRequestId() != null) {
            String cancelledRequestId = String.valueOf(params.getRequestId());
            ActiveSpan cancelledRequest = findActiveSpanByRequestId(cancelledRequestId);
            Context subscriptionContext = subscriptionContexts.get(cancelledRequestId);
            if (cancelledRequest != null) {
                parentContext = cancelledRequest.context;
            } else if (subscriptionContext != null) {
                parentContext = subscriptionContext;
            }
        }
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
        endAbandonedSpans();
        ActiveSpan activeSpan = new ActiveSpan(span, parentContext.with(span), message.method);
        activeSpan.requestId = requestId;
        if (requestId != null) {
            activeSpan.sentRequestIds.add(requestId);
        }
        if (message.getId() != null) {
            activeSpans.put(message.getId(), activeSpan);
        }
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
        Long id = context.message().getId();
        ActiveSpan activeSpan = id != null ? activeSpans.remove(id) : null;
        if (activeSpan == null) {
            // the span was ended as abandoned, see endAbandonedSpans()
            log.debugf("No span in progress for the MCP request %s", id);
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
        // spans can end on other threads, so they may be anywhere in the stack
        threadSpans.removeIf(activeSpan -> activeSpan.ended);
    }

    /**
     * Ends the spans that have been in progress for longer than the maximum age. The MCP client doesn't report
     * the end of an operation in some error cases (for example, when the thread is interrupted), and such spans
     * would otherwise never end. Checks at most once per check interval.
     */
    private void endAbandonedSpans() {
        long now = System.nanoTime();
        long nextCheck = nextAbandonedSpansCheck.get();
        if (now - nextCheck < 0
                || !nextAbandonedSpansCheck.compareAndSet(nextCheck, now + abandonedSpansCheckIntervalNanos())) {
            return;
        }
        for (Map.Entry<Long, ActiveSpan> entry : activeSpans.entrySet()) {
            ActiveSpan activeSpan = entry.getValue();
            // removing the entry atomically makes sure the span is ended only once, even if the operation ends now
            if (now - activeSpan.startNanos > maxSpanAgeNanos && activeSpans.remove(entry.getKey(), activeSpan)) {
                activeSpan.ended = true;
                Span span = activeSpan.span;
                setEndAttributes(span, activeSpan.protocolVersion);
                span.setAttribute(ERROR_TYPE, ErrorTypeValues.OTHER);
                span.setStatus(StatusCode.ERROR, "The MCP client did not report the end of the operation");
                span.end();
            }
        }
    }

    private ActiveSpan findActiveSpanByRequestId(String requestId) {
        if (requestId == null) {
            return null;
        }
        for (ActiveSpan activeSpan : activeSpans.values()) {
            if (activeSpan.sentRequestIds.contains(requestId)) {
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
            case INITIALIZE -> McpMethodNameIncubatingValues.INITIALIZE;
            case TOOLS_CALL -> McpMethodNameIncubatingValues.TOOLS_CALL;
            case TOOLS_LIST -> McpMethodNameIncubatingValues.TOOLS_LIST;
            case NOTIFICATION_CANCELLED -> McpMethodNameIncubatingValues.NOTIFICATIONS_CANCELLED;
            case NOTIFICATION_INITIALIZED -> McpMethodNameIncubatingValues.NOTIFICATIONS_INITIALIZED;
            case PING -> McpMethodNameIncubatingValues.PING;
            case RESOURCES_LIST -> McpMethodNameIncubatingValues.RESOURCES_LIST;
            case RESOURCES_READ -> McpMethodNameIncubatingValues.RESOURCES_READ;
            case RESOURCES_TEMPLATES_LIST -> McpMethodNameIncubatingValues.RESOURCES_TEMPLATES_LIST;
            case PROMPTS_LIST -> McpMethodNameIncubatingValues.PROMPTS_LIST;
            case PROMPTS_GET -> McpMethodNameIncubatingValues.PROMPTS_GET;
            case NOTIFICATION_ROOTS_LIST_CHANGED -> McpMethodNameIncubatingValues.NOTIFICATIONS_ROOTS_LIST_CHANGED;
            case RESOURCES_SUBSCRIBE -> McpMethodNameIncubatingValues.RESOURCES_SUBSCRIBE;
            case RESOURCES_UNSUBSCRIBE -> McpMethodNameIncubatingValues.RESOURCES_UNSUBSCRIBE;
            // methods of the 2026-07-28 protocol, which the semantic conventions don't define yet
            case SERVER_DISCOVER -> "server/discover";
            case SUBSCRIPTIONS_LISTEN -> "subscriptions/listen";
            default -> method.name().toLowerCase(Locale.ROOT);
        };
    }
}
