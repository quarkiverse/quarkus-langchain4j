# Apicurio Registry + Quarkus LangChain4j Integration Proposal

**Date:** 2026-04-14
**Status:** Draft

## Overview

This document explores integration opportunities between **Apicurio Registry** and **Quarkus LangChain4j** — the Quarkiverse extension that brings LLM capabilities into Quarkus applications. The goal is to position Apicurio Registry as the governance and discovery backbone for AI-powered Java microservices.

Quarkus LangChain4j provides declarative AI services (`@RegisterAiService`), function calling (`@Tool`), RAG pipelines, guardrails, and Model Context Protocol (MCP) support. Apicurio Registry provides versioned artifact storage, schema validation, compatibility enforcement, and A2A agent discovery. The two projects complement each other naturally.

---

## 1. MCP Tool Discovery via Apicurio Registry

### Context

Quarkus LangChain4j includes an MCP client that connects to external MCP servers to dynamically discover and invoke tools at runtime. Today, each MCP server is manually configured in `application.properties`. There is no way to dynamically discover which MCP servers and tools are available across an organization.

### Proposal

Use Apicurio Registry as a **centralized catalog of MCP tool definitions**. The registry stores and governs tool definitions (name, description, parameter schema, which MCP server hosts them) as versioned artifacts. A registry-aware client layer in Quarkus LangChain4j queries the registry to discover available tools and their hosting servers, then connects to those servers directly for execution.

The registry does not execute tools or act as a proxy — it stays true to its role as an artifact store with governance on top. Execution remains with the actual MCP servers.

### How It Works

1. Developers register MCP tool definitions as `MCP_TOOL` artifacts in Apicurio Registry (name, description, parameter schema, hosting MCP server URL).
2. The registry validates tool definitions against the MCP spec, enforces compatibility rules across versions, and enables search/discovery.
3. A Quarkus LangChain4j client-side integration queries the registry at runtime to discover available tools and which MCP servers host them.
4. The client automatically connects to the actual MCP servers for tool execution — no manual per-server configuration needed.

### Value

- **Dynamic tool discovery** — no redeployment or config changes needed when tools are added or updated.
- **Schema governance** — the registry's compatibility rules prevent breaking changes to tool parameters.
- **Centralized catalog** — all MCP tools across the organization are discoverable in one place.
- **Versioning** — full version history of tool schema changes with compatibility checking.
- **Client-side simplification** — instead of manually configuring each MCP server, the AI service discovers them through the registry.

---

## 2. A2A Agent Card Discovery

### Context

Apicurio Registry already supports the **A2A (Agent-to-Agent) protocol** with the `AGENT_CARD` artifact type and `/.well-known/agent.json` endpoints. Agent cards describe an AI agent's capabilities, skills, supported input/output modes, and contact information. Quarkus LangChain4j supports agent patterns and multi-turn tool invocation but lacks a standardized agent discovery mechanism.

### Proposal

Use Apicurio Registry as the **agent discovery layer** for Quarkus LangChain4j applications.

### How It Works

1. AI services built with Quarkus LangChain4j **publish** their agent cards to the registry on startup (via a Quarkus extension or startup observer).
2. An orchestrator agent **discovers** peer agents by querying the registry's agent search API, filtering by skills, capabilities, or input/output modes.
3. The orchestrator delegates tasks to discovered agents using the contact information from the agent card.

### Value

- **Multi-agent orchestration** — agents find and delegate to each other through a central registry.
- **Standardized discovery** — follows the A2A protocol, ensuring interoperability beyond the Java ecosystem.
- **Governance** — agent card versioning and validation ensure agents advertise accurate capabilities.
- **Already built** — the registry-side implementation exists; the integration is primarily on the LangChain4j side.

---

## 3. Prompt Template Management

### Context

Quarkus LangChain4j uses `@SystemMessage` and `@UserMessage` annotations with template strings that are compiled into the application. Prompt engineering is inherently iterative — prompts change far more frequently than application code. Apicurio Registry already supports prompt template rendering via its `/render` endpoint (used in the support-chat module).

### Proposal

Store and version **prompt templates** in Apicurio Registry, and provide a mechanism for Quarkus LangChain4j AI services to resolve prompts from the registry at runtime.

### How It Works

1. Prompt templates are stored as versioned artifacts in the registry (e.g., artifact type `PROMPT_TEMPLATE` or plain text/JSON).
2. A custom `SystemMessageProvider` / `UserMessageProvider` (or equivalent CDI bean) resolves the prompt from the registry by artifact ID, with optional caching.
3. Prompts can be updated in the registry without redeploying the application.

```java
@RegisterAiService
public interface SupportAgent {
    @SystemMessage(fromRegistry = "support-agent-system-prompt")
    @UserMessage(fromRegistry = "support-agent-user-prompt")
    String answer(String question);
}
```

### Value

