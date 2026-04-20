// @ts-check
// Asciidoctor.js port of Quarkus's TooltipInlineMacroProcessor.java:
// https://github.com/quarkusio/quarkus/blob/main/docs/src/main/java/io/quarkus/docs/generation/TooltipInlineMacroProcessor.java
//
// The Quarkus config-doclet emits `tooltip:enumvalue[description]` for
// enum-valued configuration properties. Without a registered processor the
// macro leaks to rendered HTML as literal text. Quarkus's own docs build
// registers the Java processor above, which discards the description and
// renders just the value in monospace. This module mirrors that behavior for
// the Antora/asciidoctor.js build used by quarkus-langchain4j.
//
// Input : tooltip:filesystem[The `path()` represents a filesystem reference]
// Output: `filesystem`

module.exports.register = function register (registry) {
  if (typeof registry.register === 'function') {
    registry.register(function () {
      this.inlineMacro('tooltip', function () {
        this.process(function (parent, target) {
          return this.createInline(parent, 'quoted', target, { type: 'monospaced' })
        })
      })
    })
  } else if (typeof registry.inlineMacro === 'function') {
    registry.inlineMacro('tooltip', function () {
      this.process(function (parent, target) {
        return this.createInline(parent, 'quoted', target, { type: 'monospaced' })
      })
    })
  }
  return registry
}
