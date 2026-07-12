package io.janda.vorwahlguard.domain.service;

import io.janda.vorwahlguard.domain.model.PhoneNumber;
import io.janda.vorwahlguard.domain.model.Rule;
import io.janda.vorwahlguard.domain.model.ScreeningDecision;
import java.util.List;

/**
 * CLAUDE.md §4 conflict resolution, pure and in-memory:
 *
 * <ol>
 *   <li>Longest matching prefix wins (an exact match is the longest possible prefix).
 *   <li>On equal specificity: {@code ALLOW > SILENCE > BLOCK}.
 *   <li>No match → allow the call, record nothing.
 * </ol>
 *
 * <p>Disabled rules are never considered. This never throws for well-formed input — the
 * screening hot path (CLAUDE.md §3 rule 1) cannot afford a checked or unchecked surprise here.
 */
public final class RuleMatcher {

    public ScreeningDecision match(List<Rule> rules, PhoneNumber number) {
        if (rules == null || rules.isEmpty() || number == null) {
            return ScreeningDecision.allow();
        }

        Rule best = null;
        for (Rule rule : rules) {
            if (rule == null || !rule.enabled()) {
                continue;
            }
            if (!rule.pattern().matches(number)) {
                continue;
            }
            if (best == null || isBetter(rule, best)) {
                best = rule;
            }
        }

        if (best == null) {
            return ScreeningDecision.allow();
        }
        return new ScreeningDecision(best.action(), best.id());
    }

    private boolean isBetter(Rule candidate, Rule current) {
        int specificityCompare =
                Integer.compare(candidate.pattern().specificity(), current.pattern().specificity());
        if (specificityCompare != 0) {
            return specificityCompare > 0;
        }
        return candidate.action().precedence() > current.action().precedence();
    }
}
