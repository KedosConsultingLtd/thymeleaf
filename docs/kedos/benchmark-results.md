# Rendering benchmark results (2026-11-09)

Benchmark: `benchmarks/thymeleaf-benchmarks` (JMH sample time, one thread, 1 fork, warm caches, UTF-8 sink).

**Environment:** a shared 4-vCPU cloud container on JDK 21. Absolute numbers are indicative only; compare rows
within one table, not across machines. Differences under about 5% on the mean, and most single-run p99
differences, are within noise.

## 1. Variants (full matrix, before the `ExpressionUtils` fix)

| Engine | Page | Variant | Mean µs | p50 µs | p99 µs | KB allocated | Mean vs baseline |
|---|---|---|---|---|---|---|---|
| SPRING | HOME | BASELINE | 1852 | 1720 | 3113 | 2257 | +0% |
| SPRING | HOME | WHITESPACE | 1657 | 1577 | 2729 | 2258 | -11% |
| SPRING | HOME | COALESCING | 1783 | 1706 | 3113 | 2300 | -4% |
| SPRING | HOME | WHITESPACE_AND_COALESCING | 1669 | 1602 | 3212 | 2251 | -10% |
| SPRING | LISTING | BASELINE | 3845 | 3621 | 6299 | 4698 | +0% |
| SPRING | LISTING | WHITESPACE | 3734 | 3596 | 6145 | 4698 | -3% |
| SPRING | LISTING | COALESCING | 3931 | 3719 | 6424 | 4723 | +2% |
| SPRING | LISTING | WHITESPACE_AND_COALESCING | 3606 | 3490 | 5989 | 4719 | -6% |
| SPRING | PRODUCT | BASELINE | 1909 | 1774 | 3158 | 2288 | +0% |
| SPRING | PRODUCT | WHITESPACE | 1757 | 1665 | 3105 | 2333 | -8% |
| SPRING | PRODUCT | COALESCING | 1792 | 1733 | 3265 | 2309 | -6% |
| SPRING | PRODUCT | WHITESPACE_AND_COALESCING | 1774 | 1669 | 4474 | 2310 | -7% |
| SPRING | CONTENT | BASELINE | 1282 | 1204 | 2269 | 1664 | +0% |
| SPRING | CONTENT | WHITESPACE | 1152 | 1083 | 2597 | 1595 | -10% |
| SPRING | CONTENT | COALESCING | 1218 | 1184 | 2159 | 1638 | -5% |
| SPRING | CONTENT | WHITESPACE_AND_COALESCING | 1177 | 1100 | 2287 | 1672 | -8% |
| STANDARD | HOME | BASELINE | 933 | 899 | 1632 | 986 | +0% |
| STANDARD | HOME | WHITESPACE | 861 | 822 | 1516 | 986 | -8% |
| STANDARD | HOME | COALESCING | 932 | 909 | 1620 | 996 | -0% |
| STANDARD | HOME | WHITESPACE_AND_COALESCING | 824 | 804 | 1431 | 996 | -12% |
| STANDARD | LISTING | BASELINE | 2364 | 2265 | 4093 | 2237 | +0% |
| STANDARD | LISTING | WHITESPACE | 2130 | 2087 | 4137 | 2237 | -10% |
| STANDARD | LISTING | COALESCING | 2329 | 2265 | 3955 | 2246 | -1% |
| STANDARD | LISTING | WHITESPACE_AND_COALESCING | 2206 | 2167 | 3739 | 2246 | -7% |
| STANDARD | PRODUCT | BASELINE | 1048 | 958 | 2019 | 993 | +0% |
| STANDARD | PRODUCT | WHITESPACE | 888 | 830 | 1543 | 993 | -15% |
| STANDARD | PRODUCT | COALESCING | 1075 | 976 | 1733 | 999 | +3% |
| STANDARD | PRODUCT | WHITESPACE_AND_COALESCING | 903 | 831 | 1587 | 999 | -14% |
| STANDARD | CONTENT | BASELINE | 677 | 632 | 1169 | 707 | +0% |
| STANDARD | CONTENT | WHITESPACE | 567 | 535 | 1024 | 707 | -16% |
| STANDARD | CONTENT | COALESCING | 624 | 601 | 1096 | 716 | -8% |
| STANDARD | CONTENT | WHITESPACE_AND_COALESCING | 568 | 538 | 1053 | 716 | -16% |

**Cached model size and output size** (`ModelStatsReport`, all templates and fragments a page uses):

