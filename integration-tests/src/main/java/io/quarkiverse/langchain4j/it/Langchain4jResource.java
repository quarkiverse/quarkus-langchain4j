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
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.ChatCompletionRequest;
import io.quarkiverse.langchain4j.ChatMessage;
import io.quarkiverse.langchain4j.OpenAiClient;
import io.smallrye.mutiny.Uni;

@Path("/langchain4j")
@ApplicationScoped
public class Langchain4jResource {

    private final ChatLanguageModel chatLanguageModel;
    private final OpenAiClient openAiClient;

    public Langchain4jResource(ChatLanguageModel chatLanguageModel, @RestClient OpenAiClient openAiClient) {
        this.chatLanguageModel = chatLanguageModel;
        this.openAiClient = openAiClient;
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
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-3.5-turbo");
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("When was the nobel prize for economics first awarded?");
        message.setName("testing");
        request.setMessages(List.of(message));
        request.setLogitBias(Collections.emptyMap());
        request.setMaxTokens(100);
        request.setUser("testing");
        request.setPresencePenalty(0d);
        request.setFrequencyPenalty(0d);
        return openAiClient.createChatCompletion(request).map(r -> r.getChoices().get(0).getMessage().getContent());
    }

}
