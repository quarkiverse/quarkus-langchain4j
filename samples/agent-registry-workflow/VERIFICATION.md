# Verification record

Local evaluation on 2026-10-07, based on Quarkus LangChain4j main `ea14602c9` plus this sample.
Java 25.0.2, Quarkus 3.40.0, A2A SDK 1.4.0.Final, MCP Server 2.0.2,
Apicurio Registry/SDK 3.3.3, host Ollama with `qwen2.5:7b`.

## Automated checks

From the repository root:

```shell
./mvnw -f samples/agent-registry-workflow/pom.xml verify
```

Passed: nine tests across `WorkflowResourceTest`, `RequiredContractsTest`, `DiscoveredWeatherTest` and `WeatherToolsTest`.
The same build passed with `OLLAMA_URL=http://127.0.0.1:1`, demonstrating that the ordinary
tests do not need a live model. All three applications packaged successfully.

```shell
python3 samples/agent-registry-workflow/scripts/run.py --smoke
```

Passed against a fresh ephemeral Registry and four real application processes:

- Discovered exactly `summarizer` and `translator` via `ApicurioAgentsRegistry`.
- Real A2A summary: “Amsterdam features canals and bicycles. Open standards enable independent
  services to communicate effectively.”
- Real A2A translation: “Amsterdam se caractérise par des canaux et des vélos. Les normes ouvertes
  permettent aux services indépendants de communiquer efficacement.”
- MCP response: “The fictional weather data shows 16 degrees Celsius with light rain in Amsterdam.”
  The Weather server log confirmed `getWeather` actually executed; the result alone was not treated
  as proof of a tool call.
- Removing a registered skill returned 400, the rejected version returned 404, and accepted content
  was retained. Adding a skill created a new accepted version.
- Runner removed its application processes, Registry container and Compose network.

## Connected workflow follow-up

The enhanced live run also passed the single `/workflow/briefing` request:

- Three enabled contracts reported with version/globalId.
- Actual `getWeather` output included in the A2A summarizer input.
- Actual summary passed unchanged into the A2A translator input.
- Non-empty French final result returned with all step inputs/outputs.
- Briefing remained operational after the rejected skill update.
- Deprecating the translator returned 503 at `contract-check` and did not increase the Weather
  server's invocation count. Restoring `ENABLED` recovered the complete flow.

Automated tests additionally assert the ordering of preflight, discovery, MCP invocation and A2A
handoffs. The dashboard uses the same endpoint and renders returned values as text; browser visual
testing is not part of the recorded automated checks.

This is a local verification record, not a claim of upstream merge, release, CI execution, security
certification or deterministic LLM output. The build reports upstream SDK split-package and optional
gRPC indexing warnings; those did not prevent these tested HTTP/JSON-RPC paths from working.

## Observations from integration testing

- Generic MCP discovery formats the default group as `null` in search output while connection keys
  normalize it to `default`. The sample uses the explicit group `weather-tools`.
- The model sometimes supplied a JSON object or omitted arguments for the generic `callMcpTool`
  interface. `DiscoveredWeather` gives the application a typed city argument and calls the same
  extension operations in order, encoding the JSON string with Jackson.
- Current A2A input-key and republish fixes are used directly; the original demo's custom discovery
  workaround is not copied.
- Registry compatibility decisions were tested independently of runtime enforcement. No tenant
  isolation, authenticated remote connection, version-pinning or execution replay claim is made.
