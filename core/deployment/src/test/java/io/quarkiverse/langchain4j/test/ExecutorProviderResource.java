package io.quarkiverse.langchain4j.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.context.ManagedExecutor;

import dev.langchain4j.spi.ExecutorProvider;

@Path("/executor")
public class ExecutorProviderResource {

    @Inject
    ManagedExecutor managedExecutor;

    @GET
    public String execute() throws Exception {
        var executor = ExecutorProvider.get().executor();
        return CompletableFuture.supplyAsync(() -> "before:" + (executor == managedExecutor), executor)
                .get(10, TimeUnit.SECONDS);
    }
}