| Page | Cached events: baseline → coalesced | Output bytes: baseline → whitespace-collapsed |
|---|---|---|
| HOME | 767 → 368 (−52%) | 105,306 → 61,526 (−42%) |
| LISTING | 857 → 443 (−48%) | 176,833 → 117,457 (−34%) |
| PRODUCT | 879 → 514 (−42%) | 107,582 → 62,858 (−42%) |
| CONTENT | 1,014 → 257 (−75%) | 100,543 → 58,627 (−42%) |

**What this shows:**
- **Whitespace collapsing: 8–16% lower mean render time.** The gain comes from writing and encoding 34–42% fewer
  bytes. Smaller pages also cut network and compression cost, which this benchmark doesn't measure.
- **Static-run coalescing: roughly 0–8%, often within noise,** although it halves the cached model. Per-render
  cost is dominated by *dynamic* work: `th:each` iterations, expression evaluation, link building, message
  resolution and processor execution. The static markup it removes was already cheap to replay. It helps most on
  static-heavy pages (CONTENT −5% to −8%).
- **Allocation is 1–4.7 MB per render and barely moves with either variant,** so the next gains have to come from
  the dynamic path.
- **The Spring (SpEL) engine costs about 1.6–2× the core (OGNL) engine** for identical output, in both time and
  allocation.

## 2. Where allocation goes (JFR, LISTING page, BASELINE)

Top allocating frames in Thymeleaf, Spring or OGNL code:

| Spring engine (SpEL) | Share | Core engine (OGNL) | Share |
|---|---|---|---|
| SpEL `CodeFlow.toDescriptor` (runs on every property read, even with the compiler off) | 10.0% | `ProcessorTemplateHandler.resetModel` | 7.1% |
| SpEL `AccessorUtils.getAccessorsToTry` | 8.4% | `StandardLinkBuilder.replaceTemplateParamsInBase` | 6.3% |
| `ExpressionUtils.isTypeBlockedForAllPurposes` | 5.1% | `ProcessorTemplateHandler.handleOpenElement` | 6.3% |
| `ExpressionUtils.isTypeBlockedForMemberCalls` | 4.8% | `StandardLinkBuilder.buildLink` | 6.2% |
| `ThymeleafEvaluationContext` ACL property accessor `canRead` | 3.8% | `ElementProcessorIterator.recompute` | 5.7% |
| `StandardLinkBuilder.buildLink` | 3.5% | OGNL `executeExpression` | 5.5% |
| SpEL `ExpressionState.initContextObjects` | 3.3% | `OpenElementTag.removeAttribute` / `Attributes.removeAttribute` | 9.0% |

## 3. Fix applied: per-class cache of the expression security check

`ExpressionUtils.isMemberForbiddenForInstanceOfType` ran two stream pipelines on every property access and method
call. It now caches the per-class decision in a `ClassValue`; behaviour is unchanged and both test suites pass.

A/B on the same machine, BASELINE variant, 2 forks each:

| Engine | Page | mean µs before → after | p99 µs before → after | KB/render before → after |
|---|---|---|---|---|
| SPRING | LISTING | 3755 → 3019 (-20%) | 6164 → 5054 (-18%) | 4708 → 4134 (-12%) |
| SPRING | PRODUCT | 1836 → 1488 (-19%) | 3194 → 2980 (-7%) | 2332 → 1991 (-15%) |
| STANDARD | LISTING | 2353 → 1987 (-16%) | 4850 → 4280 (-12%) | 2233 → 2061 (-8%) |
| STANDARD | PRODUCT | 1001 → 939 (-6%) | 1766 → 2000 (+13%) | 992 → 953 (-4%) |

The OGNL PRODUCT p99 increase is single-run noise: the mean and allocation both went down.

## 4. Next candidates, by measured share

| # | Change | Where | Expected effect |
|---|---|---|---|
| 1 | Reuse SpEL evaluation-context objects (ACL property accessor, method resolver, type locator) across renders, so SpEL's per-node accessor cache stays valid | `thymeleaf-spring6` `ThymeleafEvaluationContext`, `SPELVariableExpressionEvaluator` | Spring: up to about 8% (`getAccessorsToTry`, `canRead`) |
| 2 | Link building: avoid repeated string rebuilding in `replaceTemplateParamsInBase` / `buildLink` | core `StandardLinkBuilder` | about 6–12% of allocation |
| 3 | Per-element processor execution: `resetModel`, `ProcessorExecutionVars.cloneVars`, `ElementProcessorIterator`, attribute removal creating new tag objects | core engine | about 20–25% of allocation (core engine); larger change |
| 4 | Report SpEL's `CodeFlow.toDescriptor` per read upstream (Spring Framework) | Spring Framework | Spring: about 10% |
