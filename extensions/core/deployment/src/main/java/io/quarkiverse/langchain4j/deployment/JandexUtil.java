package io.quarkiverse.langchain4j.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

class JandexUtil {

    private static final Logger log = Logger.getLogger(JandexUtil.class);

    static Collection<ClassInfo> getAllSuperinterfaces(ClassInfo interfaceName, IndexView index) {
        Set<ClassInfo> result = new HashSet<>();

        Queue<DotName> workQueue = new ArrayDeque<>();
        Set<DotName> alreadyProcessed = new HashSet<>();

        workQueue.add(interfaceName.name());
        while (!workQueue.isEmpty()) {
            DotName iface = workQueue.remove();
            if (!alreadyProcessed.add(iface)) {
                continue;
            }

            List<ClassInfo> directSuperInterfaces = new ArrayList<>();
            for (DotName name : interfaceName.interfaceNames()) {
                ClassInfo classInfo = index.getClassByName(name);
                if (classInfo == null) {
                    log.warn("'" + name
                            + "' used for creating an AiService is not an interface. Attempting to create an AiService using this class will fail");
                }
                directSuperInterfaces.add(classInfo);
            }
            for (ClassInfo directSubInterface : directSuperInterfaces) {
                result.add(directSubInterface);
                workQueue.add(directSubInterface.name());
            }
        }

        return result;
    }

    static boolean isDefault(short flags) {
        return ((flags & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC);
    }

    static Class<?> load(Type type, ClassLoader classLoader) {
        if (type.kind() == Type.Kind.PRIMITIVE) {
            PrimitiveType prim = type.asPrimitiveType();
            switch (prim.primitive()) {
                case INT:
                    return int.class;
                case BYTE:
                    return byte.class;
                case CHAR:
                    return char.class;
                case LONG:
                    return long.class;
                case FLOAT:
                    return float.class;
                case SHORT:
                    return short.class;
                case DOUBLE:
                    return double.class;
                case BOOLEAN:
                    return boolean.class;
                default:
                    throw new RuntimeException("Unknown type " + prim.primitive());
            }
        } else {
            try {
                return Class.forName(type.name().toString(), false, classLoader);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
