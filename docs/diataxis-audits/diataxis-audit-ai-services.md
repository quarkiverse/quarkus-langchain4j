# Diataxis Audit: ai-services.adoc

## Overall Assessment

`ai-services.adoc` is titled "AI Services Reference" but contains a significant mix of all four Diataxis types: reference tables, how-to procedures, tutorial-style introductions, and explanatory context. The title sets a reference expectation, but roughly half the content is procedural (how-to) or explanatory (concept). Separating these types would make each section more effective for its intended audience.

## What the Docs Do Well

1. **Strong reference table for `@RegisterAiService`** (lines 50-103): The attributes table follows clean reference conventions — structured, consistent columns, factual descriptions. This is textbook Diataxis reference content.

2. **Illustrative code snippets in annotation sections** (lines 38-46, 121-124, 138-145): Short code blocks that demonstrate annotation usage in context without forming step-by-step procedures. These are legitimate reference illustrations.

3. **Clear cross-references to related guides** (lines 76, 86, 345, 400, 492-498): The document links out to dedicated pages for RAG, function calling, and model configuration rather than duplicating that content inline. This respects Diataxis boundaries.

4. **Neutral, factual tone in annotation descriptions** (lines 116-173): The `@SystemMessage`, `@UserMessage`, `@MemoryId`, and `@ImageUrl` sections describe what each annotation does without unnecessary digression. Solid reference style.

## Opportunities to Strengthen Diataxis Alignment

1. **Lines 6-30 — Overview is a tutorial masquerading as reference introduction.**
   The "Overview" section walks the reader through creating a `GreetingService` interface, injecting it, and calling a method — a step-by-step learning path ("Inject and use the generated service"). This is tutorial content. The reference document should open with a concise, factual description of what AI Services are, then link to a tutorial for the guided introduction. This content should move to a separate tutorial file.

2. **Lines 60, 274 — Use of "we recommend" breaks reference neutrality.**
   Reference content should describe, not opine. Line 60 ("we recommend using `@ToolBox`") and line 274 ("each model needs to be given a name") should either state the options factually or move the recommendation to a how-to guide. In reference docs, replace "we recommend" with a factual note like "Use `@ToolBox` to scope tools per method."

3. **Lines 186-203 — Chat Memory Management mixes explanation with how-to.**
   This section explains *what* chat memory is (concept content: "AI Services manage conversational state," "memory can be persisted in various ways"), then switches to a procedural instruction ("To customize the memory provider..."). The explanation should move to a concept file; the customization step should move to a how-to file.

4. **Lines 205-237 — LLM Response Mapping is a how-to guide inside a reference page.**
   This section instructs the reader how to map responses ("You can disable automatic schema insertion via configuration") and provides steps to achieve a goal. It should move to a how-to guide. Only the factual description of supported return types and schema behavior belongs in reference.

5. **Lines 239-267 — Streaming Responses is a how-to guide.**
   "Define the method to return a reactive stream" is a procedural instruction for achieving a goal. "You can consume the stream internally" and "Or expose the stream directly in REST endpoints" present two approaches to a practical task. This entire section should move to a how-to guide.

6. **Lines 269-307 — Configuring the chat model mixes explanation with how-to.**
   "While LLMs are the base AI models, the chat language model builds upon them" is explanatory context. "Each model needs to be given a name, which is then referenced by the AI service like below" is procedural guidance. The configuration example with `m1`, `m2`, `m3` is a how-to walkthrough. This section should split: explanation to a concept file, configuration steps to a how-to file.

7. **Lines 310-344 — Tools Integration is a how-to guide.**
   This section walks the reader through defining tools, configuring them, and using `@ToolBox` — a procedural sequence for achieving tool integration. It should move to the existing `function-calling.adoc` or a dedicated how-to file, not sit in a reference page.

8. **Lines 347-388 — Tool Error Handling is valid reference content but could be better structured.**
   The `@HandleToolArgumentError` and `@HandleToolExecutionError` sections describe annotations with illustrative code. This is legitimate reference content. However, the "Method requirements" list at line 383-387 should be formatted as a table for consistency with the `@RegisterAiService` attributes table.

9. **Lines 402-430 — Moderation mixes how-to with reference.**
   "By default... users can opt in to having the LLM moderate content" is explanatory. "For moderation to work, the following criteria need to be met" is a requirements list (reference). The custom supplier code block is a how-to procedure. These three concerns should separate.

10. **Lines 432-490 — Working with Images is a how-to guide.**
    "Create the image instance like this" is a procedural instruction. The summary list at lines 476-490 duplicates earlier code snippets. This section should move to a how-to guide and the duplicated summary should be removed.

11. **Line 30 — `IMPORTANT` admonition uses tutorial voice in reference context.**
    "You do **not** need to implement the interface" is a reassurance aimed at learners, not a reference-appropriate statement. In a reference doc, state the fact neutrally: "Quarkus LangChain4j generates the interface implementation automatically."

## Summary Scorecard

| Quadrant | Current Coverage | Compliance | Notes |
|----------|-----------------|------------|-------|
| **Tutorial** | Lines 6-30 (embedded) | Mixed | Tutorial intro buried in reference page |
| **How-to Guide** | Lines 205-344, 432-490 (embedded) | Mixed | Multiple how-to sections scattered throughout |
| **Reference** | Lines 32-173, 347-388 | Strong | Annotations section is well-structured reference |
| **Explanation** | Lines 186-203, 269-275 (embedded) | Mixed | Explanation fragments mixed with procedures |

## Closing

The annotations reference core (lines 32-173) is strong, well-structured Diataxis reference content. Separating the procedural and explanatory content into dedicated files will make each piece more effective for its intended audience.
