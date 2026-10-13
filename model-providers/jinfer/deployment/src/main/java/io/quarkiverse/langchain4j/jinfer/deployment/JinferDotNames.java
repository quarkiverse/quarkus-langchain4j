package io.quarkiverse.langchain4j.jinfer.deployment;

import org.jboss.jandex.DotName;

import dev.langchain4j.model.audio.TextToSpeechModel;
import io.quarkiverse.langchain4j.ModelName;

/**
 * DotNames that the core framework does not expose. jinfer produces a {@link TextToSpeechModel}
 * bean itself (there is no TextToSpeech provider SPI in core), so it needs its own DotName.
 */
final class JinferDotNames {

    static final DotName TEXT_TO_SPEECH_MODEL = DotName.createSimple(TextToSpeechModel.class);
    static final DotName MODEL_NAME = DotName.createSimple(ModelName.class);

    private JinferDotNames() {
    }
}
