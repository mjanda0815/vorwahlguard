# Initial prompt for Claude Code

Scaffolding, not documentation. Delete it once M0 is merged.

---

## The order of operations

Not "just tell Claude Code to go." Three things have to be true first.

```bash
cd vorwahlguard
gh auth status          # 1. gh must be authenticated
./scripts/bootstrap.sh  # 2. creates the repo, develop branch, labels, issues
claude                  # 3. only now
```

`bootstrap.sh` makes the first commit and pushes it. If you start Claude Code before the repo
exists, the first thing it will do is ask you where to push.

Two mechanics worth knowing:

- **Subagents load at session start.** Editing a file in `.claude/agents/` mid-session has no
  effect until you restart. Do not debug an agent that is not actually running.
- **`"model": "opusplan"` is already set** in `.claude/settings.json`. Confirm with `/model` in
  the session that it took, and that Opus is the planning model.

---

## Session 0 — verify the setup before writing a line

```
Read CLAUDE.md, PROJECT.md and docs/ARCHITECTURE.md.

Then answer, without writing any code:
1. What are the three hard constraints on onScreenCall(), in your own words?
2. Which module does the rule matching live in, and why not the other one?
3. What happens if the decision logic throws an exception?
4. Which permission must never appear in the manifest?
5. A user picks "USA" in the country picker. What pattern is created, and what does the UI
   have to tell them before the rule is saved?
6. When may two implementer subagents run in parallel, and what breaks if they both run
   ./gradlew in the same checkout?

If anything in those documents is wrong, outdated, or contradicts the current Android API,
tell me now rather than working around it later.
```

Wrong answer → fix `CLAUDE.md`, not the code. That is the cheapest bug fix in the project.

---

## Session 1 — the M0 skeleton

```
We are building VorwahlGuard. You have read CLAUDE.md, PROJECT.md and docs/ARCHITECTURE.md.

Task: implement issue #1 (M0, project skeleton) on a Git Flow feature branch.

Scope:
- settings.gradle.kts with two modules: :core-domain and :app
- gradle/libs.versions.toml as the single source of truth for versions
- :core-domain — java-library plugin, Java 17, dependencies: libphonenumber, JUnit 5, AssertJ.
  No Android dependency of any kind. Do not add the geocoder or carrier artifacts.
- :app — com.android.application, Kotlin, minSdk 29, Jetpack Compose (BOM), Material 3, Hilt,
  Room. Depends on :core-domain.
- AndroidManifest.xml with the CallScreeningService declared (BIND_SCREENING_SERVICE permission,
  android.telecom.CallScreeningService intent filter, exported=true) but not yet implemented,
  and with NO internet permission.
- Compose scaffold: bottom navigation with Übersicht, Regeln, Protokoll, Einstellungen — each a
  placeholder. Material 3, light and dark theme. Strings in values/ and values-de/.
- Application class with @HiltAndroidApp.
- One smoke test in :core-domain so ./gradlew :core-domain:test actually runs something.

Constraints:
- Use current stable versions. Look them up if unsure; do not guess a version number.
- Gradle Kotlin DSL only, no Groovy.
- Do not implement any screening, matching or country logic. That is M1 and M2.

Process:
1. git checkout -b feature/1-gradle-skeleton
2. Delegate the design to the architect subagent. Show me its plan and the exact dependency
   versions. Wait for my approval.
3. This milestone is sequential — one implementer, no parallel dispatch. The modules depend on
   each other and there is only one Gradle build.
4. ./gradlew build until green.
5. Run the reviewer subagent.
6. Commit in logical steps, Conventional Commits, Refs: #1.
7. Show me the diffstat and ask before pushing.
```

---

## Why the plan-first gate matters here

Two failure modes in this project are quiet and expensive:

- A wrong AGP/Gradle/JDK combination produces an error that sends you down a rabbit hole for an
  hour. Confirming versions *before* files are written costs one round trip.
- `:core-domain` accidentally depending on Android turns the whole architecture into decoration.
  If the plan contains an `implementation("androidx.…")` line in the domain module, catch it in
  the plan, not in review.

---

## Subsequent sessions

| Command | What it does |
|---|---|
| `/feature 3` | Git Flow branch for issue #3: architect plans (Opus), implementers execute (Sonnet), reviewer audits (Opus) |
| `/review` | Run the Opus reviewer against the current branch |
| `/adr voicemail redirection` | Record an architecture decision |

One issue, one branch, one PR, one fresh session. A session that has been through three features
carries a context window full of decisions that no longer apply.

**Where parallelism actually pays here:** M1's value objects (`PhoneNumber`, `Pattern`,
`CountryCatalog`) are independent — dispatch three Sonnet agents in one turn. `RuleMatcher`
depends on all three and must wait. M4's four screens are independent — dispatch them together,
but give them `isolation: worktree` or let one agent own the build.

Everywhere else, sequential is faster than untangling a merge conflict between two agents that
both stubbed the same class.

---

## The one thing worth typing manually

Before M2 is merged, verify on a real device:

```
Regel anlegen: Österreich → +43* → Lautlos
Von einer österreichischen Nummer anrufen lassen.
Erwartung: Telefon klingelt nicht, Anruf steht im Anrufprotokoll.

Dann dieselbe Regel auf Sperren stellen und erneut anrufen lassen.
Erwartung: Anrufer bekommt sofort ein Ablehnsignal.
```

No test suite substitutes for that. `CallScreeningService` behaviour varies by OEM skin and
carrier, and the emulator will lie to you about both.
