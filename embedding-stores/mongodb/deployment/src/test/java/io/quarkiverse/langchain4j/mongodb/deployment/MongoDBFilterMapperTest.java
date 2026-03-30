package io.quarkiverse.langchain4j.mongodb.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

import com.mongodb.client.model.Filters;

import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.quarkiverse.langchain4j.mongodb.MongoDBFilterMapper;

public class MongoDBFilterMapperTest {
    private static final String METADATA_FIELD = "metadata";
    private final MongoDBFilterMapper mapper = new MongoDBFilterMapper(METADATA_FIELD);

    @Test
    public void testEqual() {
        IsEqualTo predicate = new IsEqualTo("field", 3);
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.eq("metadata.field", 3).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testNotEqual() {
        IsNotEqualTo predicate = new IsNotEqualTo("field", 3);
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.ne("metadata.field", 3).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testGreaterThan() {
        IsGreaterThan predicate = new IsGreaterThan("field", 5);
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.gt("metadata.field", 5).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testGreaterThanOrEqual() {
        IsGreaterThanOrEqualTo predicate = new IsGreaterThanOrEqualTo("field", 5);
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.gte("metadata.field", 5).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testLessThan() {
        IsLessThan predicate = new IsLessThan("field", 10);
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.lt("metadata.field", 10).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testLessThanOrEqual() {
        IsLessThanOrEqualTo predicate = new IsLessThanOrEqualTo("field", 10);
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.lte("metadata.field", 10).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testIsIn() {
        IsIn predicate = new IsIn("field", Arrays.asList(1, 2, 3));
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.in("metadata.field", Arrays.asList(1, 2, 3)).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testIsNotIn() {
        IsNotIn predicate = new IsNotIn("field", Arrays.asList(1, 2, 3));
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.nin("metadata.field", Arrays.asList(1, 2, 3)).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testContainsString() {
        ContainsString predicate = new ContainsString("field", "test");
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        // MongoDB regex for contains with case-insensitive flag
        assertEquals(Filters.regex("metadata.field", ".*\\Qtest\\E.*", "i").toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testAnd() {
        And predicate = new And(new IsEqualTo("field", 3), new IsEqualTo("field2", 5));
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.and(
                Filters.eq("metadata.field", 3),
                Filters.eq("metadata.field2", 5)).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testOr() {
        Or predicate = new Or(new IsEqualTo("field", 3), new IsEqualTo("field2", 5));
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.or(
                Filters.eq("metadata.field", 3),
                Filters.eq("metadata.field2", 5)).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testNot() {
        Not predicate = new Not(new IsEqualTo("field", 3));
        Bson result = mapper.map(predicate);
        assertNotNull(result);
        assertEquals(Filters.not(Filters.eq("metadata.field", 3)).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }

    @Test
    public void testComplexFilter() {
        // (field = 3 AND field2 > 5) OR field3 < 10
        And andPredicate = new And(new IsEqualTo("field", 3), new IsGreaterThan("field2", 5));
        Or orPredicate = new Or(andPredicate, new IsLessThan("field3", 10));
        Bson result = mapper.map(orPredicate);
        assertNotNull(result);
        assertEquals(Filters.or(
                Filters.and(
                        Filters.eq("metadata.field", 3),
                        Filters.gt("metadata.field2", 5)),
                Filters.lt("metadata.field3", 10)).toBsonDocument().toJson(),
                result.toBsonDocument().toJson());
    }
}
