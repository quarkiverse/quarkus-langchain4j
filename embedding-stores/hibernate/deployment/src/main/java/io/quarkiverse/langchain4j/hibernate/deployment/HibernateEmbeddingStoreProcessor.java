package io.quarkiverse.langchain4j.hibernate.deployment;

import static io.quarkiverse.langchain4j.hibernate.runtime.DynamicEmbeddingStoreAdditionalMappingContributor.DEFAULT_DYNAMIC_PU_NAME;
import static io.quarkus.hibernate.orm.deployment.util.HibernateProcessorUtil.setDialectAndStorageEngine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitTransactionType;

import org.hibernate.boot.jaxb.SourceType;
import org.hibernate.boot.jaxb.mapping.spi.JaxbEntityImpl;
import org.hibernate.boot.jaxb.mapping.spi.JaxbEntityMappingsImpl;
import org.hibernate.boot.jaxb.mapping.spi.JaxbTableImpl;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.Embedding;
import dev.langchain4j.store.embedding.hibernate.EmbeddingEntity;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import io.agroal.api.AgroalPoolInterceptor;
import io.quarkiverse.langchain4j.deployment.EmbeddingStoreBuildItem;
import io.quarkiverse.langchain4j.hibernate.runtime.DynamicEmbeddingStoreAdditionalMappingContributor;
import io.quarkiverse.langchain4j.hibernate.runtime.HibernateEmbeddingStoreRecorder;
import io.quarkiverse.langchain4j.hibernate.runtime.SetupVectorConfigAgroalPoolInterceptor;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.SystemPropertyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.deployment.HibernateOrmConfig;
import io.quarkus.hibernate.orm.deployment.ImpliedBlockingPersistenceUnitTypeBuildItem;
import io.quarkus.hibernate.orm.deployment.JpaModelIndexBuildItem;
import io.quarkus.hibernate.orm.deployment.JpaModelPersistenceUnitContributionBuildItem;
import io.quarkus.hibernate.orm.deployment.JpaModelPersistenceUnitMappingBuildItem;
import io.quarkus.hibernate.orm.deployment.PersistenceUnitDescriptorBuildItem;
import io.quarkus.hibernate.orm.deployment.PersistenceXmlDescriptorBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.DatabaseKindDialectBuildItem;
import io.quarkus.hibernate.orm.runtime.HibernateOrmPersistenceUnitProviderHelper;
import io.quarkus.hibernate.orm.runtime.boot.QuarkusPersistenceUnitDescriptor;
import io.quarkus.hibernate.orm.runtime.boot.xml.RecordableXmlMapping;
import io.quarkus.hibernate.orm.runtime.config.DatabaseOrmCompatibilityVersion;
import io.quarkus.hibernate.orm.runtime.customized.BuiltinFormatMapperBehaviour;
import io.quarkus.hibernate.orm.runtime.customized.FormatMapperKind;
import io.quarkus.hibernate.orm.runtime.customized.JsonFormatterCustomizationCheck;
import io.quarkus.hibernate.orm.runtime.migration.MultiTenancyStrategy;
import io.quarkus.hibernate.orm.runtime.recording.RecordedConfig;
import io.quarkus.runtime.configuration.ConfigurationException;

class HibernateEmbeddingStoreProcessor {

    private static final DotName AGROAL_POOL_INTERCEPTOR = DotName.createSimple(AgroalPoolInterceptor.class);
    private static final DotName SETUP_VECTOR_CONFIG_AGROAL_POOL_INTERCEPTOR = DotName
            .createSimple(SetupVectorConfigAgroalPoolInterceptor.class);
    private static final DotName HIBERNATE_EMBEDDING_STORE = DotName.createSimple(HibernateEmbeddingStore.class);
    private static final DotName EMBEDDING_ENTITY = DotName.createSimple(EmbeddingEntity.class);
    private static final DotName ENTITY = DotName.createSimple(Entity.class);
    private static final DotName EMBEDDING = DotName.createSimple(Embedding.class);
    private static final DotName EMBEDDING_VECTOR = DotName
            .createSimple("dev.langchain4j.store.embedding.hibernate.EmbeddingVector");
    private static final DotName EMBEDDED_TEXT = DotName.createSimple(EmbeddedText.class);
    private static final DotName UNMAPPED_METADATA = DotName.createSimple(UnmappedMetadata.class);
    private static final DotName METADATA_ATTRIBUTE = DotName.createSimple(MetadataAttribute.class);

