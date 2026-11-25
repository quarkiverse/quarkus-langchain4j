package io.quarkiverse.langchain4j.hibernate.milvus.deployment;

import static io.quarkus.datasource.deployment.spi.DatabaseDefaultSetupConfig.DEFAULT_DATABASE_NAME;
import static io.quarkus.datasource.deployment.spi.DatabaseDefaultSetupConfig.DEFAULT_DATABASE_STRONG_PASSWORD;
import static io.quarkus.datasource.deployment.spi.DatabaseDefaultSetupConfig.DEFAULT_DATABASE_USERNAME;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.jboss.logging.Logger;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.command.InspectContainerResponse;

import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.deployment.spi.DatasourceStartable;
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceContainerConfig;
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceProvider;
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceProviderBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesComposeProjectBuildItem;
import io.quarkus.devservices.common.ComposeLocator;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.devservices.common.JBossLoggingConsumer;
import io.quarkus.devservices.common.Labels;
import io.quarkus.devservices.common.Volumes;
import io.quarkus.runtime.LaunchMode;

public class MilvusDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(MilvusDevServicesProcessor.class);

    private static final MilvusDatasourceServiceConfigurator configurator = new MilvusDatasourceServiceConfigurator();

    @BuildStep
    DevServicesDatasourceProviderBuildItem setupMilvus(
            DevServicesComposeProjectBuildItem composeProjectBuildItem) {
        return new DevServicesDatasourceProviderBuildItem(JDBCMilvusProcessor.DATABASE_KIND,
                new DevServicesDatasourceProvider() {

                    @Override
                    public String getFeature() {
                        return JDBCMilvusProcessor.FEATURE;
                    }

                    @Override
                    public DatasourceStartable createDatasourceStartable(
                            Optional<String> username,
                            Optional<String> password,
                            String datasourceName, DevServicesDatasourceContainerConfig containerConfig,
                            LaunchMode launchMode, boolean useSharedNetwork, Optional<Duration> startupTimeout) {

                        String effectiveUsername = containerConfig.getUsername()
                                .orElse(username.orElse(DEFAULT_DATABASE_USERNAME));
                        String effectivePassword = containerConfig.getPassword()
                                .orElse(password.orElse(DEFAULT_DATABASE_STRONG_PASSWORD));
                        String effectiveDbName = containerConfig.getDbName().orElse(
                                DataSourceUtil.isDefault(datasourceName) ? DEFAULT_DATABASE_NAME : datasourceName);

                        String selectedImage = ConfigureUtil.getDefaultImageNameFor("milvus");
                        QuarkusMilvusContainer container = new QuarkusMilvusContainer(Optional.of(selectedImage),
                                containerConfig.getFixedExposedPort(),
                                composeProjectBuildItem.getDefaultNetworkId(),
                                useSharedNetwork);
                        startupTimeout.ifPresent(container::withStartupTimeout);

                        container.withUsername(effectiveUsername)
                                .withPassword(effectivePassword)
                                .withDatabaseName(effectiveDbName)
                                .withReuse(containerConfig.isReuse());
                        Labels.addDataSourceLabel(container, datasourceName);
                        Volumes.addVolumes(container, containerConfig.getVolumes());

                        container.withEnv(containerConfig.getContainerEnv());

                        containerConfig.getAdditionalJdbcUrlProperties().forEach(container::withUrlParam);

                        containerConfig.getInitScriptPath().ifPresent(container::withInitScripts);
                        if (containerConfig.isShowLogs()) {
                            container.withLogConsumer(new JBossLoggingConsumer(LOG));
                        }

                        return container;
                    }

                    @Override
                    public Optional<DevServicesDatasourceProvider.RunningDevServicesDatasource> findRunningComposeDatasource(
                            LaunchMode launchMode,
                            boolean useSharedNetwork, DevServicesDatasourceContainerConfig containerConfig,
                            DevServicesComposeProjectBuildItem composeProjectBuildItem) {

                        List<String> images = List.of(containerConfig.getImageName().orElse("milvus"), "milvus");
                        return ComposeLocator
                                .locateContainer(composeProjectBuildItem, images, MilvusContainer.MILVUS_PORT, launchMode,
                                        useSharedNetwork)
                                .map(containerAddress -> configurator.composeRunningService(containerAddress, containerConfig));
                    }

                });
    }

    private static class MilvusContainer extends JdbcDatabaseContainer<MilvusContainer> {

        public static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("milvusdb/milvus");
        public static final int MILVUS_PORT = 19530;
        private static final String DEFAULT_DATABASE_NAME = "default";
        private static final String DEFAULT_USERNAME = "root";
        private static final String DEFAULT_PASSWORD = "Milvus";

        private String etcdEndpoint;

        private String databaseName = DEFAULT_DATABASE_NAME;
        private String username = DEFAULT_USERNAME;
        private String password = DEFAULT_PASSWORD;

        public MilvusContainer(Optional<String> imageName) {
            super(DockerImageName
                    .parse(imageName.orElseGet(() -> ConfigureUtil.getDefaultImageNameFor("milvus")))
                    .asCompatibleSubstituteFor(DEFAULT_IMAGE_NAME));
            withExposedPorts(9091, 19530);
            waitingFor(Wait.forHttp("/healthz").forPort(9091));
            withCommand("bash", "-c", "/milvus/start.sh");
            withCopyFileToContainer(
                    MountableFile.forClasspathResource("testcontainers/embedEtcd.yaml"),
                    "/milvus/configs/embedEtcd.yaml");
            withCopyFileToContainer(
                    MountableFile.forClasspathResource("testcontainers/start.sh", 0777),
                    "/milvus/start.sh");
            withEnv("COMMON_STORAGETYPE", "local");
            // See https://github.com/milvus-io/milvus/issues/43760
            withEnv("DEPLOY_MODE", "STANDALONE");
        }

        @Override
        public String getDriverClassName() {
            return JDBCMilvusProcessor.DRIVER_CLASS;
        }

        @Override
        public String getJdbcUrl() {
            String additionalUrlParams = constructUrlParameters("?", "&");
            return ("jdbc:milvus://" +
                    getHost() +
                    ":" +
                    getMappedPort(MILVUS_PORT) +
                    "/" +
                    databaseName +
                    additionalUrlParams);
        }

        @Override
        public String getDatabaseName() {
            return databaseName;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public MilvusContainer withDatabaseName(final String databaseName) {
            this.databaseName = sanitizeDatabaseName(databaseName);
            return self();
        }

        private String sanitizeDatabaseName(String databaseName) {
            StringBuilder sb = null;
            for (int i = 0; i < databaseName.length(); i++) {
                final char c = databaseName.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    if (sb == null) {
                        sb = new StringBuilder(databaseName.length());
                        sb.append(databaseName, 0, i);
                    }
                    sb.append('_');
                } else if (sb != null) {
                    sb.append(c);
                }
            }
            return sb == null ? databaseName : sb.toString();
        }

        @Override
        public MilvusContainer withUsername(final String username) {
            this.username = username;
            return self();
        }

        @Override
        public MilvusContainer withPassword(final String password) {
            this.password = password;
            return self();
        }

        @Override
        protected String getTestQueryString() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void waitUntilContainerStarted() {
            getWaitStrategy().waitUntilReady(this);
        }

        @Override
        protected void containerIsStarted(InspectContainerResponse containerInfo) {
            super.containerIsStarted(containerInfo);
            if (!DEFAULT_DATABASE_NAME.equals(databaseName)) {
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest createDatabaseRequest = HttpRequest.newBuilder()
                            .uri(java.net.URI.create(getEndpoint() + "/v2/vectordb/databases/create"))
                            .header("Authorization", "Bearer " + DEFAULT_USERNAME + ":" + DEFAULT_PASSWORD)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"dbName\": \"" + databaseName + "\"}"))
                            .build();
                    HttpResponse<String> response = client.send(
                            createDatabaseRequest,
                            HttpResponse.BodyHandlers.ofString());
                    if (!"{\"code\":0,\"data\":{}}".equals(response.body())) {
                        throw new RuntimeException(
                                "Failed to create Milvus database: " + databaseName + "\nResponse: " + response.body());
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create Milvus database: " + databaseName, e);
                }
            }
            if (!DEFAULT_USERNAME.equals(username)) {
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest createUser = HttpRequest.newBuilder()
                            .uri(java.net.URI.create(getEndpoint() + "/v2/vectordb/users/create"))
                            .header("Authorization", "Bearer " + DEFAULT_USERNAME + ":" + DEFAULT_PASSWORD)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"userName\": \"" + username + "\", \"password\": \"" + password + "\"}"))
                            .build();
                    HttpResponse<String> response = client.send(createUser, HttpResponse.BodyHandlers.ofString());
                    if (!"{\"code\":0,\"data\":{}}".equals(response.body())) {
                        throw new RuntimeException(
                                "Failed to create Milvus user: " + username + "\nResponse: " + response.body());
                    }
                    HttpRequest grantRole = HttpRequest.newBuilder()
                            .uri(java.net.URI.create(getEndpoint() + "/v2/vectordb/users/grant_role"))
                            .header("Authorization", "Bearer " + DEFAULT_USERNAME + ":" + DEFAULT_PASSWORD)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"userName\": \"" + username + "\", \"roleName\": \"admin\"}"))
                            .build();
                    response = client.send(grantRole, HttpResponse.BodyHandlers.ofString());
                    if (!"{\"code\":0,\"data\":{}}".equals(response.body())) {
                        throw new RuntimeException(
                                "Failed to grant role for Milvus user: " + username + "\nResponse: " + response.body());
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create Milvus user: " + username, e);
                }
            }
        }

        public MilvusContainer withEtcdEndpoint(String etcdEndpoint) {
            this.etcdEndpoint = etcdEndpoint;
            return this;
        }

        public String getEndpoint() {
            return "http://" + getHost() + ":" + getMappedPort(19530);
        }

        @Override
        protected void configure() {
            if (this.etcdEndpoint == null) {
                withEnv("ETCD_USE_EMBED", "true");
                withEnv("ETCD_DATA_DIR", "/var/lib/milvus/etcd");
                withEnv("ETCD_CONFIG_PATH", "/milvus/configs/embedEtcd.yaml");
            } else {
                withEnv("ETCD_ENDPOINTS", this.etcdEndpoint);
            }
        }
    }

    private static class QuarkusMilvusContainer extends MilvusContainer implements DatasourceStartable {

        private final OptionalInt fixedExposedPort;
        private final boolean useSharedNetwork;

        private final String hostName;
        private DevServicesDatasourceProvider.RunningDevServicesDatasource runningDevServicesDatasource;

        public QuarkusMilvusContainer(Optional<String> imageName, OptionalInt fixedExposedPort,
                String defaultNetworkId, boolean useSharedNetwork) {
            super(imageName);
            this.fixedExposedPort = fixedExposedPort;
            this.useSharedNetwork = useSharedNetwork;

            this.hostName = ConfigureUtil.configureNetwork(this, defaultNetworkId, useSharedNetwork, "milvus");
        }

        @Override
        protected void configure() {
            super.configure();

            if (useSharedNetwork) {
                return;
            }

            if (fixedExposedPort.isPresent()) {
                addFixedExposedPort(fixedExposedPort.getAsInt(), MILVUS_PORT);
            } else {
                addExposedPort(MILVUS_PORT);
            }
        }

        public int getPort() {
            if (useSharedNetwork) {
                return MILVUS_PORT;
            }

            if (fixedExposedPort.isPresent()) {
                return fixedExposedPort.getAsInt();
            }
            return super.getMappedPort(MILVUS_PORT);
        }

        @Override
        public String getHost() {
            return useSharedNetwork ? hostName : super.getHost();
        }

        // this is always reachable from the host JVM, so that testcontainers can determine the status of the
        // container (which it does by trying to acquire a connection)
        @Override
        public String getJdbcUrl() {
            String host = DevServicesHostUtil.resolvePublishedPortHost(getContainerId(), getHost());
            String authority = DevServicesHostUtil.formatHostAndPort(host, getMappedPort(MILVUS_PORT));
            String additionalUrlParams = constructUrlParameters("?", "&");
            return "jdbc:milvus://" + authority + "/" + getDatabaseName() + additionalUrlParams;
        }

        // this is meant to be called by Quarkus code and is not strictly needed
        // in the PostgreSQL case as testcontainers does not try to establish
        // a connection to determine if the container is ready, but we do it anyway to be consistent across
        // DB containers
        public String getEffectiveJdbcUrl() {
            if (useSharedNetwork) {
                // in this case we expose the URL using the network alias we created in 'configure'
                // and the container port since the application communicating with this container
                // won't be doing port mapping
                String authority = DevServicesHostUtil.formatHostAndPort(hostName, MILVUS_PORT);
                String additionalUrlParams = constructUrlParameters("?", "&");
                return "jdbc:milvus://" + authority + "/" + getDatabaseName() + additionalUrlParams;
            }
            return getJdbcUrl();
        }

        public String getReactiveUrl() {
            return null;
        }

        @Override
        public String getConnectionInfo() {
            return getEffectiveJdbcUrl();
        }

        @Override
        public void close() {
            super.close();
        }

        @Override
        public DevServicesDatasourceProvider.RunningDevServicesDatasource runningDevServicesDatasource() {
            // Cache, since this is going to be called a lot
            if (runningDevServicesDatasource == null) {
                runningDevServicesDatasource = DatasourceStartable.super.runningDevServicesDatasource();
            }
            return runningDevServicesDatasource;
        }
    }
}
