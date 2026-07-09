# Contributing

## Branching — Git Flow

| Branch | From | Into | Purpose |
|---|---|---|---|
| `main` | — | — | released code only, tagged `vX.Y.Z`, protected |
| `develop` | `main` | — | integration branch, PR target |
| `feature/<nr>-<slug>` | `develop` | `develop` | one issue, one branch |
| `release/x.y.z` | `develop` | `main` + `develop` | version bump, changelog, stabilisation |
| `hotfix/x.y.z` | `main` | `main` + `develop` | production emergencies only |

Direct pushes to `main` and `develop` are rejected. Force-pushing to either is never acceptable.

```bash
git checkout develop && git pull
git checkout -b feature/12-country-picker
# ...
gh pr create --base develop --fill
```

## Commits — Conventional Commits

```
<type>(<optional scope>): <imperative subject, ≤72 chars>

<optional body: why, not what>

Refs: #12
```

Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`.
Scopes: `core-domain`, `screening`, `data`, `ui`, `di`, `ci`.

```
feat(core-domain): resolve rule conflicts by longest prefix
fix(screening): guard against null call handle for withheld numbers
feat(ui): warn that +1 also covers Canada and 20 territories
```

No emoji, no trailers advertising tooling, no "WIP" commits on a PR branch — squash them
locally first.

## Definition of Done

- [ ] Domain logic covered by `:core-domain` unit tests
- [ ] `./gradlew build lint` passes locally
- [ ] No new manifest permission (a new permission requires explicit discussion)
- [ ] No phone number in any `Log.*` call
- [ ] User-facing strings in `strings.xml`, both `values` (en) and `values-de` (de)
- [ ] Documentation updated when behaviour or setup changed
- [ ] PR body explains the reasoning; the diff explains itself

## Code style

- `:core-domain` is Java 17. No Kotlin, no Android imports, no annotations from DI frameworks.
- `:app` is Kotlin. Compose functions are `@Composable fun PascalCase()`, stateless where
  possible, state hoisted to a ViewModel.
- Public domain types are immutable. Prefer records where D8 supports them; plain final classes
  otherwise.
- Formatting per `.editorconfig`. 120-column limit.

## Working with agents

`.claude/agents/` ships four roles: `architect` and `reviewer` on Opus (read-only),
`implementer` and `test-writer` on Sonnet. Plans come before code, reviews come before the PR.

Subagents are loaded at session start. If you edit an agent file, restart the session or the old
definition stays live.

## Reporting bugs

Reproduce it as a failing `:core-domain` test if you can — that is the fastest path to a fix.
For screening bugs, include Android version, OEM, carrier, and whether the number arrived with
a caller ID.

**Never paste a real phone number into an issue.** Redact it: `+43663…`.
