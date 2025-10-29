package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AfterAgentInvocation;
import dev.langchain4j.agentic.declarative.BeforeAgentInvocation;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.HumanInTheLoopResponseSupplier;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.RetrievalAugmentorSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;

public final class AgenticLangChain4jDotNames {

    public static final DotName AGENT = DotName.createSimple(Agent.class.getName());
    public static final DotName SUB_AGENT = DotName.createSimple(SubAgent.class.getName());
    public static final DotName SUPERVISOR_AGENT = DotName.createSimple(SupervisorAgent.class.getName());
    public static final DotName SEQUENCE_AGENT = DotName.createSimple(SequenceAgent.class.getName());
    public static final DotName PARALLEL_AGENT = DotName.createSimple(ParallelAgent.class.getName());
    public static final DotName LOOP_AGENT = DotName.createSimple(LoopAgent.class.getName());
    public static final DotName CONDITIONAL_AGENT = DotName.createSimple(ConditionalAgent.class.getName());

    public static final List<DotName> ALL_AGENT_ANNOTATIONS = List.of(AGENT, SUB_AGENT, SUPERVISOR_AGENT, SEQUENCE_AGENT,
            PARALLEL_AGENT, LOOP_AGENT, CONDITIONAL_AGENT);
    public static final List<DotName> AGENT_ANNOTATIONS_WITH_SUB_AGENTS = List.of(SUPERVISOR_AGENT, SEQUENCE_AGENT,
            PARALLEL_AGENT, LOOP_AGENT, CONDITIONAL_AGENT);

    public static final DotName CHAT_MODEL_SUPPLIER = DotName.createSimple(ChatModelSupplier.class.getName());
    public static final DotName AGENTIC_SCOPE = DotName.createSimple(AgenticScope.class);
    public static final DotName RESULT_WITH_AGENTIC_SCOPE = DotName.createSimple(ResultWithAgenticScope.class);

    public static final DotName ACTIVATION_CONDITION = DotName.createSimple(ActivationCondition.class.getName());
    public static final DotName BEFORE_AGENT_INVOCATION = DotName.createSimple(BeforeAgentInvocation.class.getName());
    public static final DotName AFTER_AGENT_INVOCATION = DotName.createSimple(AfterAgentInvocation.class.getName());
    public static final DotName AGENT_REQUEST = DotName.createSimple(AgentRequest.class.getName());
    public static final DotName AGENT_RESPONSE = DotName.createSimple(AgentResponse.class.getName());
    public static final DotName CHAT_MEMORY_PROVIDER_SUPPLIER = DotName
            .createSimple(ChatMemoryProviderSupplier.class.getName());
    public static final DotName CHAT_MEMORY_SUPPLIER = DotName
            .createSimple(ChatMemorySupplier.class.getName());
    public static final DotName CONTENT_RETRIEVER_SUPPLIER = DotName
            .createSimple(ContentRetrieverSupplier.class.getName());
    public static final DotName ERROR_HANDLER = DotName
            .createSimple(ErrorHandler.class.getName());
    public static final DotName ERROR_CONTEXT = DotName
            .createSimple(ErrorContext.class.getName());
    public static final DotName ERROR_RECOVERY_RESULT = DotName
            .createSimple(ErrorRecoveryResult.class.getName());
    public static final DotName EXIT_CONDITION = DotName
            .createSimple(ExitCondition.class.getName());
    public static final DotName HUMAN_IN_THE_LOOP = DotName
            .createSimple(HumanInTheLoop.class.getName());
    public static final DotName HUMAN_IN_THE_LOOP_RESPONSE_SUPPLIER = DotName
            .createSimple(HumanInTheLoopResponseSupplier.class.getName());
    public static final DotName OUTPUT = DotName
            .createSimple(Output.class.getName());
    public static final DotName PARALLEL_EXECUTOR = DotName
            .createSimple(ParallelExecutor.class.getName());
    public static final DotName RETRIEVAL_AUGMENTER_SUPPLIER = DotName
            .createSimple(RetrievalAugmentorSupplier.class.getName());
    public static final DotName TOOL_PROVIDER_SUPPLIER = DotName
            .createSimple(ToolProviderSupplier.class.getName());
    public static final DotName TOOL_SUPPLIER = DotName
            .createSimple(ToolsSupplier.class.getName());

    private AgenticLangChain4jDotNames() {
    }
}
