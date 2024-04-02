package io.quarkiverse.langchain4j.openai.runtime.jackson;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import dev.ai4j.openai4j.chat.ImageDetail;

public class ImageDetailsSerializer extends StdSerializer<ImageDetail> {
    public ImageDetailsSerializer() {
        super(ImageDetail.class);
    }

    @Override
    public void serialize(ImageDetail value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString().toLowerCase(Locale.ROOT));
    }
}
