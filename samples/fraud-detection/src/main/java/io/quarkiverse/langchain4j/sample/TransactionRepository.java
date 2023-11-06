package io.quarkiverse.langchain4j.sample;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

@ApplicationScoped
public class TransactionRepository implements PanacheRepository<Transaction> {

    @Tool("Get the transaction for a given customer for the last 15 minutes")
    public List<Transaction> getTransactionsForCustomer(long customerId) {
        return find("customerId = ?1 and time > ?2", customerId, LocalDateTime.now().minusMinutes(15)).list();
    }

    public double getAmountForCustomer(long customerId) {
        return find("customerId = ?1 and time > ?2", customerId, LocalDateTime.now().minusMinutes(15))
                .stream().mapToDouble(t -> t.amount).sum();
    }

    @Tool("Get the city for a given transaction id")
    public String getCityForTransaction(long transactionId) {
        return find("id = ?1", transactionId).firstResult().city;
    }

}
