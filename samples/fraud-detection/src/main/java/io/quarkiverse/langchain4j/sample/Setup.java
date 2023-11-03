package io.quarkiverse.langchain4j.sample;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class Setup {

    public static List<String> CITIES = List.of("Paris", "Lyon", "Marseille", "Bordeaux", "Toulouse", "Nantes", "Brest",
            "Clermont-Ferrand", "La Rochelle", "Lille", "Metz", "Strasbourg", "Nancy", "Valence", "Avignon", "Montpellier",
            "Nime", "Arles", "Nice", "Cannes");

    public static String getARandomCity() {
        return CITIES.get(new Random().nextInt(CITIES.size()));
    }

    @Transactional
    public void init(@Observes StartupEvent ev, CustomerRepository customers, TransactionRepository transactions) {
        Random random = new Random();
        if (customers.count() == 0) {
            var customer1 = new Customer();
            customer1.name = "Emily Johnson";
            customer1.email = "emily.johnson@mail.com";
            customers.persist(customer1);

            var customer2 = new Customer();
            customer2.name = "Michael Anderson";
            customer2.email = "michael.anderson@mail.com";
            customers.persist(customer2);

            var customer3 = new Customer();
            customer3.name = "Olivia Williams";
            customer3.email = "olivia.williams@mail.com";
            customers.persist(customer3);
        }

        transactions.deleteAll(); // Delete all transactions
        for (int i = 0; i < 50; i++) {
            var transaction = new Transaction();
            // Get a random customer
            var idx = random.nextInt((int) customers.count());
            transaction.customerId = customers.findAll().page(idx, 1).firstResult().id;
            transaction.amount = random.nextInt(1000) + 1;
            transaction.time = LocalDateTime.now().minusMinutes(random.nextInt(20));
            transaction.city = getARandomCity();
            transactions.persist(transaction);
        }

        for (Customer customer : customers.listAll()) {
            System.out.println("Customer: " + customer.name + " - " + customer.id);
        }
    }
}
