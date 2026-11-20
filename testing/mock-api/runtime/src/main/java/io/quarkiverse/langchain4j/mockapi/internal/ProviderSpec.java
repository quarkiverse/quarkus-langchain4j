package io.quarkiverse.langchain4j.mockapi.internal;

import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

/**
 * Describes how one model provider is pointed at the mock. {@code id} is the provider id core's build items carry.
 * {@code endpointKeys} are the endpoint config keys to override, relative to a model's prefix, each with the path
 * appended to the mock URL. {@code identityDefaults} maps an identity property, relative to the model's prefix, to its
 * placeholder value. {@code identityAlternatives} maps an identity property to the properties (relative to the same
 * prefix) that replace it: when the user configured any of them for a model, the placeholder is not filled in for that
 * model. {@code pathPrefix} (empty or starting with {@code /}) goes between the mock URL and every endpoint suffix, so
 * the provider's requests arrive under a path of their own.
 */
public record ProviderSpec(String id, String prefix, String pathPrefix, List<EndpointKey> endpointKeys,
        Map<String, String> identityDefaults, Map<String, List<String>> identityAlternatives, String credentialsBean) {

    public ProviderSpec(String id, String prefix, String pathPrefix, List<EndpointKey> endpointKeys,
            Map<String, String> identityDefaults, Map<String, List<String>> identityAlternatives) {
        this(id, prefix, pathPrefix, endpointKeys, identityDefaults, identityAlternatives, null);
    }

    /**
     * Names a bean class of this module that supplies the provider's credentials against the mock; it is registered only
     * when the provider is in use, and named rather than referenced because it compiles against the provider's optional
     * dependency.
     */
    public ProviderSpec withCredentialsBean(String credentialsBean) {
        return new ProviderSpec(id, prefix, pathPrefix, endpointKeys, identityDefaults, identityAlternatives, credentialsBean);
    }

    /**
     * The property prefix, ending in a dot, of the model with the given config name.
     */
    public String modelPrefix(String configName) {
        return NamedConfigUtil.isDefault(configName) ? prefix + "." : prefix + "." + configName + ".";
    }

    public record EndpointKey(String key, String pathSuffix) {
    }
}
