### MCP Conformance Testing

## Before running any test scenarios

Make sure the testing client application is built. This should happen as part of the build of `quarkus-langchain4j`, 
but in case you need to do it explicitly in this module (both the properties with version overrides are optional):

```shell
mvn package \
  -Dquarkus-langchain4j.version=999-SNAPSHOT  \
  -Dquarkus.platform.version=3.27.1
```

## Running the tests

WARNING: You should generally be using the same version of the `modelcontextprotocol/conformance` package 
that is used by the CI (see `mcp-conformance-tests` in `.github/workflows/build-pull-request.yml`) to avoid getting different results. In the following snippets, replace `VERSION` with a proper version number.

To run all scenarios, including the ones that we are not supporting right now (repl:
```shell
npx @modelcontextprotocol/conformance@VERSION client --command "sh client-command.sh" --suite all --expected-failures conformance-baseline.yml
```

To run only the scenarios that we are supporting right now:
```shell
SCENARIOS="initialize tools_call"
for SCENARIO in $SCENARIOS; do
  npx @modelcontextprotocol/conformance@VERSION client --command "sh client-command.sh" --scenario $SCENARIO
  echo "------------------------------------------------------------------"
done
```

To run a specific scenario:
```shell
npx @modelcontextprotocol/conformance@VERSION client --command "sh client-command.sh" --scenario $SCENARIO
```
