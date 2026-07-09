---
description: Implement a GitHub issue on a Git Flow feature branch, with Opus planning and Sonnet execution
argument-hint: <issue-number>
---

Implement GitHub issue #$1 as a Git Flow feature branch.

1. Read `CLAUDE.md` and the relevant section of `PROJECT.md`.
2. `gh issue view $1`. If the issue is ambiguous, ask me before writing code.
3. Clean working tree, then:
   `git checkout develop && git pull && git checkout -b feature/$1-<kebab-slug>`
4. **Delegate the design to the `architect` subagent.** It runs on Opus, is read-only, and
   returns a file-level plan plus a parallelisation verdict.
5. Show me the plan and **wait for my approval.** Do not skip this.
6. Execute the plan:
   - Units the architect marked independent → dispatch several `implementer` and `test-writer`
     subagents **in one turn** so they run concurrently.
   - Units with a real dependency → sequential. `:core-domain` compiles before `:app` consumes it.
   - Exactly one agent owns `./gradlew`. The others hand back diffs. Two Gradle daemons in one
     checkout will fight over the build lock.
7. `./gradlew build lint` until green.
8. Run the `reviewer` subagent (Opus, read-only) against the branch. Fix what it flags as
   blocking; tell me about the rest.
9. Commit in small logical steps, Conventional Commits, `Refs: #$1`.
10. Show me `git log --oneline develop..HEAD` and the diffstat. Ask before pushing and opening
    the PR against `develop`.

Do not merge the PR yourself.
