package io.quarkiverse.langchain4j.agentic.auth;

/**
 * Supplies credentials for calls to remote A2A agents.
 * <p>
 * Declare one as a CDI bean. It applies to every A2A client agent: those declared with
 * {@link dev.langchain4j.agentic.declarative.A2AClientAgent}, those built with
 * {@link dev.langchain4j.agentic.AgenticServices#a2aBuilder(String)}, and those that other extensions create, such
 * as the agents discovered through Apicurio Registry. It is called for every request, so it can return a token that
 * it refreshes, for example from a Quarkus OIDC client, and it receives the agent each request is for, so it can choose
 * credentials per agent. Only one such bean is supported.
 * <p>
 * The header is sent with the request that fetches the remote agent's Agent Card and with every message, on every
 * transport. A client customizer, such as an {@link dev.langchain4j.agentic.declarative.A2AClientCustomizer} method,
 * cannot remove it.
 */
public interface AgentAuthProvider {

    /**
     * Provide authorization data which will be set as an HTTP Authorization header value.
     *
     * @param input the agent the request is sent to
     * @return authorization data which must include an HTTP Authorization scheme value, for example: "Bearer the_access_token".
     *         Returning null will result in no Authorization header being set.
     */
    String getAuthorization(Input input);

    /**
     * The remote agent a request is sent to.
     */
    interface Input {

        /**
         * @return the name in the remote agent's Agent Card; for the request that fetches that Agent Card, which is not
         *         known yet, the name of the local A2A client agent
         */
        String agentName();

        /**
         * @return the remote agent's URL, as the A2A client agent was created with
         */
        String agentUrl();
    }
}
