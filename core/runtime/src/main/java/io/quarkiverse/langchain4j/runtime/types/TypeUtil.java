package io.quarkiverse.langchain4j.runtime.types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.Flow;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import io.smallrye.mutiny.Multi;

public final class TypeUtil {

    private TypeUtil() {
    }

    public static boolean isTokenStream(Type returnType) {
        return isTypeOf(returnType, TokenStream.class);
    }

    public static boolean isMulti(Type returnType) {
        return isTypeOf(returnType, Multi.class);
    }

    public static boolean isFlowPublisher(Type returnType) {
        return isTypeOf(returnType, Flow.Publisher.class);
    }

    /**
     * Returns true for the return types implemented by streaming the response: {@code Multi} and {@code Flow.Publisher}.
     */
    public static boolean isStreamed(Type returnType) {
        return isMulti(returnType) || isFlowPublisher(returnType);
    }

    /**
     * Returns the type argument of a single-argument parameterized type, such as the {@code T} of {@code Uni<T>}.
     */
    public static Type typeArgument(Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new IllegalStateException("Expected a parameterized type but got " + type);
        }
        return parameterizedType.getActualTypeArguments()[0];
    }

    public static boolean isResult(Type returnType) {
        return isTypeOf(returnType, Result.class);
    }

    public static Type resultTypeParam(ParameterizedType returnType) {
        if (!isTypeOf(returnType, Result.class)) {
            throw new IllegalStateException("Can only be called with Result<T> type");
        }
        return returnType.getActualTypeArguments()[0];
    }

    public static boolean isImage(Type returnType) {
        return isTypeOf(returnType, Image.class);
    }

    public static boolean isResultImage(Type returnType) {
        if (!isImage(returnType)) {
            return false;
        }
        return isImage(resultTypeParam((ParameterizedType) returnType));
    }

    public static boolean isTypeOf(Type type, Class<?> clazz) {
        if (type instanceof Class<?>) {
            return type.equals(clazz);
        }
        if (type instanceof ParameterizedType pt) {
            return isTypeOf(pt.getRawType(), clazz);
        }
        throw new IllegalStateException("Unsupported return type " + type);
    }
}
