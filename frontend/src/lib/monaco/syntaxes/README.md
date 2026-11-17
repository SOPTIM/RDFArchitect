# TextMate grammars

`turtle.tmLanguage.json` and `sparql.tmLanguage.json` are copied verbatim from
the CIMNotebook VS Code extension in SOPTIM's OpenCGMES repository
(`cimnotebook/vscode/syntaxes/`, commit `799f402`, extended for embedded SPARQL
in `a002ddd`).

Both repositories are SOPTIM AG under Apache-2.0, so the copy carries no
third-party obligation. They are original work, not derived from an upstream
community grammar.

Keeping them as copies rather than a shared package is deliberate: the extension
and this editor version their grammars independently, and a copy of 13 KB of
JSON is cheaper than a release pipeline for it. When highlighting drifts from
CIMNotebook, re-copy the two files.

The `meta.embedded.block.sparql` content scope in the Turtle grammar is what
makes SPARQL inside `sh:select` highlight as SPARQL. It resolves through the
`source.sparql` include, so both grammars have to be registered together — see
`../textmate.js`.
