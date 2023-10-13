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
package io.quarkiverse.langchain4j.it;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.Delta;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.OpenAiQuarkusClient;
import io.quarkiverse.langchain4j.OpenAiQuarkusRestApi;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("/langchain4j")
@ApplicationScoped
public class Langchain4jResource {

    private final ChatLanguageModel chatLanguageModel;
    private final OpenAiQuarkusRestApi openAiQuarkusRestApi;
    private final OpenAiQuarkusClient openAiQuarkusClient;

    public Langchain4jResource(ChatLanguageModel chatLanguageModel, @RestClient OpenAiQuarkusRestApi openAiQuarkusRestApi,
            OpenAiQuarkusClient openAiQuarkusClient) {
        this.chatLanguageModel = chatLanguageModel;
        this.openAiQuarkusRestApi = openAiQuarkusRestApi;
        this.openAiQuarkusClient = openAiQuarkusClient;
    }

    @GET
    public String hello() {
        return "Hello langchain4j";
    }

    @GET
    @Path("chat")
    public String chat() {
        return chatLanguageModel.generate("When was the nobel prize for economics first awarded?");
    }

    @GET
    @Path("client")
    public Uni<String> client() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .logitBias(Collections.emptyMap())
                .maxTokens(100)
                .user("testing")
                .presencePenalty(0d)
                .frequencyPenalty(0d)
                .addUserMessage("When was the nobel prize for economics first awarded?").build();
        return openAiQuarkusRestApi.createChatCompletion(request).map(r -> r.choices().get(0).message().content());
    }

    @GET
    @Path("streaming/client")
    public Multi<String> streamingClient() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .logitBias(Collections.emptyMap())
                .maxTokens(100)
                .user("testing")
                .presencePenalty(0d)
                .frequencyPenalty(0d)
                .addUserMessage("Write a short 1 paragraph funny poem about developers and null-pointers").build();
        return openAiQuarkusRestApi.streamingCreateChatCompletion(request)
                .filter(r -> {
                    if (r.choices() != null) {
                        if (r.choices().size() == 1) {
                            Delta delta = r.choices().get(0).delta();
                            if (delta != null) {
                                return delta.content() != null;
                            }
                        }
                    }
                    return false;
                })
                .map(r -> r.choices().get(0).delta().content())
                .filter(Objects::nonNull);
    }

    @GET
    @Path("partial/client")
    public String streamingOutput() throws Exception {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .logitBias(Collections.emptyMap())
                .maxTokens(100)
                .user("testing")
                .presencePenalty(0d)
                .frequencyPenalty(0d)
                .addUserMessage("Write a short 1 paragraph funny poem about javascript frameworks").build();

        CompletableFuture<String> cf = new CompletableFuture<>();
        StringBuilder sb = new StringBuilder();
        openAiQuarkusClient.chatCompletion(request)
                .onPartialResponse(r -> {
                    if (r.choices() != null) {
                        if (r.choices().size() == 1) {
                            Delta delta = r.choices().get(0).delta();
                            if (delta != null) {
                                if (delta.content() != null) {
                                    sb.append(delta.content());
                                }
                            }
                        }
                    }
                })
                .onComplete(() -> {
                    cf.complete(sb.toString());
                })
                .onError(cf::completeExceptionally)
                .execute();
        return cf.get();
    }

}
