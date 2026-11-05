package io.quarkiverse.langchain4j.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

public final class AiServicesUtil {

    private AiServicesUtil() {
    }

    public static List<MethodInfo> determineAiServiceMethods(ClassInfo iface, IndexView index) {
        List<MethodInfo> allMethods = new ArrayList<>(iface.methods());
        JandexUtil.getAllSuperinterfaces(iface, index).stream().filter(ci -> !ci.name().equals(
                LangChain4jDotNames.CHAT_MEMORY_ACCESS)).forEach(ci -> allMethods.addAll(ci.methods()));

        List<MethodInfo> methodsToImplement = new ArrayList<>();
        for (MethodInfo method : allMethods) {
            short modifiers = method.flags();
            if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || JandexUtil.isDefault(
                    modifiers)) {
                continue;
            }

            if (methodsToImplement.stream().anyMatch(m -> MethodUtil.methodSignaturesMatch(m, method))) {
                continue;
            }
            methodsToImplement.add(method);
        }
        return methodsToImplement;
    }
}
