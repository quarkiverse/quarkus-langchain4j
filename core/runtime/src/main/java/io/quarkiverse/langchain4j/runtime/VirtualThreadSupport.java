package io.quarkiverse.langchain4j.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Small compatibility helper for virtual-thread detection on JDKs that compile at release 17 but run
 * on 21+. {@code Thread#isVirtual()} is not present in the Java 17 API, so a direct call does not
 * compile; at runtime the method is available whenever the JVM supports virtual threads.
 */
public final class VirtualThreadSupport {

    private static final MethodHandle IS_VIRTUAL;

    static {
        MethodHandle mh = null;
        try {
            mh = MethodHandles.publicLookup()
                    .findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            // JDK < 21: virtual threads are not supported at all, leave IS_VIRTUAL as null.
        }
        IS_VIRTUAL = mh;
    }

    private VirtualThreadSupport() {
    }

    public static boolean isVirtualThread(Thread thread) {
        if (IS_VIRTUAL == null) {
            return false;
        }
        try {
            return (boolean) IS_VIRTUAL.invokeExact(thread);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isCurrentThreadVirtual() {
        return isVirtualThread(Thread.currentThread());
    }
}
