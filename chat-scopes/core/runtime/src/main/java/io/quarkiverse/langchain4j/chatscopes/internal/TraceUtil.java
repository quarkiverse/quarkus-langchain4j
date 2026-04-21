package io.quarkiverse.langchain4j.chatscopes.internal;

public class TraceUtil {

    public static String caller(int level) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String method = stackTrace[3 + level].getMethodName();
        String className = stackTrace[3 + level].getClassName();
        return className + "." + method + "()";
    }

    public static String caller() {
        return caller(1);
    }

}
