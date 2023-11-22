package io.quarkiverse.langchain4j.redis.runtime;

import java.util.List;

import io.quarkiverse.langchain4j.redis.MetricType;
import io.quarkiverse.langchain4j.redis.VectorAlgorithm;
import io.vertx.mutiny.redis.client.Request;

public class RedisSchema {

    private String indexName;
    private String prefix;
    private String vectorFieldName;
    private String scalarFieldName;
    private List<String> metadataFields;
    private VectorAlgorithm vectorAlgorithm;
    private Long dimension;
    private MetricType metricType;
    private static final String JSON_PATH_PREFIX = "$.";

    public RedisSchema(String indexName,
            String prefix,
            String vectorFieldName,
            String scalarFieldName,
            List<String> metadataFields,
            VectorAlgorithm vectorAlgorithm,
            Long dimension,
            MetricType metricType) {
        this.indexName = indexName;
        this.prefix = prefix;
        this.vectorFieldName = vectorFieldName;
        this.scalarFieldName = scalarFieldName;
        this.metadataFields = metadataFields;
        this.vectorAlgorithm = vectorAlgorithm;
        this.dimension = dimension;
        this.metricType = metricType;
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

    public List<String> getMetadataFields() {
        return metadataFields;
    }

    public VectorAlgorithm getVectorAlgorithm() {
        return vectorAlgorithm;
    }

    public Long getDimension() {
        return dimension;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public void defineFields(Request args) {
        defineTextField(args);
        defineVectorField(args);
        defineMetadataFields(args);
    }

    private void defineMetadataFields(Request args) {
        for (String metadataField : metadataFields) {
            args.arg(JSON_PATH_PREFIX + metadataField);
            args.arg("AS");
            args.arg(metadataField);
            args.arg("TEXT");
            args.arg("WEIGHT");
            args.arg("1.0");
        }
    }

    private void defineTextField(Request args) {
        args.arg(JSON_PATH_PREFIX + scalarFieldName);
        args.arg("AS");
        args.arg(scalarFieldName);
        args.arg("TEXT");
        args.arg("WEIGHT");
        args.arg("1.0");
    }

    private void defineVectorField(Request args) {
        args.arg(JSON_PATH_PREFIX + vectorFieldName);
        args.arg("AS");
        args.arg(vectorFieldName);
        args.arg("VECTOR");
        args.arg(vectorAlgorithm.name());
        args.arg("8");
        args.arg("DIM");
        args.arg(dimension);
        args.arg("DISTANCE_METRIC");
        args.arg(metricType.name());
        args.arg("TYPE");
        args.arg("FLOAT32");
        args.arg("INITIAL_CAP");
        args.arg("5");
    }

    public static class Builder {
        private String indexName;
        private String prefix;
        private String vectorFieldName;
        private String scalarFieldName;
        private List<String> metadataFields;
        private VectorAlgorithm vectorAlgorithm;
        private Long dimension;
        private MetricType metricType;

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

        public Builder metadataFields(List<String> metadataFields) {
            this.metadataFields = metadataFields;
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

        public Builder metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        public RedisSchema build() {
            return new RedisSchema(indexName,
                    prefix,
                    vectorFieldName,
                    scalarFieldName,
                    metadataFields,
                    vectorAlgorithm,
                    dimension,
                    metricType);
        }
    }

}
