package io.quarkiverse.langchain4j.openai.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.qute.TemplateGlobal;

@Path("/test")
public class Resource {

    private final Service service;
    private final ServiceWithResource serviceWithResource;
    private final Integer wiremockPort;

    public Resource(Service service, ServiceWithResource serviceWithResource,
            @ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
        this.service = service;
        this.serviceWithResource = serviceWithResource;
        this.wiremockPort = wiremockPort;
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
    @Path("wiremock")
    public Integer wiremockPort() {
        return wiremockPort;
    }

    @TemplateGlobal
    public static class ApplicationGlobals {

        public static boolean displayNewSpeakers() {
            return Configuration.displayNewSpeakers();
        }
    }
}
