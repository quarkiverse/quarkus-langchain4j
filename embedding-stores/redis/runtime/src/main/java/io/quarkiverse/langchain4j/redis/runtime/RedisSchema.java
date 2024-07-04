package io.quarkiverse.langchain4j.redis.runtime;

import java.util.List;

import io.quarkus.redis.datasource.search.CreateArgs;
import io.quarkus.redis.datasource.search.DistanceMetric;
import io.quarkus.redis.datasource.search.FieldOptions;
import io.quarkus.redis.datasource.search.FieldType;
import io.quarkus.redis.datasource.search.VectorAlgorithm;
import io.quarkus.redis.datasource.search.VectorType;

public class RedisSchema {

    private String indexName;
    private String prefix;
    private String vectorFieldName;
    private String scalarFieldName;
    private List<String> numericMetadataFields;
    private List<String> textualMetadataFields;
    private VectorAlgorithm vectorAlgorithm;
    private Long dimension;
    private DistanceMetric distanceMetric;
    private static final String JSON_PATH_PREFIX = "$.";

    public RedisSchema(String indexName,
            String prefix,
            String vectorFieldName,
            String scalarFieldName,
            List<String> numericMetadataFields,
            List<String> textualMetadataFields,
            VectorAlgorithm vectorAlgorithm,
            Long dimension,
            DistanceMetric distanceMetric) {
        this.indexName = indexName;
        this.prefix = prefix;
        this.vectorFieldName = vectorFieldName;
        this.scalarFieldName = scalarFieldName;
        this.numericMetadataFields = numericMetadataFields;
        this.textualMetadataFields = textualMetadataFields;
        this.vectorAlgorithm = vectorAlgorithm;
        this.dimension = dimension;
        this.distanceMetric = distanceMetric;
    }

    public String getIndexName() {
        return indexName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public String getScalarFieldName() {
        return scalarFieldName;
    }

    public List<String> getNumericMetadataFields() {
        return numericMetadataFields;
    }

    public List<String> getTextualMetadataFields() {
        return textualMetadataFields;
    }

    public VectorAlgorithm getVectorAlgorithm() {
        return vectorAlgorithm;
    }

    public Long getDimension() {
        return dimension;
    }

    public DistanceMetric getDistanceMetric() {
        return distanceMetric;
    }

    public void defineFields(CreateArgs args) {
        defineTextField(args);
        defineVectorField(args);
        defineTextualMetadataFields(args);
        defineNumericMetadataFields(args);
    }

    private void defineNumericMetadataFields(CreateArgs args) {
        for (String metadataField : numericMetadataFields) {
            args.indexedField(JSON_PATH_PREFIX + metadataField, metadataField, FieldType.NUMERIC);
        }
    }

    private void defineTextualMetadataFields(CreateArgs args) {
        for (String metadataField : textualMetadataFields) {
            args.indexedField(JSON_PATH_PREFIX + metadataField, metadataField, FieldType.TEXT);
        }
    }

    private void defineTextField(CreateArgs args) {
        args.indexedField(JSON_PATH_PREFIX + scalarFieldName, scalarFieldName, FieldType.TEXT);
    }

    private void defineVectorField(CreateArgs args) {
        args.indexedField(JSON_PATH_PREFIX + vectorFieldName,
                vectorFieldName,
                FieldType.VECTOR, new FieldOptions()
                        .vectorAlgorithm(vectorAlgorithm)
                        .vectorType(VectorType.FLOAT32)
                        .dimension(dimension.intValue())
                        .distanceMetric(distanceMetric));
    }

    public static class Builder {
        private String indexName;
        private String prefix;
        private String vectorFieldName;
        private String scalarFieldName;
        private List<String> numericMetadataFields;
        private List<String> textualMetadataFields;
        private VectorAlgorithm vectorAlgorithm;
        private Long dimension;
        private DistanceMetric metricType;

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder prefix(String prefix) {
            // Follow redis convention
            if (prefix != null && !prefix.endsWith(":")) {
                prefix = prefix + ":";
            }
            this.prefix = prefix;
            return this;
        }

        public Builder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        public Builder scalarFieldName(String scalarFieldName) {
            this.scalarFieldName = scalarFieldName;
            return this;
        }

        public Builder numericMetadataFields(List<String> numericMetadataFields) {
            this.numericMetadataFields = numericMetadataFields;
            return this;
        }

        public Builder textualMetadataFields(List<String> textualMetadataFields) {
            this.textualMetadataFields = textualMetadataFields;
            return this;
        }

        public Builder vectorAlgorithm(VectorAlgorithm vectorAlgorithm) {
            this.vectorAlgorithm = vectorAlgorithm;
            return this;
        }

        public Builder dimension(Long dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder metricType(DistanceMetric metricType) {
            this.metricType = metricType;
            return this;
        }

        public RedisSchema build() {
            return new RedisSchema(indexName,
                    prefix,
                    vectorFieldName,
                    scalarFieldName,
                    numericMetadataFields,
                    textualMetadataFields,
                    vectorAlgorithm,
                    dimension,
                    metricType);
        }
    }

}
