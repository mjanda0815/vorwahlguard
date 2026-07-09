#!/usr/bin/env bash
#
# One-shot bootstrap: local repo -> public GitHub repo with Git Flow branches,
# labels, branch protection and the first milestone issues.
#
# Prerequisites: git, gh (authenticated: `gh auth status`)
# Run once, from the repository root:  ./scripts/bootstrap.sh
#
set -euo pipefail

OWNER="mjanda0815"
REPO="vorwahlguard"
DESC="Block spam calls by number range, not one number at a time. Offline-only Android call screener with country and prefix rules."

command -v gh >/dev/null || { echo "gh CLI not found"; exit 1; }
gh auth status >/dev/null || { echo "gh not authenticated — run: gh auth login"; exit 1; }

# ---------------------------------------------------------------- local repo
if [ ! -d .git ]; then
  git init -b main
fi

git add -A
git diff --cached --quiet || git commit -m "chore: initial project scaffolding

Documentation, CI, Git Flow conventions and Claude Code configuration.
No application code yet — see PROJECT.md milestone M0."

# ---------------------------------------------------------------- remote repo
if ! gh repo view "$OWNER/$REPO" >/dev/null 2>&1; then
  gh repo create "$OWNER/$REPO" --public --description "$DESC" --source=. --remote=origin --push
else
  echo "Repo $OWNER/$REPO already exists — skipping creation."
  git remote get-url origin >/dev/null 2>&1 || git remote add origin "https://github.com/$OWNER/$REPO.git"
  git push -u origin main
fi

gh repo edit "$OWNER/$REPO" \
  --add-topic android --add-topic call-screening --add-topic spam-blocker \
  --add-topic kotlin --add-topic java --add-topic jetpack-compose \
  --add-topic privacy --add-topic hexagonal-architecture \
  --enable-issues --enable-wiki=false

# ---------------------------------------------------------------- git flow
git checkout -b develop 2>/dev/null || git checkout develop
git push -u origin develop
gh repo edit "$OWNER/$REPO" --default-branch develop

# Branch protection needs admin rights; harmless if it fails.
for BRANCH in main develop; do
  gh api -X PUT "repos/$OWNER/$REPO/branches/$BRANCH/protection" \
    -f "required_status_checks[strict]=true" \
    -f "required_status_checks[contexts][]=Build & test" \
    -F "enforce_admins=false" \
    -F "required_pull_request_reviews[required_approving_review_count]=0" \
    -F "restrictions=null" \
    -F "allow_force_pushes=false" \
    -F "allow_deletions=false" >/dev/null 2>&1 \
    && echo "Protected $BRANCH" \
    || echo "Could not protect $BRANCH (needs admin rights) — enforce it by discipline."
done

# ---------------------------------------------------------------- labels
gh label create "core-domain" --color 0E8A16 --description "Pure Java domain logic" --force
gh label create "screening"   --color 1D76DB --description "CallScreeningService hot path" --force
gh label create "ui"          --color 7F52FF --description "Compose UI" --force
gh label create "data"        --color FBCA04 --description "Room persistence" --force
gh label create "privacy"     --color B60205 --description "Touches the no-network guarantee" --force
for M in M0 M1 M2 M3 M4 M5; do
  gh label create "$M" --color C5DEF5 --description "Milestone $M" --force
done

# ---------------------------------------------------------------- issues
gh issue create --title "M0: Gradle multi-module skeleton" --label M0 --body \
"Set up \`settings.gradle.kts\`, \`gradle/libs.versions.toml\`, the \`:core-domain\` (java-library, Java 17)
and \`:app\` (com.android.application, Kotlin, Compose, Hilt) modules, minSdk 29.

Definition of done: \`./gradlew build\` is green, CI passes, an empty Compose scaffold with the four
destinations from PROJECT.md section 7 launches on an emulator. No INTERNET permission."

gh issue create --title "M1: PhoneNumber and Pattern value objects" --label "M1,core-domain" --body \
"Implement \`PhoneNumber\` (raw + normalized E.164 + region, plus an UNKNOWN instance) and \`Pattern\`
parsing/validation per CLAUDE.md section 4. Pure Java, JUnit 5.

Edge cases that must be covered: no leading \`+\`, wildcard in the middle, multiple wildcards,
bare \`*\`, the \`PRIVATE\` token, empty string, a national-format number, more than 15 digits."

gh issue create --title "M1: CountryCatalog and the 1:n calling-code problem" --label "M1,core-domain" --body \
"Country to calling code is 1:1. Calling code to country is 1:n: +1 is US, CA and ~20 Caribbean
territories; +7 is RU and KZ; +44 is GB, JE, GG, IM.

Implement \`CountryCatalog\` over libphonenumber with \`all()\`, \`byIso2()\`, \`regionsFor(int)\` and
\`describe(Pattern)\`. Verify that \`getRegionCodesForCountryCode\` exists in the pinned version; if not,
derive ambiguity from \`getSupportedRegions()\`.

Flag emoji are computed from the ISO code (0x1F1E6 + c - 'A'), not shipped as assets."

gh issue create --title "M1: RuleMatcher conflict resolution" --label "M1,core-domain" --body \
"Longest matching prefix wins; on a tie ALLOW > SILENCE > BLOCK; no match means allow and record nothing.
Disabled rules never match. See CLAUDE.md section 4.

This is the class where the bugs will live. Test it accordingly. Depends on PhoneNumber and Pattern —
do not start it in parallel with them."

gh issue create --title "M2: Screening service with fail-open guarantee" --label "M2,screening,privacy" --body \
"Wire the domain into a \`CallScreeningService\`. Requirements from CLAUDE.md section 3:
respondToCall exactly once, null handle guarded, any exception results in the call being allowed,
no disk I/O on the calling thread, rule cache warmed in the service's onCreate.

Map BLOCK to disallowCall+rejectCall, SILENCE to silenceCall alone, ALLOW to the default response."

gh issue create --title "M4: Rule creation via country picker and free-text prefix" --label "M4,ui" --body \
"Add sheet with a segmented control: 'Land wählen' | 'Vorwahl eingeben'.

Country path: searchable list, flag + name + calling code, sorted by localized display name.
After selection, show the resulting pattern AND the collateral: '+1* sperrt auch Kanada und 20
weitere Gebiete.'

Prefix path: free text, live validation against PatternSyntax from :core-domain (do not
reimplement it), plus a 'Nummer testen' field that runs a number through the current rule set.

Both paths converge on the action picker: Sperren / Lautlos / Zulassen. For a bare +XX* rule,
preselect Lautlos and explain why."

echo
echo "Done. Next:"
echo "  claude          # settings.json already selects the opusplan model"
echo "  # then paste Session 0 from INITIAL_PROMPT.md"
