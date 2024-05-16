# JBang sample aka getting an AI-powered bot in 20 lines of Java

To run the sample, you need JBang installed. If you don't have it, choose
one of the installation options from the [JBang
website](https://www.jbang.dev/download/).

You also have to set your OpenAI API key:

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, run the example with:

```
jbang jokes.java
```

or just 

```
./jokes.java
```

The result should be something like:

```
Why couldn't the bicycle find its way home?

Because it lost its bearings!

```

Explanation:  The code contains a simple picocli command-line application.
The application injects the ChatLanguageModel provided by the Quarkus Langchain4j
extension and just request a joke by using `tell me a joke` as a prompt.
