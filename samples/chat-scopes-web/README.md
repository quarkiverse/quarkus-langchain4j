# Chat application that shows Chat Scopes in action

This example shows chat scopes in action via it's websocket extension.  It is a simple travel agent web chatbot 
that can book flight and hotel reservations.  The example illustrates how
to invoke on a chat route, have nested conversations with your LLM using differing prompts,
and how to send events to the web client so it can render different things for the user.

## Running the example

A prerequisite to running this example is to provide your OpenAI API key.

```
export QUARKUS_LANGCHAIN4J_OPENAI_API_KEY=<your-openai-api-key>
```

Then, simply run the project in Dev mode:

```
mvn quarkus:dev
```

## Using the example

Open your browser and navigate to http://localhost:8080.

* Enter "help" in chat input to get what actions you can perform
* Enter "book a flight" to book a flight and enter in information for it.  LLM should ask you to fill in some information
* Enter "book a hotel" to make a hotel reservation.
* When finished with either, make the initial query more complex: i.e. "Book a flight to Dublin on June 10th".


