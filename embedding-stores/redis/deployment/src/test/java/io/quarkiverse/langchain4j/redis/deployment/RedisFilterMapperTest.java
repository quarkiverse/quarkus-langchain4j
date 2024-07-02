package io.quarkiverse.langchain4j.redis.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import io.quarkiverse.langchain4j.redis.runtime.RedisFilterMapper;

public class RedisFilterMapperTest {
    private RedisFilterMapper mapper = new RedisFilterMapper();

    // TODO: right now we only support filtering based on numeric fields

    @Test
    public void testEqual() {
        IsEqualTo predicate = new IsEqualTo("field", 3);
        assertEquals("(@field:[3 3])", mapper.map(predicate));
    }

    @Test
    public void testNotEqual() {
        IsNotEqualTo predicate = new IsNotEqualTo("field", 3);
        assertEquals("(-@field:[3 3])", mapper.map(predicate));
    }

    @Test
    public void testAnd() {
        And predicate = new And(new IsNotEqualTo("field", 3), new IsEqualTo("field2", 3));
        assertEquals("(-@field:[3 3] @field2:[3 3])", mapper.map(predicate));
    }
}
