package io.quarkiverse.langchain4j.runtime.skills;

import java.util.List;

import dev.langchain4j.service.tool.ToolProvider;

/**
 * SPI for configuring skills on AI services and agents.
 * <p>
 * Implemented by the {@code quarkus-langchain4j-skills} extension. Looked up from CDI
 * by the core and agentic recorders when {@code @Skills} is detected.
 */
public interface SkillsConfigurator {

    /**
     * Creates a {@link ToolProvider} for the given skill names.
     *
     * @param skillNames the names of skills to include; empty list means all loaded skills
     * @return a tool provider exposing the requested skills
     * @throws IllegalArgumentException if any skill name does not exist among the loaded skills
     */
    ToolProvider createToolProvider(List<String> skillNames);

    /**
     * Returns a formatted description of the available skills, suitable for inclusion in a system message.
     *
     * @param skillNames the names of skills to describe; empty list means all loaded skills
     * @return a formatted string describing the available skills
     * @throws IllegalArgumentException if any skill name does not exist among the loaded skills
     */
    String formatAvailableSkills(List<String> skillNames);

    /**
     * Builds the full skills system message for the given skill names.
     */
    default String buildSkillsSystemMessage(List<String> skillNames) {
        return "You have access to the following skills:\n"
                + formatAvailableSkills(skillNames)
                + "\nWhen the user's request relates to one of these skills, "
                + "activate it first using the `activate_skill` tool before proceeding.";
    }
}
