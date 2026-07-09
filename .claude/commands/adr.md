---
description: Record an architecture decision as an ADR
argument-hint: <short title of the decision>
---

Write an Architecture Decision Record for: $ARGUMENTS

- Find the next free number in `docs/adr/`, create `docs/adr/NNNN-<kebab-title>.md`.
- Use the Nygard format: Title, Status, Context, Decision, Consequences.
- Context states the forces at play, including the option we did *not* pick and why.
- Consequences must include the negative ones. An ADR with no downsides is a sales pitch.
- Keep it under one page. Link to the relevant section of `PROJECT.md` or `CLAUDE.md`.
- Add it to the ADR list at the bottom of `docs/ARCHITECTURE.md`.

Then show me the file and stop. Do not commit it.
