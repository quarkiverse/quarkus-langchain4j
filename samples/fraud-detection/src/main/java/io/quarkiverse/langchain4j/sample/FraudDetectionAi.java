package io.quarkiverse.langchain4j.sample;

import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.Timeout;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = { TransactionRepository.class, CustomerRepository.class })
public interface FraudDetectionAi {

    @SystemMessage("""
            You are a bank account fraud detection AI. You have to detect frauds in transactions.
            """)
    @UserMessage("""
             Your task is to detect whether a fraud was committed for the customer {{customerId}}.

             To detect a fraud, perform the following actions:
             1 - Retrieve the name of the customer {{customerId}}
             2 - Retrieve the transactions for the customer {{customerId}} for the last 15 minutes.
             3 - Sum the amount of the all these transactions. Make sure the sum is correct.
             4 - If the amount is greater than 10000, a fraud is detected.

             Answer with a **single** JSON document containing:
             - the customer name in the 'customer-name' key
             - the computed sum in the 'total' key
             - the 'fraud' key set to a boolean value indicating if a fraud was detected
             - the 'transactions' key containing the list of transaction amounts
             - the 'explanation' key containing a explanation of your answer, especially how the sum is computed.
             - if there is a fraud, the 'email' key containing an email to the customer {{customerId}} to warn him about the fraud. The text must be formal and polite. It must ask the customer to contact the bank ASAP.

            Your response must be just the raw JSON document, without ```json, ``` or anything else.
             """)
    @Timeout(value = 2, unit = ChronoUnit.MINUTES)
    String detectAmountFraudForCustomer(long customerId);

    @SystemMessage("""
            You are a bank account fraud detection AI. You have to detect frauds in transactions.
            """)
    @UserMessage("""
            Detect frauds based on the distance between two transactions for the customer: {{customerId}}.

            To detect a fraud, perform the following actions:
            1- First, retrieve the name of the customer {{customerId}}
            2 - Retrieve the transactions for the customer {{customerId}} for the last 15 minutes.
            3 - Retrieve the city for each transaction.
            4 - Check if the distance between 2 cities is greater than 500km, if so, a fraud is detected.
            5 - If a fraud is detected, find the two transactions associated with these cities.

            Answer with a **single** JSON document containing:
            - the customer name in the 'customer-name' key
            - the amount of the first transaction in the 'first-amount' key
            - the amount of the second transaction in the 'second-amount' key
            - the city of the first transaction in the 'first-city' key
            - the city of the second transaction in the 'second-city' key
            - the 'fraud' key set to a boolean value indicating if a fraud was detected (so the distance is greater than 500 km)
            - the 'distance' key set to the distance between the two cities
            - the 'explanation' key containing a explanation of your answer.
            - the 'cities' key containing all the cities for the transactions for the customer {{customerId}} in the last 15 minutes.
            - if there is a fraud, the 'email' key containing an email to the customer {{customerId}} to warn him about the fraud. The text must be formal and polite. It must ask the customer to contact the bank ASAP.

            Your response must be just the raw JSON document, without ```json, ``` or anything else.

            """)
    @Timeout(value = 2, unit = ChronoUnit.MINUTES)
    String detectDistanceFraudForCustomer(long customerId);

}
