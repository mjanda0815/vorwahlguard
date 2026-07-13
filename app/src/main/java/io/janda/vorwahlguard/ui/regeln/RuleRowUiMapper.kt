package io.janda.vorwahlguard.ui.regeln

import io.janda.vorwahlguard.domain.model.PatternKind
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import java.util.Locale
import javax.inject.Inject

/**
 * Builds [RuleRowUi] from a [Rule]. Mirrors `CountryUiMapper`'s structure: a small stateless
 * class the owning `ViewModel` constructs itself (CLAUDE.md's addrule package convention),
 * rather than something Hilt injects directly into the ViewModel's constructor.
 */
class RuleRowUiMapper @Inject constructor(
    private val catalog: CountryCatalog,
) {

    /**
     * PROJECT.md §6's own example table shows a bare exact number with no flag/country label
     * even though [CountryCatalog.describe] *could* resolve one for it — an EXACT rule is about
     * one specific number, not a country, so it is deliberately rendered as raw pattern text.
     */
    fun toRow(rule: Rule, locale: Locale): RuleRowUi {
        val pattern = rule.pattern()
        val label = when (pattern.kind()) {
            PatternKind.PRIVATE -> RuleLabel.Private
            PatternKind.ANY -> RuleLabel.Raw
            PatternKind.EXACT -> RuleLabel.Raw
            PatternKind.PREFIX -> {
                val description = catalog.describe(pattern)
                val countries = description.countries()
                if (description.ambiguous()) {
                    RuleLabel.AmbiguousCode(countries.size)
                } else if (countries.isEmpty()) {
                    // No known calling code resolves this prefix (CountryCatalog.describe can
                    // return an empty, unambiguous list, e.g. an unrecognized calling code) —
                    // fall back to the raw pattern text rather than crashing on a missing country.
                    RuleLabel.Raw
                } else {
                    val country = countries.single()
                    RuleLabel.Country(country.flagEmoji(), country.displayName(locale))
                }
            }
        }
        return RuleRowUi(
            id = rule.id(),
            patternText = pattern.text(),
            action = rule.action(),
            label = label,
        )
    }
}
