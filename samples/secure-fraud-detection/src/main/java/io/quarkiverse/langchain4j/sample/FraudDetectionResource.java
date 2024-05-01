package io.quarkiverse.langchain4j.sample;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/fraud")
@Authenticated
public class FraudDetectionResource {

    private final FraudDetectionAi service;

    public FraudDetectionResource(FraudDetectionAi service) {
        this.service = service;
    }

    @GET
    @Path("/amount")
    public String detectBaseOnAmount() {
        try {
            return service.detectAmountFraudForCustomer();
        } catch (RuntimeException ex) {
            throw (ex.getCause() instanceof MissingCustomerException) ? (MissingCustomerException) ex.getCause() : ex;
        }
    }
}
