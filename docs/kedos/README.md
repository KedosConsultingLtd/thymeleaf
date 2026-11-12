# Kedos Thymeleaf

This repository is Kedos Consulting's fork of [thymeleaf/thymeleaf](https://github.com/thymeleaf/thymeleaf).

**Branches and tags**
- **`3.1-kedos`** (default branch) is upstream's release tag `thymeleaf-3.1.7.RELEASE` plus the Kedos changes below.
- Upstream branches and tags are left as they are.
- Kedos releases are tagged with their version (e.g. `3.1.7.KEDOS-1`) and published to GitHub Packages. See
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

All changes were security-audited (see [security-audit.md](security-audit.md)). The upstream test suites pass with and
without coalescing forced on.

## Upgrading from a 3.1.6.KEDOS-n build

Builds up to `3.1.6.KEDOS-3` carried this fork's own fixes for two upstream 3.1.6 regressions. Upstream fixed both in
3.1.7 ([#1117](https://github.com/thymeleaf/thymeleaf/issues/1117),
[#1118](https://github.com/thymeleaf/thymeleaf/issues/1118)), and from `3.1.7.KEDOS-1` this fork uses upstream's
fixes. Two things change for applications:

- **Missing messages render again by default.** A missing `#{...}` or `#messages.msg(...)` renders
  `??key_locale??`, as in 3.1.4. In KEDOS-2 and KEDOS-3 it failed by default. To keep failing on missing keys, opt in
  with `SpringTemplateEngine.setFailOnMissingMessage(true)` (or on `SpringMessageResolver` /
  `StandardMessageResolver`). `#messages.msgOrNull(...)` returns `null` either way. `MessageNotFoundException` no
  longer exists.
- **String literals in restricted mode** (e.g. `'New promotion'` in a fragment parameter) are accepted, as in
  KEDOS-3, now for OGNL too.

3.1.7 also fixes [GHSA-f79q-9862-cq4c](https://github.com/thymeleaf/thymeleaf/security/advisories/GHSA-f79q-9862-cq4c)
(cached expressions evaluated under the restrictions of an earlier, less restrictive context; reported by Kedos).
All `3.1.6.KEDOS-n` builds are affected: the fork's own accessor change covered interpreted property access only,
not compiled expressions, method calls, instantiation or type references. Upgrade to `3.1.7.KEDOS-1`.

## Merging a new upstream release

```bash
git fetch upstream --tags
git checkout -b merge-3.1.x 3.1-kedos
git merge thymeleaf-3.1.x.RELEASE        # resolve conflicts, then run the core and Spring 6 suites
```

Then merge to `3.1-kedos` and tag `3.1.x.KEDOS-1`.