- **Decouples prompt lifecycle from code lifecycle** — prompts can be updated, rolled back, and A/B tested independently.
- **Versioning and compatibility** — track prompt evolution; use compatibility rules to catch regressions.
- **Centralized governance** — all prompts across the organization are discoverable and auditable.
- **Reuse** — share prompt templates across multiple AI services.

---

## 4. Structured Output Schema Guardrails

### Context

Quarkus LangChain4j auto-generates `{response_schema}` from Java return types and supports guardrails for validating LLM output. However, output validation is currently limited to type-level checks. Apicurio Registry's validity rules provide richer schema validation.

### Proposal

Implement a **registry-backed output guardrail** that validates LLM responses against schemas stored in Apicurio Registry.

### How It Works

1. Expected output schemas are stored as JSON Schema artifacts in the registry.
2. A custom guardrail bean (`RegistryOutputGuardrail`) fetches the schema from the registry and validates the LLM response against it.
3. If validation fails, the guardrail triggers a retry (using LangChain4j's retry mechanism) with a corrective prompt.

```java
@RegisterAiService
public interface DataExtractor {
    @UserMessage("Extract invoice data from: {text}")
    @OutputGuardrails(RegistrySchemaGuardrail.class)
    InvoiceData extract(String text);
}
```

```java
@ApplicationScoped
public class RegistrySchemaGuardrail implements OutputGuardrail {
    @Inject RegistryClient registryClient;

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams params) {
        JsonSchema schema = registryClient.getLatestArtifact("ai-schemas", "invoice-output");
        // Validate params.responseText() against schema
    }
}
```

### Value

- **Richer validation** — JSON Schema validation catches structural issues that Java type checks miss.
- **Decoupled schema evolution** — output schemas can be tightened or relaxed without redeployment.
- **Shared contracts** — producers and consumers of AI-generated data agree on the schema via the registry.

---

## 5. RAG Document Metadata Catalog

### Context

Quarkus LangChain4j's RAG pipeline ingests documents, chunks them, and stores embeddings in vector stores. There is no built-in mechanism to track document provenance, versioning, or ingestion status.

### Proposal

Use Apicurio Registry as a **document metadata catalog** for RAG pipelines.

### How It Works

1. Source documents (or their metadata) are registered as artifacts in Apicurio Registry.
2. Metadata labels track ingestion status: which embedding store, when last ingested, chunk count, embedding model used.
3. When a document artifact is updated in the registry, a webhook or event triggers re-ingestion into the RAG pipeline.
4. For structured documents, the registry stores and validates the expected document schema before chunking.

### Value

- **Lineage tracking** — know exactly which document versions are in which embedding stores.
- **Automated re-ingestion** — document updates automatically propagate to RAG pipelines.
- **Multi-pipeline governance** — multiple RAG pipelines share a consistent document catalog.

---

## 6. Quarkus Extension: `quarkus-langchain4j-apicurio-registry`

### Context

All the above integrations would benefit from a unified Quarkus extension that provides seamless configuration and CDI integration.

### Proposal

Build a **Quarkus extension** that bridges Apicurio Registry and Quarkus LangChain4j.

### Features

- **Auto-configuration** — connect AI services to a registry instance with minimal properties:
  ```properties
  quarkus.langchain4j.apicurio-registry.url=http://localhost:8080/apis/registry/v3
  ```
- **CDI Producers** — injectable beans for registry-backed `ToolProvider`, prompt resolvers, and guardrails.
- **Dev Services** — auto-start an Apicurio Registry instance during `quarkus:dev` for local development.
- **Dev UI panel** — browse registered AI artifacts (tools, prompts, agent cards, output schemas) from the Quarkus Dev UI.
- **Build-time validation** — verify that tool schemas and prompt templates referenced by AI services exist in the registry.
- **Agent card auto-publish** — automatically register the application's agent card on startup.

---

## Priority and Sequencing

| Phase | Integration | Effort | Impact |
|-------|------------|--------|--------|
| **Phase 1** | A2A agent card discovery | Low | High |
| **Phase 1** | Prompt template management | Medium | High |
| **Phase 2** | MCP tool discovery via registry | Medium | High |
| **Phase 2** | Output schema guardrails | Low | Medium |
| **Phase 3** | RAG document metadata catalog | Medium | Medium |
| **Phase 4** | Quarkus extension (`quarkus-langchain4j-apicurio-registry`) | High | High |

**Phase 1** focuses on capabilities where Apicurio Registry already has infrastructure (A2A, prompt rendering). **Phase 2** introduces client-side integration for dynamic tool discovery and output validation. **Phase 3** adds value-add features. **Phase 4** packages everything into a polished extension.

---

## Open Questions

1. **Prompt resolution caching** — what cache invalidation strategy? TTL, registry webhooks, or etag-based?
2. **LangChain4j upstream** — should integrations target the Quarkus extension layer or contribute to upstream LangChain4j?
3. **Authentication** — how to propagate OIDC tokens from AI services to the registry in multi-tenant setups?
4. **MCP tool discovery client** — what's the right abstraction for dynamic MCP server connection on the client side?
