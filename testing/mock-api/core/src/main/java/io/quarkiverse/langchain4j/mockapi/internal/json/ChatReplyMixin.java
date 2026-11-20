package io.quarkiverse.langchain4j.mockapi.internal.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

/**
 * A chat reply is serialized from its fields: its accessor-shaped builder methods are not properties.
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
abstract class ChatReplyMixin {
}
