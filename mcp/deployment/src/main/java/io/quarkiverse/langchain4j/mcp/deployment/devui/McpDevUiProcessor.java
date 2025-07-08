package io.quarkiverse.langchain4j.mcp.deployment.devui;

import io.quarkiverse.langchain4j.mcp.runtime.devui.McpClientsJsonRpcService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class McpDevUiProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem cardPage() {
        CardPageBuildItem card = new CardPageBuildItem();
        card.addPage(Page.webComponentPageBuilder().title("MCP clients")
                .componentLink("qwc-mcp-clients.js")
                .icon("font-awesome-solid:robot"));
        return card;
    }

    @BuildStep
    void jsonRpcService(BuildProducer<JsonRPCProvidersBuildItem> producers) {
        producers.produce(new JsonRPCProvidersBuildItem(McpClientsJsonRpcService.class));
    }
}
