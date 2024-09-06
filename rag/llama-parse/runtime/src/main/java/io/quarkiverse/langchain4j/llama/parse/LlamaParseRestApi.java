package io.quarkiverse.langchain4j.llama.parse;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface LlamaParseRestApi {

    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    LlamaParseUploadResponse upload(LlamaParseUploadRequest llamaParseUploadRequest);

    @Path("/job/{job_id}/result/json")
    Response jobJsonResult(String job_id);

    @Path("/job/{job_id}/result/markdown")
    Response  jobMarkdownResult(String job_id);
}
