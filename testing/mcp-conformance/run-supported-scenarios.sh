SCENARIOS="initialize tools_call"
for SCENARIO in $SCENARIOS; do
  npx @modelcontextprotocol/conformance client --command "sh client-command.sh" --scenario $SCENARIO
  echo "------------------------------------------------------------------"
done