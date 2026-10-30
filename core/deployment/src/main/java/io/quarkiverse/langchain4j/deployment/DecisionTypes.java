package io.quarkiverse.langchain4j.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;

import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.Scale;

/**
 * The rules of the upstream {@code DecisionMethod} about the types of a decision method, applied to the Jandex index
 * so that they can be checked at build time.
 */
public final class DecisionTypes {

    public static final DotName DECIDE = DotName.createSimple(Decide.class);
    public static final DotName DECISION_RESULT = DotName.createSimple(DecisionResult.class);
    public static final DotName CHOICE = DotName.createSimple(Choice.class);
    public static final DotName SCALE = DotName.createSimple(Scale.class);
    public static final DotName YES_NO_ANSWER = DotName.createSimple(YesNoAnswer.class);
    public static final DotName DECISION_REQUEST_PARAMETERS = DotName.createSimple(DecisionRequestParameters.class);
    private static final DotName COMPLETABLE_FUTURE = DotName.createSimple(CompletableFuture.class);
    private static final DotName COMPLETION_STAGE = DotName.createSimple(CompletionStage.class);
    private static final DotName BOOLEAN = DotName.createSimple(Boolean.class);

    private DecisionTypes() {
    }

    /**
     * The type of the result of a decision method, without its {@code CompletableFuture} (or {@code CompletionStage})
     * and {@code DecisionResult} wrappers.
     */
    public static Type contentType(Type returnType) {
        Type type = returnType;
        if (isParameterizedBy(type, COMPLETABLE_FUTURE) || isParameterizedBy(type, COMPLETION_STAGE)) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (isParameterizedBy(type, DECISION_RESULT)) {
            type = type.asParameterizedType().arguments().get(0);
        }
        return type;
    }

    /**
     * Whether the given method returns a {@code CompletableFuture} or a {@code CompletionStage}.
     */
    public static boolean isAsync(MethodInfo method) {
        DotName name = method.returnType().name();
        return name.equals(COMPLETABLE_FUTURE) || name.equals(COMPLETION_STAGE);
    }

    /**
     * Whether the given type is the type of a single answer: {@code boolean}, {@code YesNoAnswer}, an enum,
     * {@code Choice<enum>} or {@code Scale<enum>}.
     */
    public static boolean isAnswerType(Type type, IndexView index) {
        if (isBoolean(type) || type.name().equals(YES_NO_ANSWER)) {
            return true;
        }
        if (type.name().equals(CHOICE) || type.name().equals(SCALE)) {
            return true;
        }
        return type.kind() == Type.Kind.CLASS && isEnum(type.name(), index);
    }

    /**
     * The class of the given type when it answers several questions, one per field annotated with {@code @Decide},
     * like a record, or {@code null}.
     */
    public static ClassInfo objectAnswerType(Type type, IndexView index) {
        if (type.kind() != Type.Kind.CLASS) {
            return null;
        }
        ClassInfo classInfo = index.getClassByName(type.name());
        if (classInfo == null || classInfo.isInterface() || classInfo.isEnum()
                || Modifier.isAbstract(classInfo.flags())) {
            return null;
        }
        return decideFields(classInfo, index).isEmpty() ? null : classInfo;
    }

    /**
     * Whether a method returning the given content type has a {@code boolean} answer, the only kind of answer a
     * threshold applies to.
     */
    public static boolean hasBooleanAnswer(Type contentType, IndexView index) {
        if (isBoolean(contentType)) {
            return true;
        }
        ClassInfo objectType = objectAnswerType(contentType, index);
        return objectType != null && decideFields(objectType, index).stream().anyMatch(f -> isBoolean(f.type()));
    }

    /**
     * The fields of the given class and of its superclasses that are annotated with {@code @Decide}.
     */
    public static List<FieldInfo> decideFields(ClassInfo classInfo, IndexView index) {
        List<FieldInfo> fields = new ArrayList<>();
        for (ClassInfo c = classInfo; c != null; c = superclass(c, index)) {
            for (FieldInfo field : c.fields()) {
                if (field.hasDeclaredAnnotation(DECIDE)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * The classes that upstream accesses by reflection to implement the given decision method: the object answer type
     * and its superclasses, the enums used as answers, and the types of the parameters sent to the model as structured
     * values, which are converted to maps with the JSON codec. Only the classes of the index are returned.
     */
    public static Set<DotName> reflectiveTypes(MethodInfo method, IndexView index) {
        Set<DotName> types = new LinkedHashSet<>();
        Type contentType = contentType(method.returnType());
        ClassInfo objectType = objectAnswerType(contentType, index);
        if (objectType != null) {
            for (ClassInfo c = objectType; c != null; c = superclass(c, index)) {
                types.add(c.name());
            }
            for (FieldInfo field : decideFields(objectType, index)) {
                addAnswerEnum(field.type(), index, types);
            }
        } else {
            addAnswerEnum(contentType, index, types);
        }
        for (MethodParameterInfo parameter : method.parameters()) {
            addIndexedTypes(parameter.type(), index, types);
        }
        types.remove(DECISION_REQUEST_PARAMETERS);
        return types;
    }

    public static boolean isBoolean(Type type) {
        return (type.kind() == Type.Kind.PRIMITIVE && type.asPrimitiveType().equals(PrimitiveType.BOOLEAN))
                || type.name().equals(BOOLEAN);
    }

    private static void addAnswerEnum(Type type, IndexView index, Set<DotName> types) {
        Type enumType = type;
        if ((type.name().equals(CHOICE) || type.name().equals(SCALE)) && type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            enumType = type.asParameterizedType().arguments().get(0);
        }
        if (isEnum(enumType.name(), index)) {
            types.add(enumType.name());
        }
    }

    private static void addIndexedTypes(Type type, IndexView index, Set<DotName> types) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            for (Type argument : type.asParameterizedType().arguments()) {
                addIndexedTypes(argument, index, types);
            }
        }
        if (type.kind() == Type.Kind.ARRAY) {
            addIndexedTypes(type.asArrayType().elementType(), index, types);
            return;
        }
        String name = type.name().toString();
        if (type.kind() == Type.Kind.PRIMITIVE || name.startsWith("java.") || name.startsWith("dev.langchain4j.")) {
            return;
        }
        ClassInfo classInfo = index.getClassByName(type.name());
        if (classInfo != null && !classInfo.isInterface()) {
            types.add(classInfo.name());
        }
    }

    private static boolean isEnum(DotName name, IndexView index) {
        ClassInfo classInfo = index.getClassByName(name);
        return classInfo != null && classInfo.isEnum();
    }

    private static boolean isParameterizedBy(Type type, DotName rawType) {
        return type.kind() == Type.Kind.PARAMETERIZED_TYPE && type.name().equals(rawType);
    }

    private static ClassInfo superclass(ClassInfo classInfo, IndexView index) {
        DotName superName = classInfo.superName();
        if (superName == null || superName.equals(DotName.OBJECT_NAME)) {
            return null;
        }
        return index.getClassByName(superName);
    }
}
