# AGENTS.md

Guidance for AI agents and human contributors working on Quarkus LangChain4j.

Read [AI_POLICY.md](AI_POLICY.md) first. It applies to every contribution, including the ones prepared with an agent.

This repository follows the conventions of Quarkus core. Read the [Quarkus core AGENTS.md](https://github.com/quarkusio/quarkus/blob/main/AGENTS.md) and, before the matching kind of work, the skills in [.agents/skills](.agents/skills): `coding-style`, `writing-tests`, `working-with-config`, `pull-requests` and `writing-build-steps`. This file only adds what is specific to Quarkus LangChain4j or stricter than core.

The skills are verbatim copies from Quarkus core, see [UPSTREAM.md](.agents/skills/UPSTREAM.md) for the commit and `.agents/sync-core-skills.sh` to refresh them. Do not edit them here. Their paths and commands (`extensions/<name>/`, `integration-tests/<name>/`) refer to the core layout, so map them to the modules of this repository. Where a skill and this file disagree, this file wins. Other core skills, such as `manage-deprecations`, are not copied: read them in core when a task needs them.

## Lambdas and method references

- Avoid lambdas and method references everywhere: runtime code, deployment code and tests. Core asks to minimize them in runtime code, here the rule is stricter.
- In `runtime` modules it is a hard requirement. Use anonymous classes, loops and `if` statements, and avoid streams.
- The only accepted exception is a case where every alternative is clearly worse. Explain it in the PR.

## Deployment modules

- Create `DotName` constants from class literals when the class is on the deployment classpath, and keep them as `static final` fields. Use a String only when the class is not available to the deployment module.
- Reuse the existing `DotName` holders (`DotNames`, `LangChain4jDotNames` and the per-module `*DotNames` classes) before declaring a new constant.
- Resolve and validate at build time whatever the build already knows, and pass the result to the recorder.

## Configuration

- Endpoints and API keys are runtime configuration.
- When the default is owned by upstream LangChain4j, do not copy it with `@WithDefault`. Declare the property as `Optional` and document the default with `@ConfigDocDefault`. Core's rule against `Optional` defaults applies to defaults that Quarkus owns.
- When you change a default of a model provider, check that proxy support, authentication and retries still work, and report anything that stops working as a breaking change.

## Tests

- The `writing-tests` skill prefers `QuarkusExtensionTest`. This repository still has many `QuarkusUnitTest` tests, so follow the style of the neighbouring tests in the module you are changing.
- Mock the wire, not the classes. Use WireMock or a stub chat model, and Vert.x if a test needs a fake HTTP server. Avoid Mockito mocks of internal classes.
- Assert outcomes, such as what the client actually sends or what the AI service returns. Do not assert internal state of a class.

## Pull requests

- Backports to maintenance branches are cherry-picks of the original commits, not squashes.
