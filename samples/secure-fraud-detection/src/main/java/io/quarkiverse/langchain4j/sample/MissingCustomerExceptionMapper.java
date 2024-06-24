package io.quarkiverse.langchain4j.sample;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MissingCustomerExceptionMapper implements ExceptionMapper<MissingCustomerException> {
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(MissingCustomerException ex) {
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("missingCustomer").build()).build();
    }
}
