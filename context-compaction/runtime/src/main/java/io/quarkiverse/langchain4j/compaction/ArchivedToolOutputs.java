package io.quarkiverse.langchain4j.compaction;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import io.smallrye.common.annotation.Experimental;

/**
 * A tool that lets the model read tool outputs pruned from its context, page by page. Add it to the tools of an AI
 * service ({@code @RegisterAiService(tools = ArchivedToolOutputs.class)}) and set
 * {@code quarkus.langchain4j.compaction.tool-output-pruning.retrieval=tool}, so that pruned outputs tell the model how
 * to retrieve them.
 */
@ApplicationScoped
@Experimental("This feature is experimental and the API is subject to change")
public class ArchivedToolOutputs {

    /**
     * Name of the retrieval tool, used in the stubs of pruned outputs.
     */
    public static final String TOOL_NAME = "retrieveToolOutput";

    /**
     * Maximum number of characters returned by one call.
     */
    public static final int PAGE_CHARS = 4000;

    private final ToolOutputArchive archive;

    @Inject
    public ArchivedToolOutputs(ToolOutputArchive archive) {
        this.archive = archive;
    }

    @Tool(name = TOOL_NAME, value = "Reads a tool output that was removed from the conversation to save space. "
            + "Returns at most " + PAGE_CHARS + " characters starting at the given offset.")
    public String retrieveToolOutput(@ToolMemoryId Object memoryId,
            @P("The reference given in place of the removed output") String reference,
            @P("The offset of the first character to read, 0 to start from the beginning") int offset) {
        return archive.get(memoryId, reference)
                .map(output -> page(output, offset))
                .orElse("No archived output with reference '" + reference
                        + "' (it is no longer available). Call the original tool again if you need its output.");
    }

    static String page(String output, int offset) {
        int from = Math.max(0, Math.min(offset, output.length()));
        int to = Math.min(output.length(), from + PAGE_CHARS);
        String page = output.substring(from, to);
        if (to < output.length()) {
            return page + "\n[" + (output.length() - to) + " more characters: call " + TOOL_NAME + " with offset "
                    + to + " to continue]";
        }
        return page;
    }
}
