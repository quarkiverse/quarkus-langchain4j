# Chat application that shows Chat Scopes in action

This example shows chat scopes in action and uses the local async client to invoke on chat routes.
It is a simple travel agent terminal-based chatbot 
that can book flight and hotel reservations.  The example illustrates how
to invoke on a chat route, have nested conversations with your LLM using differing prompts,
and how to send events to the terminal client so it can render different things for the user.

## Running the example

A prerequisite to running this example is to provide your OpenAI API key.

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, simply build and run the code:

```
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

## Using the example

* Enter "help" in chat input to get what actions you can perform
* Enter "book a flight" to book a flight and enter in information for it.  LLM should ask you to fill in some information
* Enter "book a hotel" to make a hotel reservation.
* When finished with either, make the initial query more complex: i.e. "Book a flight to Dublin on June 10th".


