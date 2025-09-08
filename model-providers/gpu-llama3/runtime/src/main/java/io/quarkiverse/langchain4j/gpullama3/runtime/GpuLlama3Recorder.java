package io.quarkiverse.langchain4j.gpullama3.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.GpuLlama3Config;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.GpuLlama3FixedRuntimeConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class GpuLlama3Recorder {

    public Supplier<ChatModel> chatModel(GpuLlama3Config runtimeConfig,
            GpuLlama3FixedRuntimeConfig fixedRuntimeConfig,
            String configName) {

        var cfg = runtimeConfig.defaultConfig();
        var fixed = fixedRuntimeConfig.defaultConfig();

        if (cfg.enableIntegration()) {
            var builder = GpuLlama3ChatModel
                    .builder()
                    .modelPath(Paths.get(fixed.modelPath()));

            return () -> builder.build();
        } else {
            return DisabledChatModel::new;
        }
    }

    public RuntimeValue<ChatModel> create(GpuLlama3Config config) {
        GpuLlama3ChatModel model = GpuLlama3ChatModel.builder()
                .modelPath(Path.of(config.defaultConfig().modelPath()))
                .build();
        return new RuntimeValue<>(model);
    }
}
