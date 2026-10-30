---
name: feature-intake
description: Interview the user about a new feature idea in the main conversation before writing intent.md. Use when the user describes a new feature, graph, page or change they want built.
---

# Feature intake

Goal: gather just enough to write a good `intent.md`. You are capturing the *why* and the *outcome*, not designing.

## Steps
1. Restate the idea in one sentence and confirm.
2. Ask **at most 5 questions in one message**, picking only those still unclear:
   - What question should this answer for you, or what decision will it help you make?
   - Which activities count? (sport types, date range, private activities?)
   - What would "done" look like — what do you see on screen?
   - What is explicitly *not* part of this?
   - Any must-haves (units, time period, comparisons, mobile view)?
   - Is there anything sensitive here (maps/GPS, heart rate, sharing with others)?
3. Offer sensible defaults inside the questions so the user can just say "yes" (e.g. "km and min/km, last 12 weeks — ok?").
4. Once answered, pick the next feature number by listing `docs/features/`, create the folder `docs/features/NNN-short-slug/`, and delegate to the `intent-extractor` sub-agent with the folder path plus the idea and answers verbatim.
5. Show the user the resulting summary and open questions. Don't start specs until they approve.
