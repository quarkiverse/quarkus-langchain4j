package io.quarkiverse.langchain4j.runtime;

import java.util.concurrent.Callable;

import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;

public class VertxUtil {

    public static void runOutEventLoop(Runnable runnable) {
        if (Context.isOnEventLoopThread()) {
            Context executionContext = VertxContext.getOrCreateDuplicatedContext();
            if (executionContext != null) {
                executionContext.executeBlocking(new Callable<Object>() {
                    @Override
                    public Object call() {
                        runnable.run();
                        return null;
                    }
                });
            } else {
                Infrastructure.getDefaultWorkerPool().execute(runnable);
            }
        } else {
            runnable.run();
        }
    }
}
