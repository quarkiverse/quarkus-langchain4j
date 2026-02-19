package org.acme.example.gemini.aiservices;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.jboss.resteasy.reactive.RestQuery;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Path("gemini/")
public class GeminiResource {

    @POST
    @Path("models/gemini-2.5-flash:generateContent")
    @Produces("application/json")
    @Consumes("application/json")
    public String generateResponse(String generateRequest, @RestQuery String key) {
        if (generateRequest.contains("functionResponse")) {

            JsonObject json = new JsonObject(generateRequest);
            JsonArray contents = json.getJsonArray("contents");
            String toolRole = getToolResponse(contents);
            return """
                     {
                       "candidates": [
                         {
                           "content": {
                             "role": "model",
                             "parts": [
                               {
                                "text": %s
                               }
                             ]
                           },
                           "finishReason": "STOP"
                         }
                       ],
                       "usageMetadata": {
                         "promptTokenCount": 11,
                         "candidatesTokenCount": 37,
                         "totalTokenCount": 48
                       }
                     }
                    """.formatted(toolRole);
        } else {
            return """
                     {
                       "candidates": [
                         {
                           "content": {
                             "role": "model",
                             "parts": [
                               {
                                "functionCall": {
                                  "name": "duplicateContent",
                                  "args": {
                                    "content": "Nice to meet you"
                                  }
                                }
                               }
                             ]
                           },
                           "finishReason": "STOP"
                         }
                       ],
                       "usageMetadata": {
                         "promptTokenCount": 11,
                         "candidatesTokenCount": 37,
                         "totalTokenCount": 48
                       }
                     }
                    """;
        }
    }

    private String getToolResponse(JsonArray contents) {
        for (int i = 0; i < contents.size(); i++) {
            JsonObject content = contents.getJsonObject(i);

            // Check if this is a user role with parts
            if ("user".equals(content.getString("role", null))) {
                JsonArray parts = content.getJsonArray("parts");

                if (parts != null) {
                    for (int j = 0; j < parts.size(); j++) {
                        JsonObject part = parts.getJsonObject(j);

                        // Check if this part contains a functionResponse
                        JsonObject functionResponse = part.getJsonObject("functionResponse", null);

                        if (functionResponse != null) {
                            // Check if it's the duplicateContent function
                            if ("duplicateContent".equals(functionResponse.getString("name", null))) {
                                JsonObject responseObj = functionResponse.getJsonObject("response");

                                if (responseObj != null) {
                                    return responseObj.getString("response", null);
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

}
