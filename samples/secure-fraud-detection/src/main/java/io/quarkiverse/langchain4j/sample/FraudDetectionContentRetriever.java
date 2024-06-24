package io.quarkiverse.langchain4j.sample;

import java.time.ZoneOffset;
import java.util.List;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FraudDetectionContentRetriever implements ContentRetriever {
    private static final Logger log = Logger.getLogger(FraudDetectionContentRetriever.class);

    @Inject
    TransactionRepository transactionRepository;

    @Inject
    CustomerRepository customerRepository;

    @Inject
    @IdToken
    JsonWebToken idToken;
    
    @Override
    @Authenticated
    public List<Content> retrieve(Query query) {
        log.infof("Use customer name %s and email %s to retrieve content", idToken.getName(),
                idToken.getClaim(Claims.email));

        int transactionLimit = customerRepository.getTransactionLimit(idToken.getName(),
                idToken.getClaim(Claims.email));

        List<Transaction> transactions = transactionRepository.getTransactionsForCustomer(idToken.getName(),
                idToken.getClaim(Claims.email));

        JsonArray jsonTransactions = new JsonArray();
        for (Transaction t : transactions) {
            jsonTransactions.add(JsonObject.of("customer-name", t.customerName, "customer-email", t.customerEmail,
                    "transaction-amount", t.amount, "transaction-city", t.city,
                    "transaction-time-in-seconds-from-the-epoch", t.time.toEpochSecond(ZoneOffset.UTC)));
        }

        JsonObject json = JsonObject.of("transaction-limit", transactionLimit, "transactions", jsonTransactions);
        return List.of(Content.from(json.toString()));
    }
}
