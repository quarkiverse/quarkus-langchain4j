package io.quarkiverse.langchain4j.skills;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;

/**
 * A simple system message provider that describes which skills are available to the model.
 * If you need the system message to contain some more information apart from that, you can reuse the part
 * that retrieves skill descriptions and add it to your own SystemMessageProvider.
 */
@ApplicationScoped
public class SkillsSystemMessageProvider implements SystemMessageProvider {

    @Override
    public Optional<String> getSystemMessage(Object memoryId) {
        Instance<SkillsToolProvider> skillsToolProvider = CDI.current().select(SkillsToolProvider.class);
        if (skillsToolProvider.isResolvable()) {
            return Optional.of("""
                    You have access to the following skills:
                    %s
                    """.formatted(skillsToolProvider.get().getSkills().formatAvailableSkills()));
        } else {
            return Optional.empty();
        }
    }

}
