package io.quarkiverse.langchain4j.ollama.tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;

public class Tools {

    @Singleton
    @SuppressWarnings("unused")
    public static class Calculator {
        @Tool("Calculates the length of a string")
        int stringLength(String s) {
            return s.length();
        }

        String stringLengthStr(String s) {
            return String.format("The length of the word %s is %d", s, s.length());
        }

        @Tool("Calculates the sum of two numbers")
        int add(int a, int b) {
            return a + b;
        }

        String addStr(int a, int b) {
            return String.format("The sum of %s and %s is %d", a, b, a + b);
        }

        @Tool("Calculates the square root of a number")
        double sqrt(int x) {
            return Math.sqrt(x);
        }

        String sqrtStr(int x) {
            return String.format("The square root of %s is %f", x, Math.sqrt(x));
        }
    }

    @Singleton
    @SuppressWarnings("unused")
    static class ExpenseService {
        @Tool("useful for when you need to lookup condominium expenses for given dates.")
        public String getExpenses(String condominium, String fromDate, String toDate) {
            String result = String.format("""
                    The Expenses for %s from %s to %s are:
                        - Expense hp12: 2800e
                        - Expense 2: 15000e
                    """, condominium, fromDate, toDate);
            Log.infof(result);
            return result;
        }
    }

    @ApplicationScoped
    static class EmailService {
        @Tool("send the given content by email")
        @SuppressWarnings("unused")
        public void sendAnEmail(String content) {
            Log.info("Tool sendAnEmail has been executed successfully!");
        }
    }
}
