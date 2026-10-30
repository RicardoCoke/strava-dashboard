---
name: code-review
description: How to review code in this repo and how to report findings — review order, severity levels and the exact report format. Use when reviewing a diff, a PR or a feature implementation.
---

# Code review

## Mindset
- Review the code, not the author's intent. If it isn't in the code or tests, it isn't done.
- Every finding must cite `path/File.kt:line` and say **what goes wrong, when** (concrete input/state → wrong result). If you can't describe the failure, it's not a finding — at most a Nit.
- Don't pad the report. Zero findings in a category is a valid result.
- Prefer one precise finding over three vague ones.

## Review order
1. **Spec conformance** — walk `specs.md`: each R# implemented? each acceptance criterion has a test that would actually fail if the behaviour broke?
2. **Correctness** — logic, off-by-one in date bucketing, unit conversions, null/empty cases, pagination ends, error paths, race conditions, frontend/backend contract mismatches, missing loading/empty/error states.
3. **Privacy & security** — run every item in the `privacy-check` skill.
4. **Standards** — `code-standards` skill violations not caught by ktlint/detekt.
5. **Simplicity & maintainability** — dead code, duplication, needless abstraction, unclear names.
6. **Tests** — meaningful assertions, deterministic, fixtures contain no real personal data.

## Severity
| Level | Meaning | Merge? |
|---|---|---|
| **Blocker** | Wrong results, data loss, security/privacy leak, secret in repo, spec requirement missing | No |
| **Major** | Bug in an edge case from the spec, missing test for an acceptance criterion, layering violation, unhandled API error | Fix before merge |
| **Minor** | Standards issue, weak naming, small duplication, missing non-critical test | Fix or justify |
| **Nit** | Taste. Optional | Optional |

## Report format (use exactly)

```markdown
# Review: <feature folder name>
**Base:** <base ref> → **Head:** <HEAD short sha>
**Verdict:** APPROVE | APPROVE WITH CHANGES | REQUEST CHANGES

## Checks
- Backend: ktlint pass/fail · detekt pass/fail (N issues) · tests X passed, Y failed
- Frontend: lint pass/fail · tests X passed, Y failed · build pass/fail

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | |
| R2 | ✅ | ❌ | no test for empty week |

## Findings
### [Blocker] <short title>
- **Where:** `backend/src/main/kotlin/.../File.kt:42` or `frontend/src/app/.../file.ts:42`
- **Problem:** <what is wrong>
- **Failure scenario:** <concrete input/state → wrong outcome>
- **Suggested fix:** <one or two lines>

### [Major] ...

## Privacy checklist
<each privacy-check item: ✅ / ❌ (with finding ref) / n/a>

## Summary
<2–3 sentences: overall quality and the most important thing to fix>
```

Verdict rules: any Blocker → REQUEST CHANGES. Majors only → APPROVE WITH CHANGES. Otherwise APPROVE.
