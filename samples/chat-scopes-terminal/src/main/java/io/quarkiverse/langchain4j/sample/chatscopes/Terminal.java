package io.quarkiverse.langchain4j.sample.chatscopes;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes;
import io.quarkiverse.langchain4j.sample.chatscopes.Model.PushPlaceholder;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

@QuarkusMain
public class Terminal implements QuarkusApplication {
    public static final String BOLD_YELLOW = "\u001B[1;33m";
    // Bold Green escape sequence
    public static final String BOLD_GREEN = "\u001B[1;32m";
    // Reset escape sequence
    public static final String RESET = "\u001B[0m";
    public static final String DIM = "\u001B[2m";

    @Inject
    LocalChatRoutes.Client client;
    
    @Override
    public int run(String... args) throws Exception {
        Stack<String> placeholder = new Stack<>();
        placeholder.add("Book your vacation");
        
        LocalChatRoutes.SessionBuilder builder = client.builder()
                                                        .messageHandler(message -> System.out.println(message))
                                                        .thinkingHandler(thinking -> System.out.println(DIM + thinking + RESET))
                                                        .eventHandler(Model.PUSH_PLACEHOLDER_EVENT, (event) -> placeholder.push(((PushPlaceholder)event).placeholder()))
                                                        .eventHandler(Model.POP_PLACEHOLDER_EVENT, (event) -> placeholder.pop());

        LocalChatRoutes.Session session = builder.connect();

        System.out.println();
        System.out.println(BOLD_YELLOW + "Welcome to the Quarkus AI Travel Agent!" + RESET);
        System.out.println();
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        System.out.print(BOLD_GREEN + placeholder.peek() + RESET + ": ");    
        while (scanner.hasNextLine()) {
            String userMessage = scanner.nextLine().trim();
            if (!userMessage.isEmpty()) {
                System.out.println();
                session.chat(userMessage);
            }
            System.out.println();
            System.out.print(BOLD_GREEN + placeholder.peek() + RESET + ": ");
        }
        
        return 0;
    }

}
