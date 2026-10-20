package io.quarkiverse.langchain4j.openai.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.qute.TemplateGlobal;

@Path("/test")
public class Resource {

    private final Service service;
    private final ServiceWithResource serviceWithResource;

    public Resource(Service service, ServiceWithResource serviceWithResource) {
        this.service = service;
        this.serviceWithResource = serviceWithResource;
    }

    @GET
    public String hello() {
        return service.findTalks("java");
    }

    @GET
    @Path("with-resource")
    public String withResource() {
        return serviceWithResource.findTalks("java");
    }

    // needed because the dev mode test does not have access to this because of ClassLoaders
    @GET
    @Path("url")
    public String mockUrl() {
        return ConfigProvider.getConfig().getValue("quarkus.langchain4j.mock-api.url", String.class);
    }

    @TemplateGlobal
    public static class ApplicationGlobals {

        public static boolean displayNewSpeakers() {
            return Configuration.displayNewSpeakers();
        }
    }
}
