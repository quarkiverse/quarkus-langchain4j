# MCP-based filesystem assistant

This sample showcases how to use an MCP server (spawned as a subprocess) to
provide tools to an LLM. In this case, we use the
`@modelcontextprotocol/server-filesystem` MCP server that is provided as an
[NPM
package](https://www.npmjs.com/package/@modelcontextprotocol/server-filesystem),
giving the LLM a set of tools to interact with the local filesystem. The
only directory that the agent will be able to access is the `playground`
directory in the root of the project.

# Prerequisites

The project assumes that you have `npm` installed, and it attempts to run
the MCP server by executing `npm exec
@modelcontextprotocol/server-filesystem@0.6.2 playground` (`playground`
denotes the allowed directory for the agent). If your environment requires a
different command to run the server, please modify the constructor of the
`FilesystemToolProvider` class manually and adjust it to your needs, but
keep in mind that you have to run the package as a subprocess directly in a
way that Quarkus can connect to its standard input and output.

# Running the sample

Run the sample using `mvn quarkus:dev` and then access
`http://localhost:8080` to start chatting. Some more information
and a few suggested prompts to try out will be shown on that page.