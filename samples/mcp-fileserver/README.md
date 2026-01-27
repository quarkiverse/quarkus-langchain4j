# MCP-based filesystem server

This sample showcases how to implement an MCP server to give an LLM a set of tools to interact with the local filesystem. Property `working.folder` is used to limit LLM to work with "safe" folder only.

# Usage
1. Build the uber-jar: `mvn clean install`
2. Run the server directly `java -Dworking.folder=src -jar $PWD/target/quarkus-langchain4j-sample-mcp-fileserver-1.0-SNAPSHOT-runner.jar`
3. Or use mcp inspector: `npx @modelcontextprotocol/inspector`
4. Can be used with quarkus-langchain4j clients, by adding this property:
```
quarkus.langchain4j.mcp.filesystem.command="java,-Dworking.folder=playground,-jar,/home/user/.m2/repository/io/quarkiverse/langchain4j/quarkus-langchain4j-sample-mcp-fileserver/1.0-SNAPSHOT/quarkus-langchain4j-sample-mcp-fileserver-1.0-SNAPSHOT-runner.jar"
```
Beware, that MCP tools usually require the full path to the executable file


