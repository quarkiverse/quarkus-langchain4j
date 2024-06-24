# Secure Fraud Detection Demo

This demo showcases the implementation of a secure fraud detection system which is available only to users authenticated with Google.
It uses the `gpt-3.5-turbo` LLM, use `quarkus.langchain4j.openai.chat-model.model-name` property to select a different model.

## The Demo

### Setup

The demo requires that your Google account's full name and email are configured.
You can use system or env properties, see `Running the Demo` section below.

When the application starts, 5 transactions with random amounts between 1 and 1000 are generated for the registered user.
A random city is also assigned to each transaction.

The setup is defined in the [Setup.java](./src/main/java/io/quarkiverse/langchain4j/samples/Setup.java) class.

The registered user and transactions are stored in a PostgreSQL database. When running the demo in dev mode (recommended), the database is automatically created and populated.

### Content Retrieval

To enable fraud detection, we provide the LLM with access to the custom [FraudDetectionContentRetriever](./src/main/java/io/quarkiverse/langchain4j/samples/FraudDetectionContentRetriever.java) content retriever.

`FraudDetectionContentRetriever` is registered by [FraudDetectionRetrievalAugmentor](./src/main/java/io/quarkiverse/langchain4j/samples/FraudDetectionRetrievalAugmentor.java).

It can only be accessed securely and it retrieves transaction data for the currently authenticated user through two Panache repositories:

- [CustomerRepository.java](./src/main/java/io/quarkiverse/langchain4j/samples/CustomerRepository.java)
- [TransactionRepository.java](./src/main/java/io/quarkiverse/langchain4j/samples/TransactionRepository.java)

It extracts the authenticated user's name and email from an injected `JsonWebToken` ID token.

### AI Service

This demo leverages the AI service abstraction, with the interaction between the LLM and the application handled through the AIService interface.

The `io.quarkiverse.langchain4j.sample.FraudDetectionAi` interface uses specific annotations to define the LLM:

```java
@RegisterAiService(retrievalAugmentor = FraudDetectionRetrievalAugmentor.class)
```

For each message, the prompt is engineered to help the LLM understand the context and answer the request:

```java
    @SystemMessage("""
            You are a bank account fraud detection AI. You have to detect frauds in transactions.
            """)
    @UserMessage("""
            Your task is to detect whether a fraud was committed for the customer.

            Answer with a **single** JSON document containing:
            - the customer name in the 'customer-name' key
            - the transaction limit in the 'transaction-limit' key
            - the computed sum of all transactions committed during the last 15 minutes in the 'total' key
            - the 'fraud' key set to true if the computed sum of all transactions is greater than the transaction limit
            - the 'transactions' key containing an array of JSON objects. Each object must have transaction 'amount', 'city' and formatted 'time' keys.
            - the 'explanation' key containing an explanation of your answer.
            - the 'email' key containing the customer email if the fraud was detected. 
            
           Your response must be just the raw JSON document, without ```json, ``` or anything else. Do not use null JSON properties.
            """)
@Timeout(value = 2, unit = ChronoUnit.MINUTES)
String detectAmountFraudForCustomer();
```

_Note:_ You can also use fault tolerance annotations in combination with the prompt annotations.

### Using the AI service

Once defined, you can inject the AI service as a regular bean, and use it:

```java
package io.quarkiverse.langchain4j.sample;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/fraud")
@Authenticated
public class FraudDetectionResource {

    private final FraudDetectionAi service;

    public FraudDetectionResource(FraudDetectionAi service) {
        this.service = service;
    }

    @GET
    @Path("/amount")
    public String detectBaseOnAmount() {
    	return service.detectAmountFraudForCustomer();
    }
}
```

`FraudDetectionResource` can only be accessed by authenticated users.

## Google Authentication

This demo requires users to authenticate with Google.
All you need to do is to register an application with Google, follow steps listed in the [Quarkus Google](https://quarkus.io/guides/security-openid-connect-providers#google) section.
Name your Google application as `Quarkus LangChain4j AI`, and make sure an allowed callback URL is set to `http://localhost:8080/login`.
Google will generate a client id and secret, use them to set `quarkus.oidc.client-id` and `quarkus.oidc.credentials.secret` properties.

## Running the Demo

To run the demo, use the following command:

```shell
mvn quarkus:dev -Dname="Firstname Familyname" -Demail=someuser@gmail.com
```

Note, you should use double quotes to register your Google account's full name.

Then, access `http://localhost:8080`, login to Google, and follow a provided application link to check the fraud.

