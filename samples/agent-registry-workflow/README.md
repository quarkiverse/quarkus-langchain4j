# Agent discovery, invocation and contract governance

Run a complete **Apicurio Registry + Quarkus LangChain4j** workflow:

Create a multilingual weather briefing in **one connected request**:

1. Two A2A servers publish their Agent Cards through the A2A Registry extension.
2. The application checks its three required registered contracts are enabled and contain the required capabilities.
3. The MCP Registry extension discovers the weather endpoint, connects over Streamable HTTP and invokes `getWeather`.
4. The orchestrator discovers the summarizer and translator through `ApicurioAgentsRegistry`.
   It passes the real weather result plus the user's context to the summarizer, then passes the summary to the translator.
5. The dashboard displays the observed contract versions and each actual input/output handoff.
6. Try a breaking contract update, then deprecate and restore the translator: rejection preserves
   accepted content, while the application's lifecycle policy blocks new briefings for a deprecated contract.

Based on [agent-discovery-demo](https://github.com/carlesarnal/agent-discovery-demo) and the
existing [A2A discovery](../a2a-agent-discovery) and [MCP discovery](../apicurio-registry-mcp) samples.
This sample supplies the real specialist servers and performs invocation, not just routing metadata.

```text
                     ┌─ summarizer :11010 ──┐
                     │    publishes card    │
input → orchestrator :11020 ← Registry :8180 │
                     │    publishes card    │
                     └─ translator :11030 ──┘
                         summary → French

briefing request → contract checks → MCP weather → A2A summary → A2A French translation
```

The orchestrator knows application roles (`summarizer`, `translator`, `weather-tools/weather`),
not server URLs. Registry supplies the locations. The A2A sequence order is explicit;
the generated text is not deterministic. Weather data is a small, fictional fixture.

## Versions and prerequisites

| Component | Version used |
|---|---|
| Quarkus LangChain4j extensions | `999-SNAPSHOT`, built from this repository checkout |
| Quarkus | `3.40.0` |
| A2A Java SDK | `1.4.0.Final`, aligned for reference servers and client via its BOM |
| Quarkus MCP Server | `2.0.2` |
| Apicurio Registry container and Java SDK | `3.3.3` |
| Ollama model | `qwen2.5:7b` by default |

Use Java 17 or newer (the verification run used Java 25), Docker with Compose v2, Python 3.9+
and a running [Ollama](https://ollama.com/). Allow enough memory for the 7B model and four JVMs.
This uses the latest released Registry found when creating the sample, not a floating snapshot image.
The extension source includes fixes for discovered A2A input keys and card republishing;
the older demo's hand-built A2A discovery workaround is unnecessary.

The demo is **localhost-only and unauthenticated**. Every application binds to `127.0.0.1`;
the Registry container exposes only a loopback port. `a2a.authorization.required=false` is
specific to this local demonstration. Do not deploy these settings on a shared network.

## Build

From the **repository root**, install the current extensions and their deployment artifacts:

```shell
./mvnw -pl a2a-apicurio-registry/deployment,mcp-apicurio-registry/deployment,model-providers/ollama/deployment \
  -am install -DskipTests -Dno-format
./mvnw -f samples/agent-registry-workflow/pom.xml verify
ollama pull qwen2.5:7b
```

The sample uses its own parent POM and is also registered in `samples/pom.xml` for the repository's
`-Dsamples` build. Normal tests use mocks and require neither Ollama nor a Registry container.

## Run

```shell
python3 samples/agent-registry-workflow/scripts/run.py
```

The runner checks prerequisites and free ports, starts a dedicated ephemeral Registry, launches
two instances of `text-agent` (the second with the `translator` profile), the weather server and
the orchestrator, waits for readiness, then registers the weather tool. It refuses to reuse an
existing sample deployment. Ctrl+C stops its JVMs and removes its Registry container; data is lost.
It does not stop or modify your Ollama server or another demo stack.

Override `OLLAMA_URL` and `OLLAMA_MODEL` for a different installed model. It must support tool calls.
The runner uses fixed sample service ports to keep published addresses and registrations consistent.
Use a dedicated Registry: the current A2A extension searches all Agent Cards, not a sample-specific group.

Open **http://localhost:11020/** for the briefing dashboard. Choose a city, enter context and create
a French briefing. The page shows the contract versions observed before execution and the exact
MCP/A2A handoff results. It does not fabricate intermediate steps or simulate remote calls.

The same connected workflow is available over HTTP:

```shell
curl -fsS http://localhost:11020/workflow/briefing \
  -H 'Content-Type: application/json' \
  --data '{"city":"Amsterdam","context":"Visitors will walk through the city and meet colleagues to discuss open standards."}'
```

The response has `contracts`, ordered `steps` and the final French `result`.
Only Amsterdam, Paris and Madrid have fixture weather. Required contracts are checked before
any MCP or A2A invocation; a deprecated contract returns HTTP 503 with `stage: contract-check`.

### Explore the individual stages

The original stage endpoints remain useful for diagnosis. Discover the published agents:

```shell
curl -fsS http://localhost:11020/workflow/agents
```

Execute both remote agents, passing the summary into the translation task:

```shell
curl -fsS http://localhost:11020/workflow/summarize-and-translate \
  -H 'Content-Type: text/plain' \
  --data 'Amsterdam has canals and many bicycles. Open standards let independently built services communicate.'
```

The response contains `summary` and `translation`. Missing required agents produce HTTP 503 before
the workflow begins; blank or over-8000-character input produces HTTP 400.

Discover, connect and invoke the weather tool:

```shell
curl -fsS http://localhost:11020/workflow/weather \
  -H 'Content-Type: text/plain' --data 'What is the weather in Amsterdam?'
```

The answer should describe fictional 16 C/light rain data. Server logs under
`samples/agent-registry-workflow/target/run/` include the actual `getWeather` invocation.

### Why a typed weather tool?

`DiscoveredWeather` exposes a simple `city` parameter to the model. It calls the existing extension's
`searchMcpServers`, `connectMcpServer` and `callMcpTool`, and serializes the argument map with Jackson.
This keeps discovery/connection in the extension while the application owns sequencing and argument typing.

The generic extension tools remain suitable for exploration (see the existing MCP sample), but in
live testing the model sometimes supplied an object where `callMcpTool` requires a JSON string, or
omitted the connection key. A typed application tool avoids relying on prompt instructions to repair that.
The sample uses a named MCP group because default-group results currently format as `null/artifact`,
while connection keys normalize to `default/artifact`.

## Governance changes the consumer's behavior

Leave the interactive runner and dashboard open. In a second terminal:

```shell
python3 samples/agent-registry-workflow/scripts/run.py --governance
```

This removes a skill from a candidate summarizer card: Registry rejects it and preserves the
accepted definition. An additive update is accepted. Create another briefing: accepted contracts
remain usable. These are metadata updates, not deployments of new agent code.

Next:

```shell
python3 samples/agent-registry-workflow/scripts/run.py --deprecate
```

Create a briefing again: the application refuses to start it because the translator's registered
contract is `DEPRECATED`. No MCP or A2A invocation occurs for that request. Restore it:

```shell
python3 samples/agent-registry-workflow/scripts/run.py --restore
```

The next briefing succeeds. This is an explicit policy in `RequiredContracts`, not an automatic
feature of the discovery extension, and not a gateway rule preventing direct calls to the agent.
The independent diagnostic endpoints do not apply the briefing's lifecycle policy.

## Live smoke test, including governance

Stop the interactive runner first, then run:

```shell
python3 samples/agent-registry-workflow/scripts/run.py --smoke
```

This creates a fresh stack and asserts:

- Both named A2A agents are discovered using the actual extension.
- The two remote A2A calls return non-empty results. Unit tests separately verify that the translator
  receives the summary, not the original input. Language quality is a human evaluation, not a substring test.
- The MCP answer contains the fixture's city/temperature **and** the server log confirms real invocation.
- Removing a skill from the registered summarizer card is rejected with HTTP 400; no rejected version
  is created and accepted content remains unchanged.
- Adding a skill is accepted and persists as a new version.
- A connected briefing passes the actual MCP output into summarization and the actual summary
  into translation, and reports three enabled contract versions.
- The connected workflow still runs after the rejected update; deprecation blocks it before MCP
  invocation, and restoring the contract recovers the flow. Unit tests verify preflight ordering.

The runner always tears down its stack, including on failure. The smoke test requires a real Ollama model;
it is an explicit live evaluation, not part of the no-infrastructure Maven test run. Model behavior can vary.

## What this proves, and what it does not

- A2A cards are published with the extension's **programmatic publisher**, allowing one application
  to run under two identities. The public A2A card and registry publication use the same name/description/URL.
- MCP registration is an explicit `MCP_TOOL` artifact with connection labels, matching the consumer
  extension today. It does **not** implement MCP_SERVER adoption, exact server→tool references or automatic
  Registry-side `tools/list` ingestion.
- Compatibility protects accepted **metadata**. The additive governance test edits Registry content,
  not the running agent. Rejected publication does not stop running traffic. The sample does not enforce
  remote deployment pinning or contract drift checks. The connected briefing does enforce its own
  enabled-contract preflight policy. Metadata and content are read using the observed version,
  but endpoint resolution remains in the extensions; a concurrent change can occur after preflight.
  The returned contract list is not a transactional snapshot or proof of the running deployment's version.
- Publication is best-effort in the extension; the runner's discovery assertions detect missing cards.
- There is no gateway, tenant/agent/tool authorization, credential broker, durable session isolation,
  human escalation, token/cost quota, persistent memory or execution replay implementation here.
- Readiness endpoints and local logs are operational diagnostics, not tool-level QoS or execution audit.
- Native artifact discovery is exercised. No AI Catalog/ARD conformance or production security claim is made.
- The connected briefing chooses fixed roles and sequence in application code. It does not ask an LLM
  to plan arbitrary workflows. The separate weather assistant endpoint demonstrates model-driven tool use.

These boundaries keep the sample executable with today's projects while identifying the next integration
work in [Apicurio's AI Agent Registry epic](https://github.com/Apicurio/apicurio-registry/issues/6991).
