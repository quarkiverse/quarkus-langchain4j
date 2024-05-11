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

           var customer4 = new Customer();
            customer4.name = "Robin Banks";
            customer4.email = "robin.banks@mail.com";
            customers.persist(customer4);

           var customer5 = new Customer();
            customer5.name = "Miles Faux";
            customer5.email = "miles.faux@mail.com";
            customers.persist(customer5);
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

        // Ensure that the customer 4. Robin Banks has a transaction sum greater than 10000
        var transaction41 = new Transaction();
        transaction41.customerId = 4;
        transaction41.amount = 10001;
        transaction41.time = LocalDateTime.now();
        transaction41.city = getARandomCity();
        transactions.persist(transaction41);
        
        // Ensure that the customer 5. Miles Faux has traveled to cities with distance greater than 500km
        // So, let's go with Brest and Nice that are 1000km apart
        var transaction51 = new Transaction();
        transaction51.customerId = 5;
        transaction51.amount = 1000;
        transaction51.time = LocalDateTime.now();
        transaction51.city = "Brest";
        transactions.persist(transaction51);
        
        var transaction52 = new Transaction();
        transaction52.customerId = 5;
        transaction52.amount = 1000;
        transaction52.time = LocalDateTime.now();
        transaction52.city = "Nice";
        transactions.persist(transaction52);

        for (Customer customer : customers.listAll()) {
            if (customer.id == 4) {
                System.out.println("Customer: " + customer.name + " - " + customer.id + " has a transaction sum greater than 10000");
            } else if (customer.id == 5) {
                System.out.println("Customer: " + customer.name + " - " + customer.id + " has traveled to cities with distance greater than 500km");
            } else {
              System.out.println("Customer: " + customer.name + " - " + customer.id);
            }
        }
    }
}
