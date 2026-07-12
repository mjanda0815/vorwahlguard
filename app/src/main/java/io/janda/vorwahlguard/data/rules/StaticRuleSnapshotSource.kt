package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import java.time.Instant
import javax.inject.Inject

/**
 * M2 stub [RuleSnapshotSource]: one hardcoded seed rule so the screening path is manually
 * verifiable end to end before Room lands in M3 (PROJECT.md M2: "add `+43*` as Lautlos, call
 * from an Austrian number, the phone stays silent").
 */
class StaticRuleSnapshotSource @Inject constructor() : RuleSnapshotSource {

    // core-domain is not compiled with -parameters, so named arguments are not available here;
    // order matches Rule's canonical constructor: id, pattern, action, enabled, label, createdAt.
    override fun load(): List<Rule> = listOf(
        Rule(
            SEED_RULE_ID,
            PatternSyntax.parse("+43*"),
            RuleAction.SILENCE,
            true,
            "Österreich",
            Instant.EPOCH,
        ),
    )

    private companion object {
        const val SEED_RULE_ID = "m2-seed-at-silence"
    }
}
