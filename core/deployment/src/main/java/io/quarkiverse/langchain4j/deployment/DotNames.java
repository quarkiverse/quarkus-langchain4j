package io.quarkiverse.langchain4j.deployment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.inject.Instance;

import org.jboss.jandex.DotName;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public class DotNames {

    public static final DotName BOOLEAN = DotName.createSimple(Boolean.class);
    public static final DotName PRIMITIVE_BOOLEAN = DotName.createSimple(boolean.class);
    public static final DotName BYTE = DotName.createSimple(Byte.class);
    public static final DotName PRIMITIVE_BYTE = DotName.createSimple(byte.class);
    public static final DotName CHARACTER = DotName.createSimple(Character.class);
    public static final DotName PRIMITIVE_CHAR = DotName.createSimple(char.class);
    public static final DotName DOUBLE = DotName.createSimple(Double.class);
    public static final DotName PRIMITIVE_DOUBLE = DotName.createSimple(double.class);
    public static final DotName FLOAT = DotName.createSimple(Float.class);
    public static final DotName PRIMITIVE_FLOAT = DotName.createSimple(float.class);
    public static final DotName INTEGER = DotName.createSimple(Integer.class);
    public static final DotName PRIMITIVE_INT = DotName.createSimple(int.class);
    public static final DotName LONG = DotName.createSimple(Long.class);
    public static final DotName PRIMITIVE_LONG = DotName.createSimple(long.class);
    public static final DotName SHORT = DotName.createSimple(Short.class);
    public static final DotName PRIMITIVE_SHORT = DotName.createSimple(short.class);
    public static final DotName BIG_INTEGER = DotName.createSimple(BigInteger.class);
    public static final DotName BIG_DECIMAL = DotName.createSimple(BigDecimal.class);
    public static final DotName STRING = DotName.createSimple(String.class);
    public static final DotName URI = DotName.createSimple(java.net.URI.class);
    public static final DotName URL = DotName.createSimple(java.net.URL.class);
    public static final DotName LIST = DotName.createSimple(List.class);
    public static final DotName SET = DotName.createSimple(Set.class);
    public static final DotName MULTI = DotName.createSimple(Multi.class);
    public static final DotName UNI = DotName.createSimple(Uni.class);
    public static final DotName BLOCKING = DotName.createSimple(Blocking.class);
    public static final DotName NON_BLOCKING = DotName.createSimple(NonBlocking.class);
    public static final DotName COMPLETION_STAGE = DotName.createSimple(CompletionStage.class);
    public static final DotName RUN_ON_VIRTUAL_THREAD = DotName.createSimple(RunOnVirtualThread.class);

    public static final DotName OBJECT = DotName.createSimple(Object.class.getName());
    public static final DotName RECORD = DotName.createSimple(Record.class);
    public static final DotName CDI_INSTANCE = DotName.createSimple(Instance.class);

    public static final DotName CHAT_MODEL_LISTENER = DotName.createSimple(ChatModelListener.class);
}
