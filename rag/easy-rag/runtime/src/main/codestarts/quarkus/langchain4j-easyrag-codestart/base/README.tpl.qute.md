### LangChain4j Easy RAG

This code is a very basic sample service to start developing with Quarkus LangChain4j using Easy RAG.

{#if input.selected-extensions-ga.contains('io.quarkiverse.langchain4j:quarkus-langchain4j-openai')}
This code is set up to use OpenAI as the LLM, thus you need to set the `QUARKUS_LANGCHAIN4J_OPENAI_API_KEY` environment variable to your OpenAI API key.
{#else}
You have to add an extension that provides an embedding model. For that, you can choose from the plethora of extensions like quarkus-langchain4j-openai, quarkus-langchain4j-ollama, or import an in-process embedding model - these have the advantage of not having to send data over the wire.
{/if}

In `./easy-rag-catalog/` you can find a set of example documents that will be used to create the RAG index which the bot (`src/main/java/org/acme/Bot.java`) will ingest.

On first run, the bot will create the RAG index and store it in `easy-rag-catalog.json` file and reuse it on subsequent runs.
This can be disabled by setting the `quarkus.langchain4j.easy-rag.reuse-embeddings.enabled` property to `false`.

Add it to a Rest endpoint:
```java
    @Inject
    Bot bot;
    
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String q) {
        return bot.chat(q);
    }
```

In a more complete example, you would have a web interface and use websockets that would provide more interactive experience, see [ChatBot Easy RAG Sample](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/samples/chatbot-easy-rag) for such an example.