package io.quarkiverse.langchain4j.sample;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class Setup {

    public static List<String> CITIES = List.of("Paris", "Lyon", "Marseille", "Bordeaux", "Toulouse", "Nantes", "Brest",
            "Clermont-Ferrand", "La Rochelle", "Lille", "Metz", "Strasbourg", "Nancy", "Valence", "Avignon",
            "Montpellier", "Nime", "Arles", "Nice", "Cannes");

    public static String getARandomCity() {
        return CITIES.get(new Random().nextInt(CITIES.size()));
    }

    @Inject
    CustomerConfig config;

    @Transactional
    public void init(@Observes StartupEvent ev, CustomerRepository customers, TransactionRepository transactions) {
        customers.deleteAll();
        Random random = new Random();

        var customer = new Customer();
        customer.name = config.name();
        customer.email = config.email();
        customer.transactionLimit = 1000;
        customers.persist(customer);

        transactions.deleteAll(); // Delete all transactions
        for (int i = 0; i < 5; i++) {
            var transaction = new Transaction();
            transaction.customerName = customer.name;
            transaction.customerEmail = customer.email;
            transaction.amount = random.nextInt(1000) + 1;
            transaction.time = LocalDateTime.now().minusMinutes(random.nextInt(20));
            transaction.city = getARandomCity();
            transactions.persist(transaction);
        }

        System.out.println("Customer: " + customer.name + " - " + customer.email);
    }
}
