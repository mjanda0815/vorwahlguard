# VorwahlGuard kostenlos im Google Play Store veröffentlichen

> Stand: Juli 2026. Schritt-für-Schritt-Anleitung: Play-Console-Konto anlegen,
> alle Pflichtangaben, und der Weg bis zur kostenlosen Veröffentlichung.
> Google ändert diese Anforderungen regelmäßig — verbindlich ist immer, was
> die Play Console selbst anzeigt. Offizielle Referenzen:
> [Kontoverifizierung](https://support.google.com/googleplay/android-developer/answer/10841920),
> [Test-Anforderungen für neue persönliche Konten](https://support.google.com/googleplay/android-developer/answer/14151465).

---

## 0. Überblick: Was kommt auf dich zu

| Punkt | Aufwand/Kosten |
|---|---|
| Play-Console-Registrierung | **25 USD einmalig** (Kreditkarte/Zahlungsmittel nötig) |
| Identitäts-/Adress-/Telefonverifizierung | Ausweis + Dokument mit Adresse, wenige Tage Prüfzeit |
| DSA-Händlerstatus-Erklärung | Formular, für eine kostenlose Hobby-App: „Kein Händler" |
| **Geschlossener Test: 12 Tester, 14 Tage** | Die größte Hürde — Zeit- und Organisationsaufwand |
| Store-Eintrag (Texte, Screenshots, Grafiken) | ~1 Tag |
| Console-Formulare (Datenschutz, Data Safety, Rating, Permissions) | ~½ Tag + Prüfzeit durch Google |
| Technisch: AAB, Play App Signing, R8-Verifikation | §6 — R8-Gerätetest ist noch offen |

Für eine **kostenlose** App entfällt alles rund um Zahlungen: kein
Händlerkonto, kein Zahlungsprofil für Auszahlungen, keine Steuerformulare,
und eine Gewerbeanmeldung ist für eine kostenlose App ohne Einnahmen und ohne
Gewinnerzielungsabsicht nicht erforderlich.

---

## 1. Voraussetzungen sammeln (vor der Registrierung)

1. **Google-Konto** — idealerweise ein dediziertes (z. B.
   `vorwahlguard@gmail.com` oder das bestehende Entwickler-Konto), nicht das
   private Haupt-Konto. Das Konto lässt sich später nicht wechseln, nur die
   ganze Registrierung übertragen.
2. **Amtlicher Lichtbildausweis** (Personalausweis oder Reisepass) für die
   Identitätsprüfung — Google verlangt seit 2023 für alle neuen Konten einen
   Identitätsnachweis per Foto/Scan.
3. **Adressnachweis:** ein offizielles Dokument mit Name + Anschrift
   (Meldebescheinigung, Kontoauszug, Versorgerrechnung). Der Personalausweis
   mit Adresse reicht oft schon.
4. **Telefonnummer** — wird per SMS/Anruf verifiziert.
5. **Zahlungsmittel** für die einmalige 25-USD-Gebühr.
6. **Entscheidung Kontotyp:** Für dieses Projekt: **persönliches Konto**
   (Einzelperson). Ein Organisationskonto bräuchte eine D-U-N-S-Nummer und
   lohnt nur für Firmen. Wichtigster Nachteil des persönlichen Kontos: die
   12-Tester-Pflicht (→ §4) — Organisationskonten sind davon ausgenommen.

---

## 2. Play-Console-Konto anlegen (Schritt für Schritt)

1. **play.google.com/console** → „Jetzt starten" → mit dem gewählten
   Google-Konto anmelden.
2. Kontotyp **„Selbst" / persönliches Konto** wählen.
3. **Entwicklername** festlegen — erscheint öffentlich unter jedem
   App-Eintrag (z. B. „Martin Janda" oder ein Pseudonym/Projektname).
   Später änderbar.
4. Formulare ausfüllen: Name (muss exakt dem Ausweis entsprechen!), Adresse,
   Telefonnummer, Entwickler-Kontakt-E-Mail (öffentlich sichtbar — ggf. eine
   eigene Adresse wie `vorwahlguard@janda.io` anlegen).
5. **25 USD zahlen.**
6. **Verifizierungen durchlaufen** (kommen als Aufgaben ins
   Console-Dashboard, Reihenfolge kann variieren):
   - Identität: Ausweisfoto hochladen.
   - Adresse: Dokument hochladen; Google gleicht mit den Kontoangaben ab.
   - Telefon: SMS-/Anruf-Code.
   - E-Mail: Bestätigungslink.
   Prüfdauer: Stunden bis wenige Tage. Erst nach Abschluss kannst du Apps
   einreichen.
7. **DSA-Händlerstatus** (EU Digital Services Act, Pflichtangabe für die
   Verbreitung in der EU): Für eine kostenlose App ohne Einnahmen und ohne
   Gewinnerzielungsabsicht erklärst du dich als **„Kein Händler"
   (Non-Trader)**. Konsequenz: Deine Privatadresse und Telefonnummer werden
   **nicht** öffentlich im Store-Eintrag angezeigt (bei Händlern schon).
   Solltest du später doch monetarisieren, muss der Status auf „Händler"
   geändert werden — dann wird die Anschrift öffentlich.

---

## 3. App in der Console anlegen

1. Dashboard → **„App erstellen"**: Name „VorwahlGuard", Standardsprache
   Deutsch, Typ „App", **kostenlos**.
   ⚠️ **Die Entscheidung „kostenlos" ist endgültig** — eine kostenlose App
   kann bei Google Play nie mehr in eine kostenpflichtige umgewandelt werden
   (umgekehrt geht es). Für den gefassten Plan ist das okay, aber es sollte
   bewusst passieren.
2. Die Console führt danach durch eine Aufgabenliste („Einrichtung der App").
   Die Punkte im Einzelnen in §5.

---

## 4. Die 12-Tester-Hürde (wichtigster Planungsfaktor)

Für **persönliche Konten, die nach dem 13. November 2023 erstellt wurden**,
gilt ([offizielle Seite](https://support.google.com/googleplay/android-developer/answer/14151465)):

- Vor dem ersten Production-Release muss ein **geschlossener Test** laufen mit
  **mindestens 12 Testern, die 14 Tage lang ununterbrochen angemeldet** sind.
- Die 14 Tage zählen erst, **nachdem** der Release von Google geprüft wurde
  und mindestens 12 Tester beigetreten sind.
- Danach beantragst du im Dashboard **„Zugriff auf Produktion"** und
  beantwortest Fragen zum Testverlauf (was getestet wurde, was sich geändert
  hat). Google prüft dabei auch, ob die Tester die App real benutzt haben —
  12 tote Konten reichen nicht sicher.

**Praktisch heißt das:**

1. **Tester rekrutieren:** Familie, Freunde, Kollegen mit Android-Gerät —
   jeder braucht nur ein Google-Konto und muss über einen Opt-in-Link
   beitreten und die App installieren. Alternativ Tester-Communities
   (r/AndroidClosedTesting auf Reddit, testerscommunity.com u. ä.) — bei
   Fremd-Testern darauf achten, dass sie die App wirklich öffnen.
2. **Test-Track einrichten:** Console → Testen → Geschlossener Test → Track
   erstellen → E-Mail-Liste der Tester anlegen → AAB hochladen → Opt-in-Link
   verteilen.
3. **14 Tage warten**, dabei App-Nutzung anregen (bei VorwahlGuard: Regel
   anlegen lassen, ein Anruf-Screening erleben).
4. **Production-Zugriff beantragen**, Fragen ehrlich beantworten.

Realistischer Gesamtzeitplan von Konto-Anlage bis Live-Gang: **4–6 Wochen**,
davon der Großteil Warten (Verifizierung, 14 Tage Test, Google-Reviews).

---

## 5. Pflichtangaben und Formulare in der Console

### 5.1 Datenschutzerklärung (Pflicht, als URL)

- `PRIVACY.md` im Repo anlegen. Inhalt ist bei dieser App erfreulich kurz und
  wahr: keine Datenerhebung, keine Übertragung, kein `INTERNET` im Manifest;
  Kontakte werden bei aktivierter Kontakte-Ausnahme ausschließlich lokal
  gelesen; Anrufprotokoll bleibt lokal (SQLite), konfigurierbare Löschfrist,
  optionale Pseudonymisierung; Verantwortlicher + Kontakt.
- Per **GitHub Pages** als URL veröffentlichen (Repo-Settings → Pages →
  Branch `main`/docs) — die Console verlangt eine erreichbare URL, keine
  Datei.

### 5.2 Data-Safety-Formular

Wahrheitsgemäß und hier einfach: **keine Daten werden erhoben, keine geteilt**
(die App hat keinen Netzwerkzugriff — genau das, was der CI-Privacy-Guard
erzwingt). `READ_CONTACTS` wird zur rein lokalen Verarbeitung genutzt und
zählt damit nicht als „Erhebung" im Sinne des Formulars (Daten verlassen das
Gerät nicht) — die On-Device-Verarbeitung im Formular trotzdem sauber
deklarieren.

### 5.3 Berechtigungs-Erklärung (Permissions Declaration)

Die App nutzt zwei heikle Dinge, die Google gesondert prüft:

- **`READ_CONTACTS`**: Begründung: optionale, standardmäßig deaktivierte
  Kontakte-Ausnahme; Abgleich ausschließlich auf dem Gerät; fail-closed bei
  Entzug. Im Formular den Kern-Anwendungsfall („Anruf-Screening /
  Spam-Schutz") wählen.
- **Call-Screening-Rolle** (`ROLE_CALL_SCREENING` / `BIND_SCREENING_SERVICE`):
  Anrufblocker sind ein von Google akzeptierter Anwendungsfall, aber die
  Prüfung ist manuell und dauert erfahrungsgemäß einige Tage extra. In der
  Erklärung beschreiben: App wird vom Nutzer aktiv als Anrufschutz-App
  gewählt (System-Dialog), blockiert/stummschaltet nur nach vom Nutzer
  angelegten Regeln.

Ein Video ist für die Rollen-Prüfung hilfreich: 30–60 Sekunden Screencast,
der Onboarding (Rolle erteilen) und eine Regel zeigt.

### 5.4 Weitere Formulare

- **Inhaltseinstufung** (Fragebogen): keine Gewalt/Glücksspiel/etc. →
  niedrigste Einstufung.
- **Zielgruppe:** 18+ bzw. „nicht an Kinder gerichtet" wählen — das hält die
  App aus den strengeren Families-Richtlinien heraus.
- **Werbung enthält:** Nein.
- **Kategorie:** „Tools" oder „Kommunikation"; Tags z. B. Anrufblocker.

### 5.5 Store-Eintrag

- **Kurzbeschreibung** (80 Zeichen) und **vollständige Beschreibung** (bis
  4000) — die deutsche README liefert den Text fast fertig; zusätzlich eine
  englische Übersetzung als zweite Sprache anlegen (die App ist ohnehin
  de/en lokalisiert).
- **Grafiken:** App-Icon 512×512 PNG (aus dem Adaptive Icon exportieren),
  Feature-Grafik 1024×500, mindestens 2 Telefon-Screenshots (Übersicht,
  Regeln, Länderauswahl, Einstellungen — per `adb exec-out screencap`
  vom echten Gerät, wie in dieser Session bereits gemacht).

---

## 6. Technische Vorbereitung im Repo

- [x] **R8-Release-Build auf dem Gerät verifiziert** — erledigt am 2026-07-15
      (Release v0.2.0, versionCode 148, v2-Signatur): Länderauswahl geöffnet und
      `+43*`-Regel angelegt, kein Crash, kein Normalisierungsfehler. Die
      libphonenumber-Keep-Rules gelten damit als bestätigt (docs/RELEASE.md §4).
      Nach jedem libphonenumber-Update oder Änderung der Keep-Rules erneut
      prüfen — der schnellste automatisierte Check ist
      `ScreeningDecisionStackInstrumentedTest` gegen den Release-Build
      (`adb shell am instrument` gegen die Release-Test-APK).
- [ ] **AAB statt APK:** Play akzeptiert nur App Bundles →
      `./gradlew :app:bundleRelease` (funktioniert mit der bestehenden
      signingConfig aus docs/RELEASE.md).
      ⚠️ **Lokal bauen, nicht in CI:** der `versionCode` ist seit #53 die
      Git-Commit-Anzahl, und CIs Checkout ist ein Shallow Clone (Commit-Count
      wäre dort 1).
- [ ] **Play App Signing:** Beim ersten Upload verlangt Play die Teilnahme —
      Google verwaltet den App-Signing-Key, dein bestehender Keystore
      (`~/keys/`, docs/RELEASE.md) wird zum **Upload-Key**. Vorteil: geht der
      Upload-Key verloren, kann Google ihn zurücksetzen; der bisherige
      Keystore-Verlust-Albtraum entfällt.
- [ ] **Versionsname** ggf. anheben (aktuell 0.2.0; für den Store-Launch bietet
      sich 1.0.0 nach abgeschlossener R8-Verifikation an — M5 in der Roadmap).
- [ ] **Nach dem Launch im Repo nachziehen:** README („Nicht bei Google
      Play" → Play-Badge/Link, GitHub-Release bleibt parallel), PROJECT.md §9
      (Entscheidung revidiert), kurzes ADR zur Store-Entscheidung.

---

## 7. Veröffentlichungsablauf (Reihenfolge)

1. Konto anlegen + Verifizierungen (§1–§2) — *parallel dazu §5/§6 vorbereiten*.
2. App anlegen, alle Formulare ausfüllen (§3, §5).
3. AAB bauen, **interner Test** (bis 100 Tester, sofort verfügbar, keine
   Review): auf dem eigenen Gerät aus dem Store installieren, Rolle +
   Screening real prüfen.
4. **Geschlossener Test** starten, 12+ Tester einladen, 14 Tage laufen lassen (§4).
5. **Produktionszugriff beantragen**, Fragen beantworten, Freigabe abwarten.
6. **Production-Release** einreichen (die erste Review dauert wegen der
   Call-Screening-Rolle eher Tage als Stunden).
7. Repo aktualisieren (§6 letzter Punkt).

---

## 8. Laufender Betrieb danach

- **Updates:** neues AAB in Production hochladen — `versionCode` zählt durch
  die Commit-Count-Ableitung automatisch hoch, es ist nichts von Hand zu
  pflegen. Reviews für Updates sind meist schneller als die Erstprüfung.
- **Jährliche Pflichten:** Google verlangt gelegentlich Re-Verifizierungen
  und die Bestätigung der Kontaktdaten; die Deadlines kommen per E-Mail an
  die Entwickler-Adresse — diese Adresse also wirklich lesen.
- **Target-SDK-Pflicht:** Play verlangt jährlich (Stichtag ~31. August), dass
  Apps ein aktuelles `targetSdk` haben — aktuell erfüllt (36), muss aber
  jedes Jahr einmal angefasst werden.
- **Inaktivität:** Konten ohne Aktivität und Apps ohne Updates können nach
  längerer Zeit deaktiviert/delistet werden — ein Update pro Jahr (und sei es
  nur der targetSdk-Bump) hält alles am Leben.
