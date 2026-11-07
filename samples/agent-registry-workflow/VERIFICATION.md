# Verification record

## Latest run: 2026-11-04

Rebased onto Quarkus LangChain4j `main` at `32099e24e`, which includes the MCP_SERVER discovery and
A2A paging changes from quarkiverse/quarkus-langchain4j#3043. Extensions built from that checkout.

| Component | Version |
|---|---|
| Quarkus LangChain4j extensions | `999-SNAPSHOT` built from `32099e24e` |
| Quarkus | 3.40.0 |
| A2A SDK / Quarkus MCP Server | 1.4.0.Final / 2.0.2 |
| Apicurio Registry | `quay.io/apicurio/apicurio-registry:3.3.3` (`sha256:bece19d3e6f0`), SDK 3.3.3 |
| Model | Ollama `qwen2.5:7b` (`845dbda0ea48`) |
| Java | 25.0.2 |

- `./mvnw -f samples/agent-registry-workflow/pom.xml verify`: 9 tests passed.
- `python3 samples/agent-registry-workflow/scripts/run.py --smoke`: passed, with every check listed
  below. Both agents were discovered, real A2A summary and translation calls ran, and `getWeather`
  executed on the MCP server, confirmed in its log. The connected MCP → summary → translation briefing
  reported three contracts. Registry rejected the translator skill removal with HTTP 400
  (`Skill 'translator' was removed`, context `/skills`), and the briefing still ran on the retained
  registration. The additive update was accepted as a new version and the briefing reported it. The
  runner removed its processes, Registry container and network.

## First run: 2026-10-07

Local evaluation based on Quarkus LangChain4j main `ea14602c9` plus this sample.
Java 25.0.2, Quarkus 3.40.0, A2A SDK 1.4.0.Final, MCP Server 2.0.2,
Apicurio Registry/SDK 3.3.3, host Ollama with `qwen2.5:7b`.

## Automated checks

From the repository root:

```shell
./mvnw -f samples/agent-registry-workflow/pom.xml verify
```

Passed: nine tests across `WorkflowResourceTest`, `RegisteredContractsTest`, `DiscoveredWeatherTest` and `WeatherToolsTest`.
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
- Removing the translator's translation skill returned a Registry COMPATIBILITY violation (400), the
  rejected version returned 404, and accepted content and latest version/globalId were retained.
  Adding a translation skill created a new accepted version.
- Runner removed its application processes, Registry container and Compose network.

## Connected workflow follow-up

The enhanced live run also passed the single `/workflow/briefing` request:

- Three enabled contracts reported with version/globalId.
- Actual `getWeather` output included in the A2A summarizer input.
- Actual summary passed unchanged into the A2A translator input.
- Non-empty French final result returned with all step inputs/outputs.
- The briefing executed before and after the rejected translator update and reported the same
  registered translator version/globalId. After a compatible addition it executed and reported the new registration.
- The sample no longer has a consumer-side deprecation or capability gate. `RegisteredContracts`
  only observes metadata; a unit test confirms a deprecated state is reported rather than rejected.

Automated tests additionally assert the ordering of metadata reads, discovery, MCP invocation and A2A
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
