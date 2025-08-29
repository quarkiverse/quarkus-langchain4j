package io.quarkiverse.langchain4j.agentic.deployment;

import org.jboss.jandex.DotName;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SubAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;

public final class AgenticLangChain4jDotNames {

    public static final DotName AGENT = DotName.createSimple(Agent.class.getName());
    public static final DotName SUB_AGENT = DotName.createSimple(SubAgent.class.getName());
    public static final DotName SUPERVISOR_AGENT = DotName.createSimple(SupervisorAgent.class.getName());
    public static final DotName SEQUENCE_AGENT = DotName.createSimple(SequenceAgent.class.getName());
    public static final DotName PARALLEL_AGENT = DotName.createSimple(ParallelAgent.class.getName());
    public static final DotName LOOP_AGENT = DotName.createSimple(LoopAgent.class.getName());
    public static final DotName CONDITIONAL_AGENT = DotName.createSimple(ConditionalAgent.class.getName());
    //    public static final DotName REGISTER_AGENT = DotName.createSimple(RegisterAgent.class.getName());

    private AgenticLangChain4jDotNames() {
    }
}
