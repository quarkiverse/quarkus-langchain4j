package io.quarkiverse.langchain4j.hibernate.milvus.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public final class MilvusJDBCReflections {

    @BuildStep
    void build(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        //Not strictly necessary when using Agroal, as it also registers
        //any JDBC driver being configured explicitly through its configuration.
        //We register it for the sake of other users.
        final String driverName = "org.hibernate.milvus.jdbc.internal.MilvusDriver";
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(driverName).build());
    }
}
