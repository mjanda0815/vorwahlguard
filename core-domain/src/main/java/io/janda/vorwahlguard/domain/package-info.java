/**
 * Pure-JVM domain core of VorwahlGuard.
 *
 * <p>No Android imports are permitted here — the module uses the {@code java-library}
 * plugin, so {@code import android.*} will not compile. See {@code docs/ARCHITECTURE.md}.
 *
 * <p>Populated in milestone M1: {@code PhoneNumber}, {@code Pattern}, {@code RuleMatcher},
 * {@code CountryCatalog} and the screening service port. M0 only establishes the module.
 */
package io.janda.vorwahlguard.domain;
