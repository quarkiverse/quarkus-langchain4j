package io.quarkiverse.langchain4j.skills.runtime;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.Skills;

/**
 * A tool provider that delegates to a skills ToolProvider created by upstream langchain4j code.
 * The reason for this indirection is to be able to register this provider under a separate type in the CDI container,
 * for better clarity when there are multiple tool providers.
 */
public class SkillsToolProvider implements ToolProvider {

    private final ToolProvider delegate;
    private final Skills skills;

    public SkillsToolProvider(ToolProvider delegate, Skills skills) {
        this.delegate = delegate;
        this.skills = skills;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return delegate.provideTools(request);
    }

    public Skills getSkills() {
        return skills;
    }
}
