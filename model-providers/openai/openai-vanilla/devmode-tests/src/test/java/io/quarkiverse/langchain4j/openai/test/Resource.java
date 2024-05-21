package io.quarkiverse.langchain4j.openai.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkus.qute.TemplateGlobal;

@Path("/test")
public class Resource {

    private final Service service;

    public Resource(Service service) {
        this.service = service;
    }

    @GET
    public String hello() {
        return service.findTalks("java");
    }

    @TemplateGlobal
    public static class ApplicationGlobals {

        public static boolean displayNewSpeakers() {
            return Configuration.displayNewSpeakers();
        }
    }
}
