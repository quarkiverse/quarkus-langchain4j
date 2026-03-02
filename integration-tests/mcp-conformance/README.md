### MCP Conformance Testing

## Before running any test scenarios

```shell
mvn package -Dquarkus-langchain4j.version=999-SNAPSHOT # or any other version you want to test
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
