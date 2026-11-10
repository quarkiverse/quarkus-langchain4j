package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

/**
 * Supplies credentials for calls to A2A agents discovered through Apicurio Registry.
 * <p>
 * Declare one as a CDI bean. It is called for every request to a discovered agent, so it can
 * return a token that it refreshes, for example from a Quarkus OIDC client. These credentials are
 * for the agents, not for the registry: configure the registry's own credentials with
 * {@code quarkus.langchain4j.a2a.apicurio-registry.auth.*}.
 * <p>
 * Without such a bean, calls to discovered agents carry no credentials.
 */
public interface A2AAgentAuthProvider {

    /**
     * @param agent the discovered agent the request is sent to
     * @return the value of the {@code Authorization} header, for example {@code "Bearer <token>"}, or
     *         {@code null} to send none
     */
    String getAuthorization(DiscoveredAgent agent);

    /**
     * An agent discovered through the registry.
     */
    interface DiscoveredAgent {

        /**
         * @return the registry group of its Agent Card
         */
        String groupId();

        /**
         * @return the registry artifact ID of its Agent Card
         */
        String artifactId();

        /**
         * @return the name it is registered under in the agents registry
         */
        String name();

        /**
         * @return the URL its requests are sent to
         */
        String url();
    }
}
