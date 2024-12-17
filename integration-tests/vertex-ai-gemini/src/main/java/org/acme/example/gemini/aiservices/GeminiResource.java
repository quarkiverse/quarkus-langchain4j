package org.acme.example.gemini.aiservices;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("gemini/")
public class GeminiResource {

    @POST
    @Path("v1/projects/my_google_project_id/locations/west-europe/publishers/google/models/gemini-pro:generateContent")
    @Produces("application/json")
    public String get() {
        return """
                 {
                   "candidates": [
                     {
                       "content": {
                         "role": "model",
                         "parts": [
                           {
                             "text": "Nice to meet you"
                           }
                         ]
                       },
                       "finishReason": "STOP",
                       "safetyRatings": [
                         {
                           "category": "HARM_CATEGORY_HATE_SPEECH",
                           "probability": "NEGLIGIBLE",
                           "probabilityScore": 0.044847902,
                           "severity": "HARM_SEVERITY_NEGLIGIBLE",
                           "severityScore": 0.05592617
                         },
                         {
                           "category": "HARM_CATEGORY_DANGEROUS_CONTENT",
                           "probability": "NEGLIGIBLE",
                           "probabilityScore": 0.18877223,
                           "severity": "HARM_SEVERITY_NEGLIGIBLE",
                           "severityScore": 0.027324531
                         },
                         {
                           "category": "HARM_CATEGORY_HARASSMENT",
                           "probability": "NEGLIGIBLE",
                           "probabilityScore": 0.15278918,
                           "severity": "HARM_SEVERITY_NEGLIGIBLE",
                           "severityScore": 0.045437217
                         },
                         {
                           "category": "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                           "probability": "NEGLIGIBLE",
                           "probabilityScore": 0.15869519,
                           "severity": "HARM_SEVERITY_NEGLIGIBLE",
                           "severityScore": 0.036838707
                         }
                       ]
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
