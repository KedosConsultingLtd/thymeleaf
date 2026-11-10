# Kedos Thymeleaf

This repository is Kedos Consulting's fork of [thymeleaf/thymeleaf](https://github.com/thymeleaf/thymeleaf).

**Branches and tags**
- **`3.1-kedos`** (default branch) is upstream's release tag `thymeleaf-3.1.6.RELEASE` plus the Kedos changes below.
- Upstream branches and tags are left as they are.
- Kedos releases are tagged with their version (e.g. `3.1.6.KEDOS-1`) and published to GitHub Packages. See
  [publishing.md](publishing.md).

## Changes from upstream

| Change | Default | Docs |
|---|---|---|
| Parse filters: an extension point for transforming template events at parse time (`IParseFilter`, `IParseFilterDialect`) | – | [parse-filters.md](parse-filters.md) |
| Parse-time whitespace collapsing (`WhitespaceCollapsingDialect`) | off (opt-in) | [parse-filters.md](parse-filters.md) |
| Static-run coalescing (`StaticRunCoalescingDialect`) | off (opt-in) | [parse-filters.md](parse-filters.md) |
| Per-class cache of the expression type/member security check (`ExpressionUtils`) | on, same behaviour | [benchmark-results.md](benchmark-results.md) |
| Cacheable SpEL ACL property accessor; shared standalone accessors (`thymeleaf-spring6`) | on, same behaviour | [security-audit.md](security-audit.md) |
| Allocation-light `StandardLinkBuilder` | on, same output | [security-audit.md](security-audit.md) |
| JMH rendering benchmarks (`benchmarks/thymeleaf-benchmarks`, outside the build reactor) | – | [README](../../benchmarks/thymeleaf-benchmarks/README.md) |
| Testing library: `-Dthymeleaf.testing.additionalDialects` runs the whole suite with extra dialects | test only | [parse-filters.md](parse-filters.md) |
| **Fix for an upstream 3.1.6 regression:** `#messages.msgOrNull(...)` returns `null` again for keys the Spring `MessageSource` doesn't have (3.1.6 throws). Later message resolvers are tried again too. `#{...}` / `msg()` still fail fast by default (3.1.6 behaviour); `SpringTemplateEngine.setFailOnMissingMessage(false)` restores 3.1.4's `??key_locale??` | fix on; switch defaults to fail fast | [below](#missing-messages) |
| **Fix for an upstream 3.1.6 regression:** restricted-mode checks (e.g. fragment parameters) no longer reject text inside string literals such as `'New promotion'` (SpEL / Spring dialects) | on | [below](#restricted-mode-checks-and-string-literals) |

All changes were security-audited (see [security-audit.md](security-audit.md)). The upstream test suites pass with and
without coalescing forced on.

## Missing messages

Thymeleaf 3.1.6 started failing templates when the Spring `MessageSource` throws `NoSuchMessageException`. Spring
does this by default (`useCodeAsDefaultMessage=false`). The 3.1.6 change also broke `#messages.msgOrNull(...)`,
which is documented to return `null` for missing keys.

This fork keeps 3.1.6's intent and fixes the regression:

| Expression | Upstream 3.1.4 | Upstream 3.1.6 | This fork (default) | This fork, `failOnMissingMessage=false` |
|---|---|---|---|---|
| `#{missing}`, `#messages.msg('missing')` | `??missing_en??` | exception | exception | `??missing_en??` |
| `#messages.msgOrNull('missing')` | `null` | **exception** | `null` | `null` |
| Key missing in the Spring resolver, present in a later resolver | resolved | **exception** | resolved | resolved |

**Mechanism.** `SpringMessageResolver` throws `MessageNotFoundException`, a subclass of
`TemplateProcessingException`. The engine treats it as "not found": it tries the remaining resolvers, and only
fails where an absent message representation would otherwise be rendered.

**Restoring 3.1.4 output for `#{...}`** (e.g. on live sites while missing keys are being found) on a Spring Boot
auto-configured engine:

```java
@Bean
static BeanPostProcessor thymeleafFailOnMissingMessage() {
    return new BeanPostProcessor() {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String name) {
            if (bean instanceof SpringTemplateEngine engine) {
                engine.setFailOnMissingMessage(false);
            }
            return bean;
        }
    };
}
```

## Restricted-mode checks and string literals

**The problem.** In restricted mode (fragment parameters, event-handler attributes, …), Thymeleaf rejects
expressions that instantiate objects, reference types or beans, or read request parameters. Upstream 3.1.6 started
lower-casing the expression before checking (SpEL keywords are case-insensitive). So **text inside string
literals** now trips the check, and a fragment parameter like the following fails with "Instantiation of new
objects … is forbidden":

```html
<div th:replace="~{fragments :: editHeader(${ {{'Home','/admin'}, {'New promotion', null}} })}"></div>
```

The same happens for literals such as `'Search params'` or `'a@b.com'`.

**The fix in this fork.** The check ignores the contents of SpEL string literals. Literal boundaries are found
exactly as SpEL's tokenizer finds them (`'…'` / `"…"`, doubled quotes as escapes, no other escapes), on the
original expression text. Instantiation and type references outside literals, in any letter case, are still
rejected.

Bean-reference detection now follows SpEL's own bean-reference syntax exactly.

**Verification.**
- `SpringExternalAccessLiteralTest` includes a differential test: 200,000 generated expressions, each parsed with
  SpEL. Any that SpEL parses into a constructor, type or bean reference must be flagged.
- The OGNL (core) check is unchanged.

## Merging a new upstream release

```bash
git fetch upstream --tags
git checkout -b merge-3.1.x 3.1-kedos
git merge thymeleaf-3.1.x.RELEASE        # resolve conflicts, then run the core and Spring 6 suites
```

Then merge to `3.1-kedos` and tag `3.1.x.KEDOS-1`.
