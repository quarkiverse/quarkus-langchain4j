package org.acme.example.openai;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class TestUtils {
    private static final String MOCK_ANSWER = "MockGPT";

    @ConfigProperty(name = "openai.key")
    static String key;

    static {
        key = ConfigProvider.getConfig().getValue("openai.key", String.class);
    }

    private static boolean useMock() {
        return key.equalsIgnoreCase("ADD_A_TOKEN");
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
