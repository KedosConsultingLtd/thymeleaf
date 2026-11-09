# Security audit of the Kedos performance changes (2026-11-09)

## Scope and method

**Changes audited, against upstream `thymeleaf-3.1.6.RELEASE`:**
- parse filters (whitespace collapsing, static-run coalescing, and the static-markup bypass in
  `ProcessorTemplateHandler`);
- the `ExpressionUtils` per-class cache;
- the SpEL ACL property-accessor caching and shared standalone accessors (`thymeleaf-spring6`);
- the `StandardLinkBuilder` rewrite;
- the testing-library dialect hook.

**Threat model.** Templates are written by trusted developers. Attackers control *model data*: request
parameters, shopper input, and merchant/CMS content (multi-tenant SaaS). In scope were:
- the expression sandbox (server-side template injection);
- XSS and output integrity, including `javascript:` link filtering;
- cross-request or cross-tenant leakage through shared state;
- thread safety;
- denial of service.

**Method.** A multi-agent workflow ran in three stages:
1. Four independent auditors, one per attack surface: expression sandbox, output integrity, link building, and
   shared state / concurrency / DoS.
2. Every finding went to an adversarial verifier, who had to reproduce it with a running program against the
   changed jars, or refute it.
3. A completeness critic reviewed coverage and gaps.

Auditors compared behaviour with upstream classes compiled from the 3.1.6 sources. Each confirmed finding below
was then fixed and covered by a regression test. Every regression test was checked to **fail on the pre-fix
code**.

## Result

**No change allows reading a forbidden member or reaching a blocked type.** Every stale-cache path fails closed.
Auditors tried 13 expression shapes, three kinds of context, interpreted and compiled mode, safe navigation,
projection, selection, static access and null targets.

Seven issues were confirmed and fixed. One was refuted. The rest are accepted as documented limitations.

| # | Finding (merged duplicates) | Verified severity | Status |
|---|---|---|---|
| 1 | **SpEL `Indexer` cache poisoning.** `obj['prop']` / `obj[var]` cached the self-returning ACL accessor without SpEL's "is it still in this context?" check, and never retried after `AccessException`. Mixing standalone and MVC rendering, or using `allowedClassOverridesForViews`, made such expressions fail for good: an availability bug, not a sandbox bypass (it failed closed). | high | **Fixed**: `read()` now runs SpEL's own accessor search in the *current* context (exact type, then supertype, then generic accessors; first `canRead()` wins) and never throws on a context mismatch. ACL is re-applied whenever the ACL accessor is chosen. |
| 2 | **Switched-mode inlining skipped in coalesced text.** Under an ancestor `th:inline="javascript"`, `"css"` or `"text"`, nested static text containing `/*[- … -]*/`, `/*[+ … +]*/` or `[# th:if=…]…[/]` was coalesced and never inlined. Prototype-only content leaked, and **role-gated `[# th:if]` blocks rendered for everyone**. | medium (authorisation-gated content disclosed) | **Fixed**: text, comments and CDATA containing any `[` are never static. Every inlining syntax in every mode starts with `[`. |
| 3 | **Parse filters applied to runtime strings.** `TemplateManager.parseString` (used by `IModelFactory.parse`, `th:utext` re-parses, inliner and attribute re-parses) went through the filters. Model data parsed by a dialect came back coalesced, hiding `<script>` / `on*` elements from model-walking sanitisers. Whitespace in CMS HTML was rewritten. Deeply nested attacker HTML could overflow the stack. | high (via custom dialects) | **Fixed**: parse filters apply to template resources only; `parseString` never filters. |
| 4 | **Third-party model processors see coalesced markup.** Layout Dialect walks a decorator's `<head>`: titles were duplicated and page scripts moved before the decorator's CSP `<meta>`. | medium | **Fixed**: coalescing disables itself (with a warning) when any dialect with element model processors is configured, unless the application lists that dialect as trusted (`new StaticRunCoalescingDialect(Set.of(MyDialect.class))`). The Standard and Spring Standard dialects have none. |
| 5 | **Recursive tree/emitter.** About 12,000 nesting levels caused a `StackOverflowError` that upstream doesn't have. | low (only reachable through #3, now closed) | **Fixed**: templates nested deeper than 256 levels are passed through unchanged. |
| 6 | **Link builder: null parameter name** threw an NPE (upstream matched `{null}`). | info | **Fixed** |
| 7 | **Link builder: aliasing.** No defensive copy meant that a `computeContextPath` override modifying the map, or identity/concurrent/odd map types, could change the output. | info | **Fixed**: the copy is kept for any map that is not a plain `HashMap`/`LinkedHashMap`, and whenever `computeContextPath` is overridden. |
| – | A `th:each` whitespace rebuild drops the static flag | – | **Refuted**: that code only runs in TEXT/JS/CSS modes, and coalescing is HTML-only. |

### Accepted limitations (documented, not exploitable)

| Item | Detail |
|---|---|
| A getter that throws is invoked twice when served from SpEL's cache | `PropertyOrFieldReference` retries after any exception from a cached accessor. The render already fails in this case; the only effect is a duplicated side effect of a failing getter. |
| Shared standalone reflective accessor keeps model classes reachable | The JVM-static accessor's class-keyed caches keep model classes (and their class loaders) reachable. Upstream's MVC path already does the same through its static accessor. This only matters for hot reload or per-tenant class loaders, and attackers cannot drive it (property names come from templates). |
| Whitespace collapsing cannot see an ancestor `<pre>` | It can't see one when a fragment is selected from inside it. |

### Not verified at runtime

The critic flagged these as checked only by reading code:
- Spring WebFlux reactive and throttled rendering;
- `thymeleaf-extras-springsecurity6` (`sec:` attributes) inside coalesced regions;
- the two parse filters registered at unusual relative precedences.

The `sec:` processors are tag processors, so their elements are never coalesced. Coalescing is opt-in.

TODO: add WebFlux and Spring Security extras runs to the benchmark/regression setup before enabling coalescing in a
WebFlux application.

## Regression tests

| Test | Covers |
|---|---|
| `ACLPropertyAccessorCachingTest` (spring6) | Forbidden type after allowed type, in one render and across renders. Map keys beat getters. Bracket access across standalone, `ThymeleafEvaluationContext`, per-request override contexts and custom delegate contexts, in any order. Forbidden type through the indexer. 8-thread mixed-context stress run. All with and without the SpEL compiler. |
| `StaticRunCoalescingParseFilterTest` (core) | Switched-mode inlining under an ancestor `th:inline` (`[# th:if]`, `/*[- -]*/`, `/*[+ +]*/`, CSS, text). Runtime-string parses unfiltered (`IModelFactory.parse`, `th:utext` with a post-processor). Untrusted model-processor dialect disables coalescing; a trusted one is allowed. Nesting depths 255, 257 and 20,000. |
| `StandardLinkBuilderEquivalenceTest` (core) | The unmodified 3.1.6 builder is the oracle: about 5,000 base/parameter/context-path combinations on both the copy and copy-free paths, null keys, identity/concurrent/tree maps, and a mutating `computeContextPath` override. |

**Mutation checks** (each regression test fails on the pre-fix code):
- reverting the `Indexer` fix fails 3 SpEL tests;
- removing the ACL re-check from `read()` fails 4;
- restoring the old text rule or filtered `parseString` fails the inlining and runtime-parse tests.
