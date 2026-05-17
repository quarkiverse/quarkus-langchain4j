package io.quarkiverse.langchain4j.openai;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;

public class DisabledAudioTranscriptionModel implements AudioTranscriptionModel {

    @Override
    public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request) {
        throw new ModelDisabledException("AudioTranscriptionModel is disabled");
    }
}
