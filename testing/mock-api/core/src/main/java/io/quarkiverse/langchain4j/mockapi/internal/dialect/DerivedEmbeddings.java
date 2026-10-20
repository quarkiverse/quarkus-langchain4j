package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;

/**
 * The vectors and scores a scripted reply gives for a request, including deterministic pseudo embeddings derived from
 * the input text.
 */
public final class DerivedEmbeddings {

    private static final double EQUAL_SCORE = 1.0;

    private DerivedEmbeddings() {
    }

    /**
     * @return a unit length vector of the given dimension, always the same for the same input
     */
    public static float[] vector(String input, int dimension) {
        Random random = new Random(input.hashCode());
        float[] vector = new float[dimension];
        double sumOfSquares = 0;
        for (int i = 0; i < dimension; i++) {
            vector[i] = random.nextFloat() * 2f - 1f;
            sumOfSquares += vector[i] * vector[i];
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    /**
     * One vector per input, in input order: the reply's explicit vectors (extra ones are ignored) or vectors derived
     * from the inputs.
     *
     * @throws UnrenderableReply when the reply cannot provide a vector for every input
     */
    public static List<float[]> vectors(EmbeddingReply reply, List<String> inputs) {
        if (reply.vectors() != null) {
            if (reply.vectors().size() < inputs.size()) {
                throw new UnrenderableReply("Mock has " + reply.vectors().size()
                        + " embedding vectors but the request has " + inputs.size() + " inputs");
            }
            return reply.vectors().subList(0, inputs.size());
        }
        if (reply.derivedDimension() != null) {
            return inputs.stream().map(i -> vector(i, reply.derivedDimension())).toList();
        }
        throw new UnrenderableReply("Embedding reply has neither vectors nor a derived dimension");
    }

    /**
     * One score per scored item, in item order: the reply's scores (extra ones are ignored), or {@code 1.0} for every
     * item for {@link ScoringReply#equalScores()}.
     *
     * @param items what the request scores, as named in the error message, for example {@code documents}
     * @throws UnrenderableReply when the reply has fewer scores than the request has items
     */
    public static List<Double> scores(ScoringReply reply, int n, String items) {
        if (reply.scores() == null) {
            return Collections.nCopies(n, EQUAL_SCORE);
        }
        if (reply.scores().size() < n) {
            throw new UnrenderableReply(
                    "Mock has " + reply.scores().size() + " scores but the request has " + n + " " + items);
        }
        return reply.scores().subList(0, n);
    }

    /**
     * Adds the vector's values to {@code values}.
     *
     * @return {@code values}
     */
    public static ArrayNode floats(ArrayNode values, float[] vector) {
        for (float f : vector) {
            values.add(f);
        }
        return values;
    }
}
