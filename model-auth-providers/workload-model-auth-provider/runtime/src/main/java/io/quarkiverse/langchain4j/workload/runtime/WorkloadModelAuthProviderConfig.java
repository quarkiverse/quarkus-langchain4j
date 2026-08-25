package io.quarkiverse.langchain4j.workload.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.workload-model-auth-provider")
public interface WorkloadModelAuthProviderConfig {

    /**
     * Path to the workload identity provider token file, for example, a SPIFFE JWT-SVID.
     * The token is read from this path and periodically refreshed based on its expiry claim.
     */
    String tokenPath();
}
