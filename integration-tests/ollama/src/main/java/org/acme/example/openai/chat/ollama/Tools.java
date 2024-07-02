package org.acme.example.openai.chat.ollama;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import dev.langchain4j.agent.tool.P;
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

        @Tool("Calculates the sum of two numbers")
        int add(int a, int b) {
            return a + b;
        }

        @Tool("Calculates the square root of a number")
        double sqrt(int x) {
            return Math.sqrt(x);
        }
    }

    @Singleton
    @SuppressWarnings("unused")
    public static class ExpenseService {
        @Tool("Get expenses for a given condominium, from date and to date.")
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
    public static class EmailService {
        @Tool("send the given content by email")
        @SuppressWarnings("unused")
        public void sendAnEmail(@P("Content to send") String content) {
            System.out.printf("""
                    ***
                    ***   Tool sendAnEmail has been executed successfully!
                    ***   Content: %s
                    """, content);
        }
    }
}
