// tag::head[]
package io.quarkiverse.langchain4j.samples.images;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import dev.langchain4j.data.image.Image;

@Path("/endpoint")
public class Endpoint {

    @Inject
    ImageAiService imageAiService;

    // end::head[]
    // tag::url[]
    @GET
    @Path("/extract-menu")
    public String fromUrl(@QueryParam("u") String url) { // <1>
        return imageAiService.extractMenu(url);
    }
    // end::url[]

    // tag::ocr[]
    @GET
    @Path("/ocr-process")
    public String passingImage() throws IOException {
        byte[] bytes = Files.readAllBytes(java.nio.file.Path.of("IMG_3283.jpg"));
        String b64 = Base64.getEncoder().encodeToString(bytes);
        Image img = Image.builder()
                .base64Data(b64)
                .mimeType("image/jpeg")
                .build();

        return imageAiService.extractReceiptData(img);
    }
    // end::ocr[]

    // tag::head[]
}
// end::head[]
