package org.acme.example.gemini.aiservices;

import java.util.Iterator;

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
    @Path("v1beta/models/gemini-1.5-flash:generateContent")
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
        for (Iterator<Object> it = contents.iterator(); it.hasNext();) {
            JsonObject role = (JsonObject) it.next();
            if (role.toString().contains("functionResponse")) {
                JsonArray parts = role.getJsonArray("parts");
                JsonObject part = parts.getJsonObject(0);
                JsonObject functionResponse = part.getJsonObject("functionResponse");
                return functionResponse.getJsonObject("response").getString("content");
            }
        }
        return null;
    }

}
