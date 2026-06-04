# Design Spec — C1 Quick Wins & Safety
**Date:** 2026-06-04 (rev 2)
**Issue:** quarkiverse/quarkus-langchain4j#2527
**Branch:** feat/issue-2527-c1-quick-wins-safety

---

## Scope

All Chapter 1 remaining items after D-2 (shipped in #2526):

| ID | Description | Kind |
|----|-------------|------|
| D-1 | `jsonRpcProvider` build step not gated on dev mode | One-liner fix |
| C-7 | `loadClassSafe` / `eagerlyInitRootAgents` use TCCL | One-liner fix |
| F-7 | `hasAnyInterceptorBindings` misses bindings on parent interfaces | Correctness fix |
| S-3 | `markCdiBeanParametersAsUnremovable` covers only `@ChatModelSupplier` | Loop fix |
| F-3 | `@Fallback(fallbackMethod=...)` always fails at runtime on proxy classes | Build error |
| F-4 | `@Retry(retryOn=...)` targets types agent wraps in `AgentInvocationException` | Build warning |
| F-5 | `@Transactional + @Retry` on agent method — partial commits + stale scope | Build warning |
| F-1 | `@Retry` and `@CircuitBreaker` untested in `FaultToleranceTest` | Test addition |
| L-1 | No `@ConfigMapping` for the agentic module | Config skeleton |
| C-6 | `parallelExecution()` sequential `future.get()` loop | Upstream PR only |

---

## D-1 — `jsonRpcProvider` dev-mode gate

**File:** `agentic/deployment/.../devui/AgenticDevUIProcessor.java`

Add `(onlyIf = IsDevelopment.class)` to the `@BuildStep` annotation on `jsonRpcProvider` at line 91. Three other build steps in the same class already carry this guard.

**Test:** `AgenticJsonRpcService` is a plain class (no CDI scope annotation) registered via `JsonRPCProvidersBuildItem` — it is not a CDI bean and cannot be checked via CDI lookup. The test for D-1 is a regression test: run the existing smoke tests in test profile (which is non-dev) and verify they still pass. The negative assertion ("service is not registered in production") is not directly testable via `@QuarkusUnitTest` without custom DevUI test infrastructure; this is acceptable given the fix is a one-annotation change with obvious effect.

---

## C-7 — `loadClassSafe` and `eagerlyInitRootAgents` classloader

**File:** `agentic/runtime/.../AgenticRecorder.java`

Replace `Thread.currentThread().getContextClassLoader()` with `AgenticRecorder.class.getClassLoader()` in both:
- `loadClassSafe(AiAgentCreateInfo info)` — line 188, inside the `createAiAgent` `Function` — **runtime call site, highest risk.** This lambda executes during synthetic bean creation on whatever thread the CDI container uses — may be a Vert.x I/O thread or a virtual thread scheduled by `Executors.newVirtualThreadPerTaskExecutor()`. TCCL on these threads is not the deployment classloader.
- `eagerlyInitRootAgents(Set<String> rootAgentClassNames)` — line 72, called exclusively from `enableDevModeMonitoring`, which is `@BuildStep(onlyIf = IsDevelopment.class)` — **dev-mode-only call site, lower risk.** Startup generally runs on the main thread where TCCL is correct, but the pattern is wrong regardless.

**Code comment (both sites):**
```java
// Do not use Thread.currentThread().getContextClassLoader() here — TCCL is not
// guaranteed to be the deployment classloader on Vert.x I/O threads or virtual
// threads spawned by Executors.newVirtualThreadPerTaskExecutor(). Use the
// recorder's own classloader, which is always the deployment classloader.
```

**Test:** No viable unit test path — the failure mode requires execution on a Vert.x worker thread in a scenario where TCCL diverges. Do not write a compilation-only test. The fix is structural; code review is the verification mechanism.

---

## Shared utilities — `ValidationUtil`

Two new helpers belong in `ValidationUtil` (not `AgenticProcessor`). `ValidationUtil` already exists as the home for shared validation logic; `AgenticProcessor.java` is 793 lines. Adding helpers there signals they are reusable across build steps and future processors.

### `transitiveInterfaces(ClassInfo, IndexView) → Set<ClassInfo>`

Returns the agent interface itself plus all transitive parent interfaces, BFS order, as `ClassInfo` objects (not `DotName` — callers need `ClassInfo` to call `classAnnotations()` and `method()` without extra index lookups):

```java
static Set<ClassInfo> transitiveInterfaces(ClassInfo start, IndexView index) {
    Set<ClassInfo> result = new LinkedHashSet<>();
    Deque<ClassInfo> queue = new ArrayDeque<>();
    queue.add(start);
    while (!queue.isEmpty()) {
        ClassInfo current = queue.poll();
        if (!result.add(current)) continue;
        for (DotName parentName : current.interfaceNames()) {
            ClassInfo parent = index.getClassByName(parentName);
            if (parent != null) queue.add(parent);
        }
    }
    return result;
}
```

### `hasAnnotation(Collection<AnnotationInstance>, Set<DotName>) → boolean`

```java
static boolean hasAnnotation(Collection<AnnotationInstance> annotations, Set<DotName> names) {
    for (AnnotationInstance ann : annotations) {
        if (names.contains(ann.name())) return true;
    }
    return false;
}
```

**Why `ClassInfo.annotations()` is insufficient for F-7:** In Jandex, `ClassInfo.annotations()` (no argument) returns a `Map` of all annotation instances within this class body — on the class itself, its fields, its methods. It does NOT traverse parent interfaces. Java annotation inheritance (`@Inherited`) applies only to class-to-subclass inheritance, not interface extension; Jandex does not fabricate inherited annotations for interface hierarchies. The audit's "one word" prescription (`annotations()`) is insufficient and the full interface traversal is required.

---

## F-7 — Inherited interceptor binding detection

**File:** `agentic/deployment/.../AgenticProcessor.java` (and `ValidationUtil`)

### Problem

`hasAnyInterceptorBindings` misses interceptor bindings on parent interfaces in two scenarios:

**Scenario A — class-level annotation on parent:**
```java
@Timeout(1000)
interface BaseAgent { ... }
interface MyAgent extends BaseAgent { ... }  // @Timeout missed at class level
```

**Scenario B — method-level annotation on parent (redeclared method):**
```java
interface BaseAgent {
    @Timeout(1000)
    String chat(String input);
}
interface MyAgent extends BaseAgent {
    @Agent("...")
    String chat(String input);  // @Timeout on BaseAgent.chat() missed
}
```

### Fix

Change signature: `hasAnyInterceptorBindings(DetectedAiAgentBuildItem, Set<DotName>, IndexView) → boolean`

Caller `cdiSupport` gains `CombinedIndexBuildItem indexBuildItem` parameter; passes `indexBuildItem.getIndex()`.

```
1. hierarchy = ValidationUtil.transitiveInterfaces(agent.getIface(), index)

2. Class-level check:
   for each classInfo in hierarchy:
       for each ann in classInfo.classAnnotations():
           if ann.name() in interceptorBindings → return true

3. Method-level check (for each agentic method):
   - check method.annotations() directly
   for each classInfo in hierarchy (skip agent.getIface() — already covered above):
       parentMethod = classInfo.method(method.name(), method.parameterTypes())
       if parentMethod != null:
           check parentMethod.annotations()
   (classInfo.method(...) returns null if the parent doesn't declare that method — null-safe)
```

**Known limitation:** `@Timeout` on a non-agentic method (e.g., a helper method on the interface that is not in `getAgenticMethods()`) is still missed. Interceptor bindings at the class level ARE caught by the class-level check above. Method-level bindings on non-agentic methods are out of scope — they do not affect the agent method proxy and would be a separate audit finding.

**Test:** New `@QuarkusUnitTest` with an agent interface extending a base interface where `@Timeout` is declared at the base interface CLASS level AND on a base interface method. Verify the interception proxy is created by asserting timeout behavior at runtime. Use `FaultToleranceTest` as the model.

---

## S-3 — `markCdiBeanParametersAsUnremovable` covers all CDI-capable supplier types

**File:** `agentic/deployment/.../AgenticProcessor.java`

### Problem

Current implementation only iterates `item.getChatModelSupplier()`. Other supplier methods with `@CdiBean` parameters — if they were allowed — would silently produce `UnsatisfiedResolutionException` at runtime. The fix establishes the correct pattern for when S-1 (full CDI auto-wire) ships.

### Constant

```java
private static final List<DotName> ALL_CDI_CAPABLE_SUPPLIER_ANNOTATIONS = List.of(
    AgenticLangChain4jDotNames.CHAT_MODEL_SUPPLIER,
    AgenticLangChain4jDotNames.CHAT_MEMORY_SUPPLIER,
    AgenticLangChain4jDotNames.CHAT_MEMORY_PROVIDER_SUPPLIER,
    AgenticLangChain4jDotNames.CONTENT_RETRIEVER_SUPPLIER,
    AgenticLangChain4jDotNames.RETRIEVAL_AUGMENTER_SUPPLIER,
    AgenticLangChain4jDotNames.TOOL_SUPPLIER,
    AgenticLangChain4jDotNames.TOOL_PROVIDER_SUPPLIER,
    AgenticLangChain4jDotNames.AGENT_LISTENER_SUPPLIER
    // PARALLEL_EXECUTOR excluded: executor config, not a supplier; validated to have no parameters
);
```

### Fix

Rewrite to scan all methods across the full interface hierarchy (to match what S-1 will need):

```
markCdiBeanParametersAsUnremovable gains CombinedIndexBuildItem parameter

for each item in agents:
    for each classInfo in ValidationUtil.transitiveInterfaces(item.getIface(), index):
        for each method in classInfo.methods():
            if method has any annotation in ALL_CDI_CAPABLE_SUPPLIER_ANNOTATIONS:
                for each param in method.parameters():
                    if param has @CdiBean:
                        produce UnremovableBeanBuildItem(param.type().name())
```

**Why traverse parent interfaces:** A natural usage pattern is a base agent interface declaring supplier methods, extended by concrete agent interfaces. Without traversal, `@CdiBean` parameters on parent interface supplier methods would be missed.

### Test

`@ChatModelSupplier` is the **only** supplier annotation that currently permits parameters — all others call `validateNoMethodParameters` and reject parameterized methods at build time. The S-3 test must use `@ChatModelSupplier`.

Use `CdiChatSupplierParameterResolverTest` as the model. The new test adds a **second** `@CdiBean`-annotated parameter type that is not already marked unremovable by the existing test, to confirm the fix works across parameter iterations.

**This test is necessarily narrow** — it only covers `@ChatModelSupplier` because other supplier types don't permit parameters today. The fix is a dormant prerequisite for S-1 work; the broader test coverage lands when S-1 expands parameter support to other supplier types.

---

## F-3 — `@Fallback(fallbackMethod=...)` build error

**Location:** Add `validateFallback(ClassInfo iface)` to the existing `validate(DetectedAiAgentBuildItem)` call chain in `AgenticProcessor`.

**Why `validate()` not a new build step:** F-3 is structural per-agent validation — it detects whether a single agent interface is correctly configured. The established pattern is that structural per-agent checks go in `validate()` (line 117), which is called per-agent from `detectAgents`. A new build step would add Quarkus build-step overhead for something that fits the existing call chain.

**Implementation:**

Check both class-level and method-level `@Fallback` (using DotName string literal to avoid hard compile dependency):

```java
private static final DotName FALLBACK = DotName.createSimple("org.eclipse.microprofile.faulttolerance.Fallback");

private static void validateFallback(ClassInfo iface) {
    for (AnnotationInstance fallback : iface.annotations(FALLBACK)) {
        AnnotationValue fallbackMethod = fallback.value("fallbackMethod");
        if (fallbackMethod != null && !fallbackMethod.asString().isEmpty()) {
            AnnotationTarget target = fallback.target();
            String location = target.kind() == AnnotationTarget.Kind.CLASS
                ? "class '" + iface.name() + "'"
                : "method '" + target.asMethod().name() + "' of class '" + iface.name() + "'";
            throw new IllegalConfigurationException(
                "Agent " + location + " uses @Fallback(fallbackMethod=\"" + fallbackMethod.asString() + "\"). "
                + "Agent interfaces are dynamic proxies — fallback method name resolution always fails at runtime. "
                + "Use FallbackHandler<T> instead: @Fallback(YourFallbackHandler.class)");
        }
    }
}
```

Note: `ClassInfo.annotations(DotName)` returns all annotations with that name anywhere in the class, including on methods — suitable for both class-level and method-level detection in one pass.

**Test:** New file `validation/FallbackMethodOnAgentTest.java` — `QuarkusUnitTest.assertException()` pattern, verify `IllegalConfigurationException` with message containing `"fallback method name resolution always fails"` or similar fragment. Follow `NonStaticReturnParallelExecutorTest` as model.

---

## F-4 and F-5 — FaultTolerance interaction warnings

**Location:** New `@BuildStep @Produce(ServiceStartBuildItem.class)` method `validateFaultToleranceInteractions` in `AgenticProcessor`. Unlike F-3, F-4/F-5 are multi-annotation cross-checks that do not cleanly fit per-agent structural validation — a dedicated build step is appropriate.

**DotNames (string literals, no compile dependency):**
```java
private static final DotName RETRY = DotName.createSimple("org.eclipse.microprofile.faulttolerance.Retry");
private static final DotName TRANSACTIONAL = DotName.createSimple("jakarta.transaction.Transactional");
private static final DotName AGENT_INVOCATION_EXCEPTION =
    DotName.createSimple("dev.langchain4j.agentic.agent.AgentInvocationException");
// Supertypes that also satisfy retryOn — retry will fire for these
private static final Set<DotName> RETRY_SUPERTYPES = Set.of(
    DotName.createSimple("java.lang.RuntimeException"),
    DotName.createSimple("java.lang.Exception"),
    DotName.createSimple("java.lang.Throwable"),
    AGENT_INVOCATION_EXCEPTION
);
```

### F-4 — `@Retry(retryOn=...)` warning

Condition: `@Retry` on an agent method, `retryOn` attribute is set, and NONE of the listed types is in `RETRY_SUPERTYPES`.

Log WARN:
> "Agent method '{class}#{method}' uses @Retry(retryOn=...) but agent exceptions are wrapped in AgentInvocationException. The retryOn types {types} will not match the thrown exception. Add AgentInvocationException to retryOn, or remove retryOn to retry on all exceptions."

### F-5 — `@Transactional + @Retry` warning

Check the cross-product: the combination is dangerous whenever a retry can re-enter a method scope that was transactional on the first attempt.

For each agent interface:
1. Collect `hasClassTransactional` = interface has class-level `@Transactional`
2. Collect `hasClassRetry` = interface has class-level `@Retry`
3. For each agentic method:
   - `methodTransactional` = method has `@Transactional` OR `hasClassTransactional`
   - `methodRetry` = method has `@Retry` OR `hasClassRetry`
   - If BOTH `methodTransactional` AND `methodRetry` → WARN

Log WARN:
> "Agent method '{class}#{method}' combines @Transactional and @Retry. AgenticScope is not a JTA resource — on retry, the second attempt re-enters after the first transaction has already closed, leaving partial scope state unrolled. Ensure this combination is intentional."

### F-4/F-5 testing

**Warning testability:** Build-step `log.warn()` calls go through JBoss Logger during augmentation. No existing test in this project captures build-time warnings via `@QuarkusUnitTest`. Adding a log handler during augmentation would require non-trivial test infrastructure (JBoss Logger handler registration in the augmentation classloader, not the test classloader). This is not a pattern established in this codebase.

**Test scope for F-4 and F-5:** verify the application boots without exception when the triggering annotation combinations are present. Do not attempt to assert warning text in `@QuarkusUnitTest`. Add a comment in each test noting that the warning is build-time only.

If build-time warning capture becomes a recurring need, a follow-up should establish the pattern (possibly by producing a `AgentConfigurationWarningBuildItem` that tests can consume, rather than relying on log capture).

---

## F-1 — `@Retry` and `@CircuitBreaker` test cases

**File:** `agentic/deployment/.../FaultToleranceTest.java`

Add test cases to the existing `FaultToleranceTest`:
- `@Retry` on an agent method — verify the agent is retried on failure (mock the chat model to fail N times then succeed)
- `@CircuitBreaker` on an agent method — verify the circuit opens after threshold failures

No code change required in the main module. Effort 1 as listed in the audit.

---

## L-1 — `@ConfigMapping` skeleton

**File:** New `agentic/runtime/.../AgenticRuntimeConfig.java`

Add a minimal `@ConfigMapping(prefix = "quarkus.langchain4j.agent")` interface covering the most immediately useful properties:
- `OptionalInt defaultMaxIterations()` — caps loop/planner iteration counts
- `boolean devUiEagerInit() default true` — gates `eagerlyInitRootAgents` (covers L-5 fix)

This is a skeleton — the mapping is registered but no existing code reads it yet. It establishes the `quarkus.langchain4j.agent.*` namespace so that C3/C4/C6 chapters can add to it without a rename. Wire it into `AgenticProcessor` via a standard `@BuildStep` consuming a `RunTimeConfigBuilderBuildItem` or `ConfigBuildItem`. Document the namespace in `AGENTIC-NATIVE-AUDIT.md` as resolved.

**Test:** Verify the config properties are readable via `QuarkusUnitTest` with `overrideConfigKey("quarkus.langchain4j.agent.dev-ui.eager-init", "false")` — confirm the app boots and the property is accessible.

---

## C-6 — Upstream PR to `langchain4j-agentic`

**No Quarkus-side change.** Per `protocols/upstream/upstream-contribution-framing.md`, the `CompletableFuture.allOf()` fix in `PlannerBasedInvocationHandler.parallelExecution()` belongs in upstream `langchain4j-agentic`.

File the upstream PR framed as: "Parallel execution currently waits on submission order rather than completion order, adding latency proportional to the slowest task submitted first."

---

## Implementation order

Sequential on this branch, each with TDD before coding:

1. **D-1** — one annotation, regression test via existing smoke tests
2. **C-7** — one-liner ×2, code comment only (no test)
3. **ValidationUtil helpers** — `transitiveInterfaces` + `hasAnnotation` (tested via F-7 test)
4. **S-3** — loop fix using helpers, new @ChatModelSupplier test
5. **F-7** — `hasAnyInterceptorBindings` with interface traversal, new Timeout inheritance test
6. **F-3** — `validateFallback` in `validate()`, new validation test
7. **F-4 + F-5** — `validateFaultToleranceInteractions` build step, boot-only tests
8. **F-1** — add test cases to `FaultToleranceTest`
9. **L-1** — `AgenticRuntimeConfig` skeleton + wire-up
10. **C-6** — file upstream PR

All committed in one or more commits on this branch, single PR (#2527) after code review.
