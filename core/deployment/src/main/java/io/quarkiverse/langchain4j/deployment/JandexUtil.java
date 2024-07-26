package io.quarkiverse.langchain4j.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeVariable;
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

    /**
     * Returns the captured generic types of an interface given a class that at some point in the class
     * hierarchy implements the interface.
     *
     * The list contains the types in the same order as they are generic parameters defined on the interface
     *
     * A result is only returned if and only if all the generics where captured. If any of them where not defined by the class
     * an exception is thrown.
     *
     * Also note that all parts of the class/interface hierarchy must be in the supplied index
     *
     * As an example, imagine the following class:
     *
     * <pre>
     *
     * class MyList implements List&lt;String&gt; {
     *     ...
     * }
     *
     * </pre>
     *
     * If we call
     *
     * <pre>
     *
     * JandexUtil.resolveTypeParameters(DotName.createSimple(MyList.class.getName()),
     *         DotName.createSimple(List.class.getName()), index)
     *
     * </pre>
     *
     * then the result will contain a single element of class ClassType whose name() would return a DotName for String
     */
    static List<Type> resolveTypeParameters(DotName input, DotName target, IndexView index) {
        final ClassInfo inputClassInfo = fetchFromIndex(input, index);

        Type startingType = getType(inputClassInfo, index);
        final List<Type> result = findParametersRecursively(startingType, target,
                new HashSet<>(), index);
        // null means not found
        if (result == null) {
            return Collections.emptyList();
        }

        return result;
    }

    /**
     * Creates a type for a ClassInfo
     */
    private static Type getType(ClassInfo inputClassInfo, IndexView index) {
        List<TypeVariable> typeParameters = inputClassInfo.typeParameters();
        if (typeParameters.isEmpty())
            return ClassType.create(inputClassInfo.name(), Type.Kind.CLASS);
        Type owner = null;
        // ignore owners for non-static classes
        if (inputClassInfo.enclosingClass() != null && !Modifier.isStatic(inputClassInfo.flags())) {
            owner = getType(fetchFromIndex(inputClassInfo.enclosingClass(), index), index);
        }
        return ParameterizedType.create(inputClassInfo.name(), typeParameters.toArray(new Type[0]), owner);
    }

    /**
     * Finds the type arguments passed from the starting type to the given target type, mapping
     * generics when found, on the way down. Returns null if not found.
     */
    private static List<Type> findParametersRecursively(Type type, DotName target,
            Set<DotName> visitedTypes, IndexView index) {
        DotName name = type.name();
        // cache results first
        if (!visitedTypes.add(name)) {
            return null;
        }

        // always end at Object
        if (DotNames.OBJECT.equals(name) || DotNames.RECORD.equals(name)) {
            return null;
        }

        final ClassInfo inputClassInfo = fetchFromIndex(name, index);

        // look at the current type
        if (target.equals(name)) {
            Type thisType = getType(inputClassInfo, index);
            if (thisType.kind() == Type.Kind.CLASS)
                return Collections.emptyList();
            else
                return thisType.asParameterizedType().arguments();
        }

        // superclasses first
        Type superClassType = inputClassInfo.superClassType();
        List<Type> superResult = findParametersRecursively(superClassType, target, visitedTypes, index);
        if (superResult != null) {
            // map any returned type parameters to our type arguments on the way down
            return mapTypeArguments(superClassType, superResult, index);
        }

        // interfaces second
        for (Type interfaceType : inputClassInfo.interfaceTypes()) {
            List<Type> ret = findParametersRecursively(interfaceType, target, visitedTypes, index);
            if (ret != null) {
                // map any returned type parameters to our type arguments on the way down
                return mapTypeArguments(interfaceType, ret, index);
            }
        }

        // not found
        return null;
    }

    /**
     * Maps any type parameters in typeArgumentsFromSupertype from the type parameters declared in appliedType's declaration
     * to the type arguments we passed in appliedType
     */
    private static List<Type> mapTypeArguments(Type appliedType, List<Type> typeArgumentsFromSupertype, IndexView index) {
        // no type arguments to map
        if (typeArgumentsFromSupertype.isEmpty()) {
            return typeArgumentsFromSupertype;
        }
        // extra easy if all the type args don't contain any type parameters
        if (!containsTypeParameters(typeArgumentsFromSupertype)) {
            return typeArgumentsFromSupertype;
        }

        // this can't fail since we got a result
        ClassInfo superType = fetchFromIndex(appliedType.name(), index);

        // if our supertype has no type parameters, we don't need any mapping
        if (superType.typeParameters().isEmpty()) {
            return typeArgumentsFromSupertype;
        }

        // figure out which arguments we passed to the supertype
        List<Type> appliedArguments;

        // we passed them explicitely
        if (appliedType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            appliedArguments = appliedType.asParameterizedType().arguments();
        } else {
            // raw supertype: use bounds
            appliedArguments = new ArrayList<>(superType.typeParameters().size());
            for (TypeVariable typeVariable : superType.typeParameters()) {
                if (!typeVariable.bounds().isEmpty()) {
                    appliedArguments.add(typeVariable.bounds().get(0));
                } else {
                    appliedArguments.add(ClassType.create(DotNames.OBJECT, Type.Kind.CLASS));
                }
            }
        }

        // it's a problem if we got different arguments to the parameters declared
        if (appliedArguments.size() != superType.typeParameters().size()) {
            throw new IllegalArgumentException("Our supertype instance " + appliedType
                    + " does not match supertype declared arguments: " + superType.typeParameters());
        }
        // build the mapping
        Map<String, Type> mapping = new HashMap<>();
        for (int i = 0; i < superType.typeParameters().size(); i++) {
            TypeVariable typeParameter = superType.typeParameters().get(i);
            mapping.put(typeParameter.identifier(), appliedArguments.get(i));
        }
        // and map
        return mapGenerics(typeArgumentsFromSupertype, mapping);
    }

    private static boolean containsTypeParameters(List<Type> typeArgumentsFromSupertype) {
        for (Type type : typeArgumentsFromSupertype) {
            if (containsTypeParameters(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTypeParameters(Type type) {
        switch (type.kind()) {
            case ARRAY:
                return containsTypeParameters(type.asArrayType().constituent());
            case PARAMETERIZED_TYPE:
                ParameterizedType parameterizedType = type.asParameterizedType();
                if (parameterizedType.owner() != null
                        && containsTypeParameters(parameterizedType.owner()))
                    return true;
                return containsTypeParameters(parameterizedType.arguments());
            case TYPE_VARIABLE:
                return true;
            default:
                return false;
        }
    }

    private static List<Type> mapGenerics(List<Type> types, Map<String, Type> mapping) {
        List<Type> ret = new ArrayList<>(types.size());
        for (Type type : types) {
            ret.add(mapGenerics(type, mapping));
        }
        return ret;
    }

    private static Type mapGenerics(Type type, Map<String, Type> mapping) {
        switch (type.kind()) {
            case ARRAY:
                ArrayType arrayType = type.asArrayType();
                return ArrayType.create(mapGenerics(arrayType.constituent(), mapping), arrayType.dimensions());
            case CLASS:
                return type;
            case PARAMETERIZED_TYPE:
                ParameterizedType parameterizedType = type.asParameterizedType();
                Type owner = null;
                if (parameterizedType.owner() != null) {
                    owner = mapGenerics(parameterizedType.owner(), mapping);
                }
                return ParameterizedType.create(parameterizedType.name(),
                        mapGenerics(parameterizedType.arguments(), mapping).toArray(new Type[0]), owner);
            case TYPE_VARIABLE:
                Type ret = mapping.get(type.asTypeVariable().identifier());
                if (ret == null) {
                    throw new IllegalArgumentException("Missing type argument mapping for " + type);
                }
                return ret;
            default:
                throw new IllegalArgumentException("Illegal type in hierarchy: " + type);
        }
    }

    private static ClassInfo fetchFromIndex(DotName dotName, IndexView index) {
        final ClassInfo classInfo = index.getClassByName(dotName);
        if (classInfo == null) {
            throw new IllegalArgumentException("Class " + dotName + " was not found in the index");
        }
        return classInfo;
    }

}
