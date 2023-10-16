package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.ai4j.openai4j.moderation.ModerationResponse;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(ModerationResponse.class)
@JsonDeserialize(builder = ModerationResponse.Builder.class)
public abstract class ModerationResponseMixin {
}
