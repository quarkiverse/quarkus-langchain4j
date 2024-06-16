package io.quarkiverse.langchain4j.runtime.devui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.DockerClientFactory;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.DeviceRequest;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;

@ActivateRequestContext
public class OpenWebUIJsonRPCService {

    public static final Runnable CLOSE_TASK = () -> {
        OpenWebUIJsonRPCService service = new OpenWebUIJsonRPCService();
        service.stopOpenWebUI();
    };

    private static final String CONTAINER_NAME_PREFIX = "quarkus-open-webui-";

    public boolean isOpenWebUIRunning() {
        var container = inspectOpenWebUIContainer();
        return container != null && container.getState().getRunning();
    }

    public String getOpenWebUIUrl() {
        var container = inspectOpenWebUIContainer();
        if (container != null) {
            return container.getNetworkSettings().getPorts().getBindings().values().stream().flatMap(Arrays::stream)
                    .map(p -> "http://localhost" + ":" + p.getHostPortSpec())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public InspectContainerResponse inspectOpenWebUIContainer() {
        return DockerClientFactory.lazyClient().listContainersCmd().exec()
                .stream()
                .filter(OpenWebUIJsonRPCService::isOpenWebUIContainer)
                .findFirst()
                .map(c -> c.getId())
                .map(id -> DockerClientFactory.lazyClient().inspectContainerCmd(id).exec())
                .orElse(null);
    }

    public CreateContainerResponse startOpenWebUI(String image,
            boolean requestGpu,
            Map<Integer, Integer> portBindings,
            Map<String, String> envVars,
            Map<String, String> volumes) {
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

            DockerClientFactory.lazyClient().startContainerCmd(container.getId()).exec();
            return container;
        }
        return null;
    }

    public boolean stopOpenWebUI() {
        InspectContainerResponse container = inspectOpenWebUIContainer();
        if (container != null) {
            DockerClientFactory.lazyClient().stopContainerCmd(container.getId()).exec();
            DockerClientFactory.lazyClient().removeContainerCmd(container.getId()).exec();
            return true;
        }
        return false;
    }

    public String getConfigValue(String key) {
        try {
            return ConfigProvider.getConfig().getValue(key, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isOpenWebUIContainer(Container c) {
        return Arrays.stream(c.getNames()).anyMatch(n -> n.startsWith("/" + CONTAINER_NAME_PREFIX));
    }
}
