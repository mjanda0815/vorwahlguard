---
description: Run the Opus reviewer against the current branch
---

Dispatch the `reviewer` subagent against `git diff develop...HEAD`.

Relay its findings verbatim, grouped by severity. Then, for each blocking finding, propose a fix —
but do not apply any of them until I say which ones.

If the reviewer reports nothing, do not pad the report. "Clean" is a valid outcome.
