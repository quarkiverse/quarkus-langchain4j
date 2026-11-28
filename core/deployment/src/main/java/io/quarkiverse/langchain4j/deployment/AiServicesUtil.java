package io.quarkiverse.langchain4j.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

public final class AiServicesUtil {

    private AiServicesUtil() {
    }

    /**
     * The methods the generated implementation of an AI Service implements.
     * <p>
     * A class-based AI Service is implemented by a subclass of the class, which implements the native and abstract
     * methods of the class and of its superclasses, and nothing else. An interface-based one implements every method
     * of the interface and its superinterfaces that is not a default method.
     * <p>
     * Methods are visited the most specific type first, and a method of a supertype with the signature of one already
     * visited is overridden, by a method implemented here or by a concrete one of the user, which the generated
     * implementation must not replace.
     */
    public static List<MethodInfo> determineAiServiceMethods(ClassInfo type, IndexView index, boolean classBased) {
        List<MethodInfo> allMethods = new ArrayList<>(type.methods());
        if (classBased) {
            allMethods.addAll(superclassMethods(type, index));
        } else {
            for (ClassInfo superinterface : JandexUtil.getAllSuperinterfaces(type, index)) {
                if (!superinterface.name().equals(LangChain4jDotNames.CHAT_MEMORY_ACCESS)) {
                    allMethods.addAll(superinterface.methods());
                }
            }
        }

        List<MethodInfo> methodsToImplement = new ArrayList<>();
        List<MethodInfo> seenMethods = new ArrayList<>();
        for (MethodInfo method : allMethods) {
            short modifiers = method.flags();
            if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers)) {
                continue;
            }
            if (isOverridden(method, seenMethods)) {
                continue;
            }
            seenMethods.add(method);
            boolean concrete = classBased
                    ? !Modifier.isNative(modifiers) && !Modifier.isAbstract(modifiers)
                    : JandexUtil.isDefault(modifiers);
            if (!concrete) {
                methodsToImplement.add(method);
            }
        }
        return methodsToImplement;
    }

    private static boolean isOverridden(MethodInfo method, List<MethodInfo> seenMethods) {
        for (MethodInfo seen : seenMethods) {
            if (MethodUtil.methodSignaturesMatch(seen, method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The methods declared by the superclasses of the given class, as far as they are in the index.
     */
    private static List<MethodInfo> superclassMethods(ClassInfo classInfo, IndexView index) {
        List<MethodInfo> methods = new ArrayList<>();
        DotName superName = classInfo.superName();
        while (superName != null && !DotNames.OBJECT.equals(superName)) {
            ClassInfo superClass = index.getClassByName(superName);
            if (superClass == null) {
                break;
            }
            methods.addAll(superClass.methods());
            superName = superClass.superName();
        }
        return methods;
    }
}