    private static final String FEATURE = "langchain4j-hibernate";
    private static final String CREATE_SCRIPT = "META-INF/langchain4j-hibernate-dynamic-create-script.sql";
    private static final String LOAD_SCRIPT = "META-INF/langchain4j-hibernate-dynamic-load-script.sql";
    private static final String MAPPINGS_FILE = "META-INF/langchain4j-hibernate-dynamic-mappings.xml";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-hibernate"));
    }

    @BuildStep
    public void produceDynamicMappingBuildItem(
            HibernateGenericEmbeddingStoreBuildTimeConfig dynamicBuildTimeConfig,
            BuildProducer<PersistenceUnitDescriptorBuildItem> persistenceUnitDescriptors,
            BuildProducer<HibernateEmbeddingStoreMappingBuildItem> mappingBuildItemProducer,
            BuildProducer<JpaModelPersistenceUnitContributionBuildItem> puContributionBuildItemBuildProducer,
            BuildProducer<ServiceProviderBuildItem> serviceProviderBuildItem,
            BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods,
            BuildProducer<SystemPropertyBuildItem> systemProperties,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceBuildProducer,
            HibernateOrmConfig hibernateOrmConfig,
            List<DatabaseKindDialectBuildItem> dbKindMetadataBuildItems,
            List<PersistenceXmlDescriptorBuildItem> persistenceXmlDescriptors,
            ImpliedBlockingPersistenceUnitTypeBuildItem impliedPU,
            List<JdbcDataSourceBuildItem> jdbcDataSources) {
        if (!impliedPU.shouldGenerateImpliedBlockingPersistenceUnit() && persistenceXmlDescriptors.isEmpty()
                || !isEmpty(dynamicBuildTimeConfig)) {
            // Create a dynamic embedding store mapping build item
            JdbcDataSourceBuildItem jdbcDataSource = dynamicBuildTimeConfig.datasource()
                    .map(dn -> jdbcDataSources.stream().filter(ds -> dn.equals(ds.getName()))
                            .findFirst()
                            .orElseThrow(() -> new ConfigurationException(
                                    "Couldn't find datasource for LangChain4j Hibernate dynamic integration with name: " + dn)))
                    .or(() -> jdbcDataSources.stream()
                            .filter(i -> i.isDefault())
                            .findFirst())
                    .orElseThrow(() -> new ConfigurationException(
                            "No datasource available for LangChain4j Hibernate dynamic integration"));
            JaxbEntityMappingsImpl jaxbEntityMappings = new JaxbEntityMappingsImpl();
            JaxbEntityImpl jaxbEntity = new JaxbEntityImpl();
            JaxbTableImpl jaxbTable = new JaxbTableImpl();
            jaxbTable.setName(dynamicBuildTimeConfig.table());
            jaxbEntity.setClazz(EMBEDDING_ENTITY.toString());
            jaxbEntity.setTable(jaxbTable);
            jaxbEntityMappings.getEntities().add(jaxbEntity);
            RecordableXmlMapping xmlMapping = new RecordableXmlMapping(
                    jaxbEntityMappings,
                    null,
                    SourceType.FILE,
                    "META-INF/langchain4j-hibernate-dynamic-mappings.xml");
            Properties properties = new Properties();
            properties.put(
                    SchemaToolingSettings.JAKARTA_HBM2DDL_DATABASE_ACTION + ".orm",
                    switch (dynamicBuildTimeConfig.schemaManagement().strategy()) {
                        case DROP -> Action.DROP;
                        case DROP_AND_CREATE -> Action.CREATE;
                        case CREATE -> Action.CREATE_ONLY;
                        case NONE -> Action.POPULATE;
                        case UPDATE -> Action.UPDATE;
                        case VALIDATE -> Action.VALIDATE;
                    });
            DatabaseKind databaseKind = switch (jdbcDataSource.getDbKind()) {
                case "db2" -> DatabaseKind.DB2;
                case "mariadb" -> DatabaseKind.MARIADB;
                case "mssql" -> DatabaseKind.MSSQL;
                case "mysql" -> DatabaseKind.MYSQL;
                case "postgresql" -> DatabaseKind.POSTGRESQL;
                case "oracle" -> DatabaseKind.ORACLE;
                default -> null;
            };
            if (databaseKind == null) {
                throw new ConfigurationException(
                        "Unsupported database kind for Langchain4j Hibernate EmbeddingStore: " + jdbcDataSource.getDbKind());
            }
            final String setupSql = databaseKind.getSetupSql();
            if (setupSql != null) {
                properties.put(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE,
                        org.hibernate.tool.schema.SourceType.SCRIPT_THEN_METADATA);
                properties.put(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, CREATE_SCRIPT);
                generatedResourceBuildProducer
                        .produce(new GeneratedResourceBuildItem(CREATE_SCRIPT, setupSql.getBytes(StandardCharsets.UTF_8)));
            }
            final boolean index = dynamicBuildTimeConfig.createIndex();
            final String importSqlContent = index
                    ? databaseKind.createIndexDDL(dynamicBuildTimeConfig.distanceFunction(),
                            dynamicBuildTimeConfig.indexType().orElse(null),
                            dynamicBuildTimeConfig.table(), "embedding", dynamicBuildTimeConfig.indexOptions().orElse(null))
                    : null;
            // Always set this to avoid the default file "import.sql" to creep in
            properties.put(SchemaToolingSettings.JAKARTA_HBM2DDL_LOAD_SCRIPT_SOURCE, LOAD_SCRIPT);
            generatedResourceBuildProducer.produce(new GeneratedResourceBuildItem(LOAD_SCRIPT,
                    (importSqlContent == null ? "" : importSqlContent).getBytes(StandardCharsets.UTF_8)));

            properties.put(DynamicEmbeddingStoreAdditionalMappingContributor.DIMENSION_CONFIGURATION,
                    dynamicBuildTimeConfig.dimension().orElseThrow(() -> new ConfigurationException(
                            "The config property quarkus.langchain4j.generic.dimension is required but it could not be found in any config source",
                            Set.of("quarkus.langchain4j.generic.dimension"))));
            properties.put(DynamicEmbeddingStoreAdditionalMappingContributor.TABLE_CONFIGURATION,
                    dynamicBuildTimeConfig.table());
            puContributionBuildItemBuildProducer.produce(new JpaModelPersistenceUnitContributionBuildItem(
                    DEFAULT_DYNAMIC_PU_NAME,
                    null,
                    Collections.emptyList(),
                    List.of(MAPPINGS_FILE)));

            // Borrowed from HibernateOrmProcessor#collectDialectConfig
            Optional<io.quarkus.datasource.common.runtime.DatabaseKind.SupportedDatabaseKind> supportedDatabaseKind = setDialectAndStorageEngine(
                    DEFAULT_DYNAMIC_PU_NAME,
                    Optional.of(jdbcDataSource.getDbKind()),
                    Optional.empty(),
                    Optional.empty(),
                    hibernateOrmConfig.defaultPersistenceUnit().dialect(),
                    dbKindMetadataBuildItems,
                    systemProperties,
                    properties::setProperty);

            if (io.quarkus.datasource.common.runtime.DatabaseKind.isPostgreSQL(jdbcDataSource.getDbKind())) {
                // Workaround for https://hibernate.atlassian.net/browse/HHH-19063
                reflectiveMethods.produce(new ReflectiveMethodBuildItem(
                        "Accessed in org.hibernate.engine.jdbc.env.internal.DefaultSchemaNameResolver.determineAppropriateResolverDelegate",
                        true, "org.postgresql.jdbc.PgConnection", "getSchema"));
            }

            serviceProviderBuildItem.produce(new ServiceProviderBuildItem(AdditionalMappingContributor.class.getName(),
                    DynamicEmbeddingStoreAdditionalMappingContributor.class.getName()));
            persistenceUnitDescriptors
                    .produce(new PersistenceUnitDescriptorBuildItem(
                            new QuarkusPersistenceUnitDescriptor(
                                    DEFAULT_DYNAMIC_PU_NAME,
                                    new HibernateOrmPersistenceUnitProviderHelper(),
                                    PersistenceUnitTransactionType.JTA,
                                    Collections.emptyList(),
                                    properties,
                                    false),
                            new RecordedConfig(
                                    Optional.of(jdbcDataSource.getName()),
                                    Optional.of(jdbcDataSource.getDbKind()),
                                    supportedDatabaseKind.map(Enum::name),
                                    jdbcDataSource.getDbVersion(),
                                    Optional.empty(),
                                    Set.of(),
                                    MultiTenancyStrategy.NONE,
                                    DatabaseOrmCompatibilityVersion.LATEST,
                                    BuiltinFormatMapperBehaviour.FAIL,
                                    JsonFormatterCustomizationCheck.jsonFormatterCustomizationCheckSupplier(true, true),
                                    Collections.emptyMap()),
                            null,
                            List.of(xmlMapping),
                            false, false, Optional.of(FormatMapperKind.JACKSON), Optional.empty()));
            mappingBuildItemProducer.produce(new HibernateEmbeddingStoreMappingBuildItem(
                    DEFAULT_DYNAMIC_PU_NAME,
                    EMBEDDING_ENTITY.toString(),
                    dynamicBuildTimeConfig.distanceFunction()));
        } else {
            mappingBuildItemProducer.produce(new HibernateEmbeddingStoreMappingBuildItem(
                    null, null, null));
        }
    }

    private boolean isEmpty(HibernateGenericEmbeddingStoreBuildTimeConfig config) {
        return config.datasource().isEmpty()
                && config.dimension().isEmpty();
    }

    @BuildStep
    EmbeddingStoreBuildItem embeddingStoreBuildItem() {
        return new EmbeddingStoreBuildItem();
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            HibernateEmbeddingStoreRecorder recorder,
            List<PersistenceUnitDescriptorBuildItem> pus,
            List<HibernateEmbeddingStoreMappingBuildItem> mappings,
            JpaModelIndexBuildItem indexBuildItem,
            JpaModelPersistenceUnitMappingBuildItem persistenceUnitMappingBuildItem) {
        for (HibernateEmbeddingStoreMappingBuildItem mapping : mappings) {
            createBean(beanProducer, recorder, mapping, pus, indexBuildItem, persistenceUnitMappingBuildItem);
        }
    }

    private void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            HibernateEmbeddingStoreRecorder recorder,
            HibernateEmbeddingStoreMappingBuildItem mappingBuildItem,
            List<PersistenceUnitDescriptorBuildItem> pus,
            JpaModelIndexBuildItem indexBuildItem,
            JpaModelPersistenceUnitMappingBuildItem persistenceUnitMappingBuildItem) {

        AnnotationInstance entityManagerFactoryQualifier;
        String persistenceUnitName = null;
        if (mappingBuildItem.getPersistenceUnitName().isPresent()) {
            persistenceUnitName = mappingBuildItem.getPersistenceUnitName().get();
            entityManagerFactoryQualifier = AnnotationInstance.builder(PersistenceUnit.class)
                    .add("value", persistenceUnitName)
                    .build();
        } else {
            List<ClassInfo> entityClassInfos = getEntityClassInfos(indexBuildItem);
            for (ClassInfo entityClassInfo : entityClassInfos) {
                Set<String> persistenceUnits = persistenceUnitMappingBuildItem.getEntityToPersistenceUnits()
                        .get(entityClassInfo.name().toString());
                if (persistenceUnits != null && !persistenceUnits.isEmpty()) {
                    if (persistenceUnits.size() == 1) {
                        String persistenceUnit = persistenceUnits.iterator().next();
                        if (persistenceUnitName != null && persistenceUnitName.equals(persistenceUnit)) {
                            throw new IllegalArgumentException(
                                    "Can't determine persistence unit for HibernateEmbeddingStore, because candidate classes are part of multiple persistence units: ["
                                            + persistenceUnitName + ", " + persistenceUnit + "]");
                        }
                        persistenceUnitName = persistenceUnit;
                    } else {
                        throw new IllegalArgumentException(
                                "Can't determine entity class for HibernateEmbeddingStore, because the candidate class "
                                        + entityClassInfo.name() + " is part of multiple persistence units: "
                                        + persistenceUnits);
                    }
                }
            }

            if (persistenceUnitName == null) {
                throw new IllegalArgumentException(
                        "Can't determine persistence unit for HibernateEmbeddingStore, because none of the candidate classes are part of a persistence unit: "
                                + entityClassInfos.stream().map(ClassInfo::name).toList());
            }
            entityManagerFactoryQualifier = "<default>".equals(persistenceUnitName)
                    ? AnnotationInstance.builder(Default.class).build()
                    : AnnotationInstance.builder(PersistenceUnit.class)
                            .add("value", persistenceUnitName)
                            .build();
        }

        PersistenceUnitDescriptorBuildItem persistenceUnitDescriptor = getPersistenceUnitDescriptor(pus, persistenceUnitName);
        String databaseKind = persistenceUnitDescriptor.getConfig().getDbKind().get();

        ClassInfo entityClassInfo;
        if (mappingBuildItem.getEntityName().isPresent()) {
            String entityName = mappingBuildItem.getEntityName().get();
            entityClassInfo = indexBuildItem.getIndex().getClassByName(entityName);
            if (entityClassInfo != null) {
                // If there is a top-level class with the given entity name as class name,
                // but has no @Entity annotation, ignore it, unless it's the dynamic embedding entity
                if (entityClassInfo.annotation(ENTITY) == null && !entityName.equals(EMBEDDING_ENTITY.toString())) {
                    entityClassInfo = null;
                }
            }
            if (entityClassInfo == null) {
                // Search for @Entity annotated classes
                for (AnnotationInstance annotation : indexBuildItem.getIndex().getAnnotations(ENTITY)) {
                    ClassInfo annotationTargetClass = annotation.target().asClass();
                    AnnotationValue nameValue = annotation.value("name");
                    String explicitEntityName = nameValue == null ? "" : nameValue.asString();
                    // Match explicit name or implicit via simple class name
                    if (entityName.equals(explicitEntityName)
                            || explicitEntityName.isEmpty() && annotationTargetClass.simpleName().equals(entityName)) {
                        entityClassInfo = annotationTargetClass;
                        break;
                    }
                }
            }
            if (entityClassInfo == null) {
                throw new IllegalArgumentException(
                        "Unable to find entity class for entity name configured for HibernateEmbeddingStore " + entityName);
            }
        } else {
            // Search for @Embedding annotations
            List<ClassInfo> entityClassInfos = getEntityClassInfos(indexBuildItem);
            // Ignore classes that are not part of the chosen PU
            String persistenceUnit = persistenceUnitName;
            entityClassInfos.removeIf(
                    classInfo -> !persistenceUnitMappingBuildItem.getEntityToPersistenceUnits().get(classInfo.name().toString())
                            .contains(persistenceUnit));
            entityClassInfo = switch (entityClassInfos.size()) {
                case 0 -> throw new IllegalArgumentException(
                        "Unable to find entity classes containing @Embedding annotation for configured HibernateEmbeddingStore");
                case 1 -> entityClassInfos.get(0);
                default -> throw new IllegalArgumentException(
                        "Multiple entity classes containing @Embedding annotation found. Please explicitly configure quarkus.langchain4j.hibernate-orm.entity-name");
            };
        }

        String entityName = entityClassInfo.name().toString();
        String embeddingAttributeName = null;
        String embeddedTextAttributeName = null;
        String unmappedMetadataAttributeName = null;
        DistanceFunction localDistanceFunction = null;
        List<String> metadataAttributeNameList = new ArrayList<>();

        for (AnnotationTarget annotationTarget : getFieldsAndMethods(entityClassInfo, indexBuildItem)) {
            String attributeName;
            if (annotationTarget instanceof FieldInfo fieldInfo) {
                attributeName = fieldInfo.name();
            } else if (annotationTarget instanceof MethodInfo methodInfo) {
                attributeName = attributeName(methodInfo);
            } else {
                attributeName = null;
            }
            if (attributeName != null) {
                AnnotationInstance embeddingVectorAnnotation = annotationTarget.annotation(EMBEDDING_VECTOR);
                if (embeddingVectorAnnotation != null) {
                    if (embeddingAttributeName != null) {
                        throw new IllegalArgumentException("Multiple @Embedding/@EmbeddingVector annotated attributes ["
                                + embeddingAttributeName + "," + attributeName
                                + "] found on " + entityName
                                + ". Make sure to specify only a single embedding attribute");
                    }
                    embeddingAttributeName = attributeName;
                    AnnotationValue distance = embeddingVectorAnnotation.value("distance");
                    localDistanceFunction = distance == null ? null : DistanceFunction.valueOf(distance.asEnum());
                }
                if (annotationTarget.hasAnnotation(EMBEDDING)) {
                    if (embeddingAttributeName != null) {
                        throw new IllegalArgumentException("Multiple @Embedding/@EmbeddingVector annotated attributes ["
                                + embeddingAttributeName + "," + attributeName
                                + "] found on " + entityName
                                + ". Make sure to specify only a single embedding attribute");
                    }
                    embeddingAttributeName = attributeName;
                }
                if (annotationTarget.hasAnnotation(EMBEDDED_TEXT)) {
                    if (embeddedTextAttributeName != null) {
                        throw new IllegalArgumentException("Multiple @EmbeddedText annotated attributes ["
                                + embeddedTextAttributeName + "," + attributeName
                                + "] found on " + entityName
                                + ". Make sure to specify only a single embedded text attribute");
                    }
                    embeddedTextAttributeName = attributeName;
                }
                if (annotationTarget.hasAnnotation(UNMAPPED_METADATA)) {
                    if (unmappedMetadataAttributeName != null) {
                        throw new IllegalArgumentException("Multiple @UnmappedMetadata annotated attributes ["
                                + unmappedMetadataAttributeName + "," + attributeName
                                + "] found on " + entityName
                                + ". Make sure to specify only a single unmapped metadata attribute");
                    }
                    unmappedMetadataAttributeName = attributeName;
                }
                if (annotationTarget.hasAnnotation(METADATA_ATTRIBUTE)) {
                    metadataAttributeNameList.add(attributeName);
                }
            }
        }
        if (embeddingAttributeName == null) {
            throw new IllegalArgumentException("Embedding attribute not found on " + entityName
                    + ". Did you forget to annotate @Embedding on an attribute?");
        }
        if (unmappedMetadataAttributeName == null) {
            throw new IllegalArgumentException("Text metadata attribute not found on " + entityName
                    + ". Did you forget to annotate @UnmappedMetadata on an attribute?");
        }
        String[] metadataAttributeNames = metadataAttributeNameList.toArray(new String[0]);

        final DistanceFunction distanceFunction = mappingBuildItem.getDistanceFunction() != null
                ? mappingBuildItem.getDistanceFunction()
                : localDistanceFunction != null
                        ? localDistanceFunction
                        : DistanceFunction.COSINE;

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(HIBERNATE_EMBEDDING_STORE)
                .types(ClassType.create(HibernateEmbeddingStore.class),
                        ParameterizedType.create(HibernateEmbeddingStore.class, ClassType.create(entityClassInfo.name())),
                        ClassType.create(EmbeddingStore.class),
                        ParameterizedType.create(EmbeddingStore.class, ClassType.create(TextSegment.class)))
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .createWith(recorder.embeddingStoreFunction(
                        databaseKind,
                        entityName,
                        embeddingAttributeName,
                        embeddedTextAttributeName,
                        unmappedMetadataAttributeName,
                        metadataAttributeNames,
                        "<default>".equals(persistenceUnitName) ? null : persistenceUnitName,
                        distanceFunction))
                .addInjectionPoint(ClassType.create(DotName.createSimple(EntityManagerFactory.class)),
                        entityManagerFactoryQualifier)
                .done());

        DatabaseKind langchain4jDatabaseKind = HibernateEmbeddingStoreRecorder.langchain4jDatabaseKind(databaseKind);
        if (!DEFAULT_DYNAMIC_PU_NAME.equals(persistenceUnitName)
                && langchain4jDatabaseKind != null && langchain4jDatabaseKind.getSetupSql() != null) {
            AnnotationInstance datasourceQualifier = persistenceUnitDescriptor.getConfig().getDataSource()
                    .filter(dn -> !"<default>".equals(dn))
                    .map(dn -> AnnotationInstance.builder(DataSource.class).add("value", dn).build())
                    .orElse(AnnotationInstance.builder(Default.class).build());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(SETUP_VECTOR_CONFIG_AGROAL_POOL_INTERCEPTOR)
                    .types(ClassType.create(AGROAL_POOL_INTERCEPTOR))
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.setupVectorConfigAgroalPoolInterceptor(langchain4jDatabaseKind.getSetupSql()))
                    .qualifiers(datasourceQualifier)
                    .done());
        }
    }

    private static List<ClassInfo> getEntityClassInfos(JpaModelIndexBuildItem indexBuildItem) {
        List<ClassInfo> entityClassInfos = new ArrayList<>();
        List<AnnotationInstance> annotations = concat(
                indexBuildItem.getIndex().getAnnotations(EMBEDDING),
                indexBuildItem.getIndex().getAnnotations(EMBEDDING_VECTOR));
        for (AnnotationInstance annotation : annotations) {
            AnnotationTarget target = annotation.target();
            ClassInfo potentialEntityClass;
            if (target instanceof MethodInfo methodInfo) {
                potentialEntityClass = methodInfo.declaringClass();
            } else if (target instanceof FieldInfo fieldInfo) {
                potentialEntityClass = fieldInfo.declaringClass();
            } else {
                // Ignore other annotation targets
                potentialEntityClass = null;
            }
            if (potentialEntityClass != null && potentialEntityClass.annotation(ENTITY) != null) {
                entityClassInfos.add(potentialEntityClass);
            }
        }
        return entityClassInfos;
    }

    private static <T> List<T> concat(List<T> list1, List<T> list2) {
        if (list1 == null || list1.isEmpty()) {
            return list2;
        }
        if (list2 == null || list2.isEmpty()) {
            return list1;
        }
        ArrayList<T> list = new ArrayList<>(list1.size() + list2.size());
        list.addAll(list1);
        list.addAll(list2);
        return list;
    }

    private String attributeName(MethodInfo methodInfo) {
        if (methodInfo.parametersCount() == 0 && methodInfo.returnType() != ClassType.VOID_CLASS) {
            String methodName = methodInfo.name();
            if (methodName.startsWith("get") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
                return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
                return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            } else {
                return methodName;
            }
        } else {
            return null;
        }
    }

    private List<AnnotationTarget> getFieldsAndMethods(ClassInfo entityClassInfo, JpaModelIndexBuildItem indexBuildItem) {
        ArrayList<AnnotationTarget> annotationTargets = new ArrayList<>();
        do {
            annotationTargets.addAll(entityClassInfo.fields());
            annotationTargets.addAll(entityClassInfo.methods());
            entityClassInfo = indexBuildItem.getIndex().getClassByName(entityClassInfo.superName());
        } while (entityClassInfo != null && !DotNames.OBJECT.equals(entityClassInfo.name()));
        return annotationTargets;
    }

    private PersistenceUnitDescriptorBuildItem getPersistenceUnitDescriptor(
            List<PersistenceUnitDescriptorBuildItem> pus,
            String persistenceUnitName) {
        for (PersistenceUnitDescriptorBuildItem pu : pus) {
            if (persistenceUnitName.equals(pu.getPersistenceUnitName())) {
                return pu;
            }
        }
        throw new IllegalArgumentException("No persistence unit found for name " + persistenceUnitName);
    }
}
