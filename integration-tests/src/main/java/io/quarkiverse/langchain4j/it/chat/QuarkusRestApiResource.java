/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.quarkiverse.langchain4j.it.chat;

import static io.quarkiverse.langchain4j.it.MessageUtil.createRequest;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.chat.Message;
import io.quarkiverse.langchain4j.QuarkusRestApi;
import io.quarkiverse.langchain4j.runtime.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.OpenAi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("/restApi")
@ApplicationScoped
public class QuarkusRestApiResource {

    private final QuarkusRestApi restApi;
    private final String token;

    public QuarkusRestApiResource(LangChain4jRuntimeConfig runtimeConfig)
            throws URISyntaxException {
        OpenAi openAi = runtimeConfig.chatModel().openAi();
        this.restApi = QuarkusRestClientBuilder.newBuilder()
                .baseUri(new URI(openAi.baseUrl().orElse("https://api.openai.com/v1/")))
                .build(QuarkusRestApi.class);
        this.token = openAi.apiKey().get();

    }

    @GET
    @Path("sync")
    public String sync() {
        return restApi.blockingCreateChatCompletion(
                createRequest("Write a short 1 paragraph funny poem about segmentation fault"), token).content();
    }

    @GET
    @Path("async")
    public Uni<String> async() {
        return restApi.createChatCompletion(createRequest("Write a short 1 paragraph funny poem about Unicode"), token)
                .map(ChatCompletionResponse::content);
    }

    @GET
    @Path("streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streaming() {
        return restApi.streamingCreateChatCompletion(
                createRequest("Write a short 1 paragraph funny poem about Enterprise Java"), token)
                .map(r -> {
                    if (r.choices() != null) {
                        if (r.choices().size() == 1) {
                            ChatCompletionChoice choice = r.choices().get(0);
                            Delta delta = choice.delta();
                            if (delta != null) {
                                if (delta.content() != null) {
                                    return delta.content();
                                }
                            } else { // normally this is not needed but mock APIs don't really work with the streaming response
                                Message message = choice.message();
                                if (message != null) {
                                    return message.content();
                                }
                            }
                        }
                    }
                    return null;
                });
    }

}
