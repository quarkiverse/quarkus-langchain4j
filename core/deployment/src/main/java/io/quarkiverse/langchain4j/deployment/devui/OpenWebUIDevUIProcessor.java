package io.quarkiverse.langchain4j.deployment.devui;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.testcontainers.DockerClientFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.DeviceRequest;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;

import io.quarkiverse.langchain4j.runtime.devui.OpenWebUIJsonRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.buildtime.BuildTimeActionBuildItem;

public final class OpenWebUIDevUIProcessor {
    private static final String CONTAINER_NAME_PREFIX = "quarkus-open-webui-";

    @BuildStep
    JsonRPCProvidersBuildItem jsonRpcProviders() {
        return new JsonRPCProvidersBuildItem(OpenWebUIJsonRPCService.class);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void registerOpenWebUiCard(
            BuildProducer<BuildTimeActionBuildItem> buildTimeActionProducer,
            CuratedApplicationShutdownBuildItem closeBuildItem) {

        closeBuildItem.addCloseTask(() -> {
            stopOpenWebUI();
        }, true);

        List<BuildTimeActionBuildItem> buildTimeActions = new ArrayList<>();

        // inspectOpenWebUIContainer
        BuildTimeActionBuildItem inspectOpenWebUIContainer = new BuildTimeActionBuildItem();
        inspectOpenWebUIContainer.addAction("inspectOpenWebUIContainer", (params) -> {
            return toMap(inspectOpenWebUIContainer());
        });
        buildTimeActions.add(inspectOpenWebUIContainer);

        // startOpenWebUI
        DockerClient lazyClient = DockerClientFactory.lazyClient();
        BuildTimeActionBuildItem startOpenWebUI = new BuildTimeActionBuildItem();
        startOpenWebUI.addAction("startOpenWebUI", (var params) -> {
            try {
                String image = params.get("image");
                boolean requestGpu = Boolean.valueOf(params.get("requestGpu"));
                ObjectMapper objectMapper = new ObjectMapper();
                Map<Integer, Integer> portBindings = objectMapper.readValue(params.get("portBindings"), Map.class);
                Map<String, String> envVars = objectMapper.readValue(params.get("envVars"), Map.class);
                Map<String, String> volumes = objectMapper.readValue(params.get("volumes"), Map.class);

                if (!isOpenWebUIRunning()) {

                    List<PortBinding> allPortBindings = new ArrayList<>();
                    List<String> allEnvVars = new ArrayList<>();
                    List<Bind> allBinds = new ArrayList<>();
                    List<Volume> allVolumes = new ArrayList<>();
                    List<DeviceRequest> allDeviceRequests = new ArrayList<>();

                    try {
                        var images = DockerClientFactory.lazyClient().listImagesCmd().exec();
                        if (images.stream().filter(i -> i.getRepoTags() != null)
                                .noneMatch(i -> Arrays.asList(i.getRepoTags()).contains(image))) {
                            DockerClientFactory.lazyClient().pullImageCmd(image).start().awaitCompletion();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    for (var e : portBindings.entrySet()) {
                        allPortBindings.add(PortBinding.parse(e.getKey() + ":" + e.getValue()));
                    }

                    for (var e : envVars.entrySet()) {
                        allEnvVars.add(e.getKey() + "=" + e.getValue());
                    }

                    for (var e : volumes.entrySet()) {
                        String path = e.getKey();
                        Volume volume = new Volume(e.getValue());
                        allBinds.add(new Bind(path, volume));
                        allVolumes.add(volume);
                        try {
                            DockerClientFactory.lazyClient().inspectVolumeCmd(path).exec();
                        } catch (NotFoundException nfe) {
                            DockerClientFactory.lazyClient().createVolumeCmd().withName(path).exec();
                        }
                    }

                    if (requestGpu) {
                        DeviceRequest gpu = new DeviceRequest().withCount(-1).withCapabilities(List.of(List.of("gpu")));
                        allDeviceRequests.add(gpu);
                    }

                    HostConfig hostConfig = new HostConfig()
                            .withBinds(allBinds)
                            .withPortBindings(allPortBindings)
                            .withExtraHosts("host.docker.internal:host-gateway")
                            .withDeviceRequests(allDeviceRequests);

                    CreateContainerResponse container = DockerClientFactory.lazyClient()
                            .createContainerCmd(image)
                            .withEnv(allEnvVars)
                            .withVolumes(allVolumes)
                            .withName(CONTAINER_NAME_PREFIX + System.currentTimeMillis())
                            .withHostConfig(hostConfig)
                            .exec();

                    StartContainerCmd startContainerCmd = lazyClient.startContainerCmd(container.getId());
                    startContainerCmd.exec();
                    return true;
                }
                return null;
            } catch (JsonProcessingException ex) {
                throw new RuntimeException("Groot Kak", ex);
            }
        });
        buildTimeActions.add(startOpenWebUI);

        // isOpenWebUIRunning
        BuildTimeActionBuildItem isOpenWebUIRunning = new BuildTimeActionBuildItem();
        isOpenWebUIRunning.addAction("isOpenWebUIRunning", (params) -> {
            return isOpenWebUIRunning();
        });
        buildTimeActions.add(isOpenWebUIRunning);

        // getOpenWebUIUrl
        BuildTimeActionBuildItem getOpenWebUIUrl = new BuildTimeActionBuildItem();
        getOpenWebUIUrl.addAction("getOpenWebUIUrl", (params) -> {
            var container = inspectOpenWebUIContainer();
            if (container != null) {
                return container.getNetworkSettings().getPorts().getBindings().values().stream().flatMap(Arrays::stream)
                        .map(p -> "http://localhost" + ":" + p.getHostPortSpec())
                        .findFirst()
                        .orElse(null);
            }
            return null;
        });
        buildTimeActions.add(getOpenWebUIUrl);

        // stopOpenWebUI
        BuildTimeActionBuildItem stopOpenWebUI = new BuildTimeActionBuildItem();
        stopOpenWebUI.addAction("stopOpenWebUI", (params) -> {
            return stopOpenWebUI();
        });
        buildTimeActions.add(stopOpenWebUI);

        buildTimeActionProducer.produce(buildTimeActions);
    }

    private boolean stopOpenWebUI() throws NotFoundException, NotModifiedException {
        InspectContainerResponse container = inspectOpenWebUIContainer();
        if (container != null) {
            DockerClientFactory.lazyClient().stopContainerCmd(container.getId()).exec();
            DockerClientFactory.lazyClient().removeContainerCmd(container.getId()).exec();
            return true;
        }
        return false;
    }

    private InspectContainerResponse inspectOpenWebUIContainer() {
        return DockerClientFactory.lazyClient().listContainersCmd().exec()
                .stream()
                .filter(OpenWebUIDevUIProcessor::isOpenWebUIContainer)
                .findFirst()
                .map(c -> c.getId())
                .map(id -> DockerClientFactory.lazyClient().inspectContainerCmd(id).exec())
                .orElse(null);
    }

    private Map<String, String> toMap(InspectContainerResponse containerDetails) {
        if (containerDetails != null) {
            Map<String, String> m = new HashMap<>();
            m.put("id", containerDetails.getId());
            m.put("name", containerDetails.getName());
            if (containerDetails.getConfig() != null) {
                m.put("image", containerDetails.getConfig().getImage());
            }
            if (containerDetails.getConfig() != null && containerDetails.getConfig().getCmd() != null) {
                m.put("cmd", String.join(" ", containerDetails.getConfig().getCmd()));
            }
            return m;
        }
        return null;
    }

    private boolean isOpenWebUIRunning() {
        var container = inspectOpenWebUIContainer();
        return container != null && container.getState().getRunning();
    }

    private static boolean isOpenWebUIContainer(Container c) {
        return Arrays.stream(c.getNames()).anyMatch(n -> n.startsWith("/" + CONTAINER_NAME_PREFIX));
    }

    private <K, V> Map<K, V> unmarshalMap(String input, Class<K> keyClass, Class<V> valueClass) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(input));
            Map<K, V> map = new HashMap<>();
            properties.forEach((key, value) -> {
                K keyConverted = convert(key, keyClass);
                V valueConverted = convert(value, valueClass);
                map.put(keyConverted, valueConverted);
            });
            return map;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private <T> T convert(Object obj, Class<T> clazz) {
        String value = obj.toString();
        if (clazz.isAssignableFrom(Integer.class)) {
            return clazz.cast(Integer.parseInt(value));
        }
        return clazz.cast(value);
    }
}
