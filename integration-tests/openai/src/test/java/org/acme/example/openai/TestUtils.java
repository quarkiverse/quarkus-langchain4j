package org.acme.example.openai;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class TestUtils {
    private static final String MOCK_ANSWER = "MockGPT";

    static boolean mocked = false;

    static {
        ConfigProvider.getConfig()
                .getOptionalValue("quarkus.langchain4j.openai.base-url", String.class)
                .ifPresent(s -> mocked = s.contains("mock"));
    }

    private static boolean useMock() {
        return mocked;
    }

    // required for @EnabledIf annotations
    public static boolean usesLLM() {
        return !mocked;
    }

    public static Matcher<String> containsStringOrMock(String... expected) {
        if (useMock()) {
            return Matchers.containsString(MOCK_ANSWER);
        } else {
            // due to peculiarities of java generics, anyOf method would not accept List<Matcher<String>>
            List<Matcher<? super String>> possibilities = new ArrayList<>(expected.length);
            for (String value : expected) {
                possibilities.add(Matchers.containsStringIgnoringCase(value));
            }
            return Matchers.anyOf(possibilities);
        }
    }
}
