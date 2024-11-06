package io.quarkiverse.langchain4j.test;

import java.util.List;

public class Lists {

    public static <T> T last(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }
}
