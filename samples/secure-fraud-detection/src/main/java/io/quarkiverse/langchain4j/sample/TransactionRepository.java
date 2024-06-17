package io.quarkiverse.langchain4j.sample;

import java.time.LocalDateTime;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionRepository implements PanacheRepository<Transaction> {

    /*
     * List of transactions during the last 30 minutes.
     */
    public List<Transaction> getTransactionsForCustomer(String customerName, String customerEmail) {
        return find("customerName = ?1 and customerEmail = ?2 and time > ?3", customerName, customerEmail,
                LocalDateTime.now().minusMinutes(30)).list();
    }
}
