package io.quarkiverse.langchain4j.skills.runtime;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import io.quarkiverse.langchain4j.runtime.skills.SkillsConfigurator;

@ApplicationScoped
public class DefaultSkillsConfigurator implements SkillsConfigurator {

    @Inject
    SkillsToolProvider skillsToolProvider;

    @Override
    public ToolProvider createToolProvider(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return skillsToolProvider;
        }
        List<Skill> filtered = filterAndValidate(skillNames);
        return Skills.from(filtered).toolProvider();
    }

    @Override
    public String formatAvailableSkills(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            Skills skills = skillsToolProvider.getSkills();
            return skills != null ? skills.formatAvailableSkills() : "";
        }
        List<Skill> filtered = filterAndValidate(skillNames);
        return Skills.from(filtered).formatAvailableSkills();
    }

    private List<Skill> filterAndValidate(List<String> skillNames) {
        List<Skill> allSkills = skillsToolProvider.getAllSkills();
        Set<String> requested = new HashSet<>(skillNames);
        List<Skill> filtered = allSkills.stream()
                .filter(s -> requested.contains(s.name()))
                .toList();

        Set<String> found = filtered.stream().map(Skill::name).collect(Collectors.toSet());
        Set<String> missing = new HashSet<>(requested);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            List<String> available = allSkills.stream().map(Skill::name).toList();
            throw new IllegalArgumentException(
                    "Unknown skill name(s): " + missing + ". Available skills: " + available);
        }
        return filtered;
    }
}
