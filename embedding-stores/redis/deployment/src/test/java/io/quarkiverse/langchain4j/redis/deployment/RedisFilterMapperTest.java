package io.quarkiverse.langchain4j.redis.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
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

    @Test
    public void testKeyCannotInjectQuerySyntax() {
        // unescaped, this would select "tenant_id >= 0 OR public == 42" instead of a single field
        IsEqualTo predicate = new IsEqualTo("tenant_id:[0 inf] | @public", 42);
        assertEquals("(@tenant_id\\:\\[0\\ inf\\]\\ \\|\\ \\@public:[42 42])", mapper.map(predicate));
    }

    @Test
    public void testKeyIsEscapedForEveryOperator() {
        String key = "a | @b";
        assertEquals("(-@a\\ \\|\\ \\@b:[1 1])", mapper.map(new IsNotEqualTo(key, 1)));
        assertEquals("(@a\\ \\|\\ \\@b:[(1 inf])", mapper.map(new IsGreaterThan(key, 1)));
        assertEquals("(@a\\ \\|\\ \\@b:[1 inf])", mapper.map(new IsGreaterThanOrEqualTo(key, 1)));
        assertEquals("(@a\\ \\|\\ \\@b:[-inf (1])", mapper.map(new IsLessThan(key, 1)));
        assertEquals("(@a\\ \\|\\ \\@b:[-inf 1])", mapper.map(new IsLessThanOrEqualTo(key, 1)));
    }

    @Test
    public void testKeyWithSpecialCharacters() {
        assertEquals("(@my\\-field:[3 3])", mapper.map(new IsEqualTo("my-field", 3)));
        // the escape character itself must be escaped, or it would swallow the ':' that follows the field
        assertEquals("(@field\\\\:[3 3])", mapper.map(new IsEqualTo("field\\", 3)));
    }

    @Test
    public void testKeyWithUnsupportedCharacters() {
        // neither can be escaped, RediSearch would read both keys as "public"
        IsEqualTo controlCharacter = new IsEqualTo("public\u0001", 42);
        IsEqualTo unpairedSurrogate = new IsEqualTo("public\uD800", 42);
        assertThrows(IllegalArgumentException.class, () -> mapper.map(controlCharacter));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(unpairedSurrogate));
    }
}
