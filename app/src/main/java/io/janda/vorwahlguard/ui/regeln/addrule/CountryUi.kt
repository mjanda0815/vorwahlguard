package io.janda.vorwahlguard.ui.regeln.addrule

import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * UI-ready projection of a [Country] for the "Land wählen" tab of `AddRuleSheet`: the flag and
 * display name are pre-computed once (CLAUDE.md §6) instead of recomputing them on every
 * recomposition.
 */
data class CountryUi(
    val iso2: String,
    val callingCode: Int,
    val flagEmoji: String,
    val displayName: String,
)

/**
 * Builds and sorts [CountryUi] for a given [Locale], and answers the "Land suchen" search
 * predicate. The sorted list is cached per locale — [countriesFor] rebuilds it if the
 * requested locale differs from the last one built for, e.g. because
 * [Locale.getDefault] changed since the cache was built (the caller is expected to always pass
 * the current default).
 */
class CountryUiMapper(
    private val catalog: CountryCatalog,
) {

    @Volatile
    private var cache: CacheEntry? = null

    /** Every [Country] from [CountryCatalog.all], mapped and collator-sorted for [locale]. */
    fun countriesFor(locale: Locale): List<CountryUi> {
        val cached = cache
        if (cached != null && cached.locale == locale) {
            return cached.countries
        }

        val collator = Collator.getInstance(locale)
        val built = catalog.all()
            .map { it.toUi(locale) }
            .sortedWith(compareBy(collator) { it.displayName })
        cache = CacheEntry(locale, built)
        return built
    }

    /**
     * Case/diacritic-insensitive substring match on [CountryUi.displayName], plus a digit match
     * on [CountryUi.callingCode] so typing {@code "43"} or {@code "+43"} finds Austria even
     * though "43" is not part of any display name.
     */
    fun matches(country: CountryUi, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return true
        }
        if (matchesCallingCode(country.callingCode, trimmed)) {
            return true
        }
        return fold(country.displayName).contains(fold(trimmed))
    }

    private fun matchesCallingCode(callingCode: Int, query: String): Boolean {
        val digits = query.removePrefix("+")
        if (digits.isEmpty() || !digits.all(Char::isDigit)) {
            return false
        }
        val code = callingCode.toString()
        return code == digits || code.startsWith(digits)
    }

    /** NFD-normalizes and strips combining marks so "Osterreich"/"österreich" both match "Österreich". */
    private fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return DIACRITIC_MARKS.replace(decomposed, "").lowercase(Locale.ROOT)
    }

    private fun Country.toUi(locale: Locale): CountryUi =
        CountryUi(
            iso2 = iso2(),
            callingCode = callingCode(),
            flagEmoji = flagEmoji(),
            displayName = displayName(locale),
        )

    private data class CacheEntry(val locale: Locale, val countries: List<CountryUi>)

    private companion object {
        val DIACRITIC_MARKS = Regex("\\p{Mn}+")
    }
}
