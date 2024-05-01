package io.quarkiverse.langchain4j.sample;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRepository implements PanacheRepository<Customer> {

    /*
     * Transaction limit for the customer.
     */
    public int getTransactionLimit(String customerName, String customerEmail) {
        Customer customer = find("name = ?1 and email = ?2", customerName, customerEmail).firstResult();
        if (customer == null) {
            throw new MissingCustomerException();
        }
        return customer.transactionLimit;
    }
}
