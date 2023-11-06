# Fraud Detection Demo

This demo showcases the implementation of a simple fraud detection system using LLMs (GPT-4 in this case).

## The Demo

### Setup

The demo is based on fictional random data generated when the application starts, which includes:

- 3 users
- 50 transactions
- For each transaction, a random amount between 1 and 1000 is generated and assigned to a random user. A random city is also assigned to each transaction.

The setup is defined in the [Setup.java](./src/main/java/io/quarkiverse/langchain4j/samples/Setup.java) class.

The users and transactions are stored in a PostgreSQL database. When running the demo in dev mode (recommended), the database is automatically created and populated.

### Tools

To enable fraud detection, we provide the LLM with access to customer and transaction data through two Panache repositories:

- [CustomerRepository.java](./src/main/java/io/quarkiverse/langchain4j/samples/CustomerRepository.java)
- [TransactionRepository.java](./src/main/java/io/quarkiverse/langchain4j/samples/TransactionRepository.java)

The following code snippet demonstrates how to help the LLM access the customer name from the ID:

```java
package io.quarkiverse.langchain4j.sample;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

@ApplicationScoped
public class CustomerRepository implements PanacheRepository<Customer> {

    @Tool("get the customer name for the given customerId")
    public String getCustomerName(long id) {
        return find("id", id).firstResult().name;
    }
}
```

### AI Service

This demo leverages the AI service abstraction, with the interaction between the LLM and the application handled through the AIService interface.

The interface uses specific annotations to define the LLM and the tools to be used:

```java
@RegisterAiService(chatMemorySupplier = AiConfig.MemoryProvider.class,
        tools = { TransactionRepository.class, CustomerRepository.class })
```

For each message, the prompt is engineered to help the LLM understand the context and answer the request:

```java
@SystemMessage("""
        You are a bank account fraud detection AI. You have to detect frauds in transactions.
        """)
@UserMessage("""
        Your task is to detect whether a fraud was committed for the customer {{customerId}}.

        To detect a fraud, perform the following actions:
        1. Retrieve the name of the customer {{customerId}}
        2. Retrieve the transactions for the customer {{customerId}} for the last 15 minutes.
        3. Sum the amount of all these transactions. Ensure the sum is correct.
        4. If the amount is greater than 10000, a fraud is detected.

        Answer with a **single** JSON document containing:
        - the customer name in the 'customer-name' key
        - the computed sum in the 'total' key
        - the 'fraud' key set to a boolean value indicating if a fraud was detected
        - the 'transactions' key containing the list of transaction amounts
        - the 'explanation' key containing an explanation of your answer, especially how the sum is computed.
        - if there is a fraud, the 'email' key containing an email to the customer {{customerId}} to warn him about the fraud. The text must be formal and polite. It must ask the customer to contact the bank ASAP.

       Your response must be just the JSON document, nothing else.
        """)
@Timeout(value = 2, unit = ChronoUnit.MINUTES)
String detectAmountFraudForCustomer(long customerId);
```

_Note:_ You can also use fault tolerance annotations in combination with the prompt annotations.

### Using the AI service

Once defined, you can inject the AI service as a regular bean, and use it:

```java
@Path("/fraud")
public class FraudDetectionResource {

    private final FraudDetectionAi service;
    private final TransactionRepository transactions;

    public FraudDetectionResource(FraudDetectionAi service, TransactionRepository transactions) {
        this.service = service;
        this.transactions = transactions;
    }

    @GET
    @Path("/distance")
    public String detectBasedOnDistance(@RestQuery long customerId) {
        return service.detectDistanceFraudForCustomer(customerId);
    }

    @GET
    @Path("/amount")
    public String detectBaseOnAmount(@RestQuery long customerId) {
        return service.detectAmountFraudForCustomer(customerId);
    }
    // ...
}
```

## Running the Demo

To run the demo, use the following commands:

```shell
> mvn quarkus:dev
```
Then, issue requests:

```shell
>  http ":8080/fraud/amount?customerId=1"
>  http ":8080/fraud/distance?customerId=1"
```