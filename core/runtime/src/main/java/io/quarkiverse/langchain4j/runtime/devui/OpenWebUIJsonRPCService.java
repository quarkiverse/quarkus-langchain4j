package io.quarkiverse.langchain4j.runtime.devui;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.eclipse.microprofile.config.ConfigProvider;

@ActivateRequestContext
public class OpenWebUIJsonRPCService {

    public String getConfigValue(String key) {
        try {
            return ConfigProvider.getConfig().getValue(key, String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
