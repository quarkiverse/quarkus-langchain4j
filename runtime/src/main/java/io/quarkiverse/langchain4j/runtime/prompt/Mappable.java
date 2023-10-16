package io.quarkiverse.langchain4j.runtime.prompt;

import java.util.Map;

public interface Mappable {

    Map<String, Object> obtainFieldValuesMap();
}
