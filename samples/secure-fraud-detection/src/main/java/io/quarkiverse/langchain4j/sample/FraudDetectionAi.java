package io.quarkiverse.langchain4j.sample;

import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.Timeout;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = FraudDetectionRetrievalAugmentor.class)
public interface FraudDetectionAi {

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
}
