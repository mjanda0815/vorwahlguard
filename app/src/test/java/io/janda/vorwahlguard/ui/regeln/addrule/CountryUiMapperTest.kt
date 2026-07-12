package io.janda.vorwahlguard.ui.regeln.addrule

import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [CountryUiMapper]: the per-locale collator sort and cache in [CountryUiMapper.countriesFor]
 * (CLAUDE.md §6 — country display names are locale-derived, never a hardcoded list), and the
 * "Land suchen" predicate in [CountryUiMapper.matches] (calling-code prefix search plus a
 * diacritic-/case-insensitive display-name substring search).
 *
 * The fixture deliberately includes AT/DE (unambiguous calling codes) and US/CA (both +1), so
 * the calling-code search is exercised against the CLAUDE.md §6 1:n ambiguity, not just a single
 * clean example.
 */
class CountryUiMapperTest {

    private val countries = listOf(
        Country("AT", 43),
        Country("US", 1),
        Country("CA", 1),
        Country("DE", 49),
    )

    private lateinit var catalog: CountryCatalog
    private lateinit var mapper: CountryUiMapper

    @Before
    fun setUp() {
        catalog = mockk()
        every { catalog.all() } returns countries
        mapper = CountryUiMapper(catalog)
    }

    @Test
    fun `countriesFor maps every country from the catalog`() {
        val result = mapper.countriesFor(Locale.GERMANY)

        assertEquals(countries.size, result.size)
        assertEquals(countries.map { it.iso2() }.toSet(), result.map { it.iso2 }.toSet())
    }

    @Test
    fun `countriesFor sorts by collator order for the requested locale, not raw string order`() {
        // Raw Unicode ordering (Ö = U+00D6) sorts "Österreich" after "Vereinigte Staaten"; the
        // German collator treats Ö like O and sorts it right after "Kanada" instead. Asserting on
        // this pair proves collator sorting is actually in effect, not accidentally correct.
        val result = mapper.countriesFor(Locale.GERMANY)

        assertEquals(
            listOf("Deutschland", "Kanada", "Österreich", "Vereinigte Staaten"),
            result.map { it.displayName },
        )
        assertEquals(listOf("DE", "CA", "AT", "US"), result.map { it.iso2 })
    }

    @Test
    fun `countriesFor caches the sorted list for repeated calls with the same locale`() {
        mapper.countriesFor(Locale.GERMANY)
        mapper.countriesFor(Locale.GERMANY)

        verify(exactly = 1) { catalog.all() }
    }

    @Test
    fun `countriesFor rebuilds when the requested locale changes`() {
        mapper.countriesFor(Locale.GERMANY)
        mapper.countriesFor(Locale.US)

        verify(exactly = 2) { catalog.all() }
    }

    @Test
    fun `blank query matches every country`() {
        val austria = CountryUi("AT", 43, "🇦🇹", "Österreich")

        assertTrue(mapper.matches(austria, ""))
        assertTrue(mapper.matches(austria, " "))
    }

    @Test
    fun `digit query matches the calling code with an optional leading plus`() {
        val austria = CountryUi("AT", 43, "🇦🇹", "Österreich")

        assertTrue(mapper.matches(austria, "43"))
        assertTrue(mapper.matches(austria, "+43"))
    }

    @Test
    fun `digit query matches by calling code prefix, not only an exact code`() {
        val austria = CountryUi("AT", 43, "🇦🇹", "Österreich")

        assertTrue(mapper.matches(austria, "4"))
    }

    @Test
    fun `digit query matches an ambiguous calling code via the code path, not the display name`() {
        val us = CountryUi("US", 1, "🇺🇸", "Vereinigte Staaten")
        val canada = CountryUi("CA", 1, "🇨🇦", "Kanada")
        val austria = CountryUi("AT", 43, "🇦🇹", "Österreich")

        assertTrue(mapper.matches(us, "1"))
        assertTrue(mapper.matches(canada, "1"))
        // Sanity check that this isn't accidentally a display-name substring match.
        assertFalse(austria.displayName.contains("1"))
        assertFalse(mapper.matches(austria, "1"))
    }

    @Test
    fun `display name substring match is case and diacritic insensitive`() {
        val austria = CountryUi("AT", 43, "🇦🇹", "Österreich")

        assertTrue(mapper.matches(austria, "osterreich"))
    }

    @Test
    fun `query matching neither the calling code nor the display name returns false`() {
        val austria = CountryUi("AT", 43, "🇦🇹", "Österreich")

        assertFalse(mapper.matches(austria, "xyz"))
    }
}
