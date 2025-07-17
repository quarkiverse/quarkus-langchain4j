// tag::head[]
package io.quarkiverse.langchain4j.samples.images;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("Extract and summarize text from the provided image.")
public interface ImageAiService {
    // end::head[]

    // tag::url[]
    @UserMessage("""
            Here is a menu image.
            Extract the list of items.
            """)
    String extractMenu(@ImageUrl String imageUrl); // <1>
    // end::url[]

    // tag::ocr[]
    @UserMessage("""
            Extract the content of this receipt.
            Identify the vendor, date, location and paid amount and currency (euros or USD).
            Make sure the paid amount includes VAT.
            For each information, add the line from the receipt where you found it.
            """)
    String extractReceiptData(Image image); // <1>
    // end::ocr[]
    // tag::head[]
}
// end::head[]