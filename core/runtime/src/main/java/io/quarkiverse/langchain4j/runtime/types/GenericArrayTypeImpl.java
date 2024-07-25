package io.quarkiverse.langchain4j.runtime.types;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

/**
 * @author Marko Luksa
 * @author Jozef Hartinger
 */
class GenericArrayTypeImpl implements GenericArrayType {

    private final Type genericComponentType;

    public GenericArrayTypeImpl(Type genericComponentType) {
        this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
        return genericComponentType;
    }

    @Override
    public int hashCode() {
        return ((genericComponentType == null) ? 0 : genericComponentType.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GenericArrayType) {
            GenericArrayType that = (GenericArrayType) obj;
            if (genericComponentType == null) {
                return that.getGenericComponentType() == null;
            } else {
                return genericComponentType.equals(that.getGenericComponentType());
            }
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return genericComponentType.toString() + "[]";
    }
}
