package io.quarkiverse.langchain4j.mockapi;

import java.util.List;

/**
 * A dialect-neutral view of a recorded Decisions API request.
 */
public interface DecisionRequestView {

    String modelName();

    String inputText();

    List<Question> questions();

    interface Question {

        String type();

        String name();

        String instructions();

        /**
         * The {@code value} of each choice, for choice questions.
         */
        List<String> choices();

        /**
         * The {@code label} of each level, for score questions.
         */
        List<String> levels();
    }
}
