# Diataxis Audit: tool-guardrails.adoc

## Overall Assessment

`tool-guardrails.adoc` (881 lines) is the largest file in the quarkus-langchain4j docs. It contains well-written content covering three Diataxis types — concept (~150 lines), how-to (~550 lines), and reference (~180 lines) — interleaved throughout. The how-to content dominates and is the file's primary strength, but the reference sections (API interfaces, metrics) and concept material (architecture, best practices) are distinct enough to warrant separation.

## Section Classification

| Lines | Section | Type |
|-------|---------|------|
| 1-17 | Intro + importance note | concept |
| 19-31 | Overview (input/output stages) | concept |
| 33-77 | Quick Start | howto |
| 79-191 | Basic Usage (input + output) | howto |
| 192-283 | Guardrail Interfaces | reference |
| 285-320 | Multiple Guardrails + ordering | concept + howto |
| 322-433 | Working with Parameters | howto |
| 435-556 | Common Use Cases (4 recipes) | howto |
| 558-588 | CDI Integration | howto |
| 590-603 | Event Loop Limitation | reference |
| 605-704 | Advanced Topics + Error Handling | howto |
| 706-755 | Best Practices | concept |
| 757-801 | Testing | howto |
| 803-873 | Observability and Metrics | reference |
| 875-881 | Going Further | cross-refs |

## Strengths

1. **Excellent how-to recipes**: The Common Use Cases section (lines 435-556) provides four self-contained, copy-paste-ready recipes. Each addresses a clear user goal (security, rate limiting, privacy, cost control).
2. **Clean reference material**: The Guardrail Interfaces section (lines 192-283) and Observability section (lines 803-873) are neutral, descriptive, and well-structured — they can be extracted as-is.
3. **Practical Quick Start**: Lines 33-77 get users productive quickly with a complete, annotated example.
4. **Strong CDI integration coverage**: Lines 558-588 clearly demonstrate dependency injection, scope selection, and config property injection.

## Opportunities

1. Lines 1-31 (concept intro) should be separated — it explains *what* tool guardrails are and *why* they matter, which is understanding-oriented content mixed into a predominantly procedural file.
2. Lines 192-283 (API interfaces) should be extracted as reference — the interface definitions, request context fields, and result option enumerations are pure API surface documentation.
3. Lines 803-873 (metrics/events) should join the reference file — metric names, tags, and CDI event fields are information-oriented content.
4. Lines 706-755 (best practices) should move to concept — design guidelines presented via an illustrative example belong with the conceptual material.
5. Lines 590-603 (event loop limitation) should move to reference — it's an architectural constraint that practitioners need to look up while working.
