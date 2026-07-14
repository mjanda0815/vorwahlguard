# Monetarisierung von VorwahlGuard — Bewertung und Vorgehen

> Stand: Juli 2026. Bewertung der Optionen, Geld mit VorwahlGuard zu verdienen,
> plus konkrete Schritt-für-Schritt-Anleitungen. Die steuerlichen/rechtlichen
> Abschnitte sind eine Orientierung, keine Rechts- oder Steuerberatung — vor dem
> ersten Euro Umsatz einmal mit einem Steuerberater sprechen.

---

## 1. Das Wichtigste zuerst: der Zielkonflikt

VorwahlGuards gesamtes Wertversprechen ist ein einziger Satz: **„Diese App kann
nicht nach Hause telefonieren."** Kein `INTERNET` im Manifest, keine Dritt-SDKs,
keine Telemetrie — strukturell erzwungen, nicht versprochen (README §Datenschutz,
CLAUDE.md §1/§12). Die CI schlägt fehl, wenn eine Netzwerk-Permission auftaucht.

**Jedes Werbe-SDK bricht das vollständig:**

- Werbung braucht `INTERNET` und `ACCESS_NETWORK_STATE` im Manifest — genau die
  Zeile, deren Abwesenheit die README als „Verify it yourself" bewirbt.
- Werbe-SDKs (AdMob & Co.) sind Dritt-SDKs mit eigenem Tracking, eigener
  Datenerhebung, eigenem Netzwerkverkehr. Die Datenschutz-Tabelle in der README
  („Drittanbieter-SDKs: Keine. Das Gerät verlassende Daten: Keine.") wäre für
  die werbefinanzierte Variante schlicht falsch.
- DSGVO: Personalisierte Werbung erfordert einen Consent-Dialog (Google UMP /
  TCF 2.2) als Allererstes beim App-Start — bei einer App, deren einziges
  Verkaufsargument Privatsphäre ist.
- Zielgruppen-Paradox: Wer eine offline-only Privacy-App installiert, ist exakt
  die Person, die bei einem Werbebanner sofort deinstalliert und eine
  1-Stern-Bewertung mit dem Wort „Verrat" hinterlässt.

**Fazit vorweg:** Werbung ist für *diese* App das wirtschaftlich schlechteste
und reputationsteuerste Modell. Es gibt bessere Wege (→ §4), die das
Offline-Versprechen zu 100 % intakt lassen — inklusive bezahlter Pro-Version.
Werbung wird in §7 trotzdem vollständig beschrieben, damit die Entscheidung
informiert fällt und nicht an fehlender Information scheitert.

---

## 2. Die Lizenzfrage: MIT, privat stellen, oder etwas dazwischen?

### 2.1 Was die MIT-Lizenz tatsächlich bedeutet

- **MIT hindert dich an gar nichts.** Du darfst die App verkaufen, Werbung
  einbauen, eine Pro-Version anbieten — die Lizenz beschränkt die *Nutzer*
  deines Codes nicht dich als Urheber.
- **MIT erlaubt aber auch jedem anderen dasselbe.** Jeder darf VorwahlGuard
  forken, umbenennen, kostenlos oder kostenpflichtig neu veröffentlichen —
  auch als direkte Konkurrenz zu deiner bezahlten Version. Einzige Pflicht:
  Copyright-Hinweis beibehalten.
- **Praxis-Einordnung:** Für eine Nischen-App ohne große Bekanntheit ist das
  Fork-Risiko real, aber klein. Bekannte Open-Source-Apps (Conversations,
  DAVx⁵, Tasker-Alternativen) verkaufen seit Jahren erfolgreich auf Google
  Play, obwohl der Quellcode offen ist — bezahlt wird Bequemlichkeit
  (Store-Installation, automatische Updates), nicht Geheimhaltung.

### 2.2 Kannst du die Lizenz überhaupt ändern?

Ja, problemlos: `git shortlog` zeigt **einen einzigen Autor** (beide
Committer-Identitäten sind du). Du bist alleiniger Urheberrechtsinhaber und
kannst für alle *zukünftigen* Versionen jede beliebige Lizenz wählen — auch
proprietär/closed source. Zwei Dinge sind unumkehrbar:

1. **Bereits veröffentlichte MIT-Stände bleiben MIT.** Jeder, der v0.2.0
   geklont hat, darf diesen Stand für immer unter MIT nutzen und forken. Ein
   „Zurückholen" gibt es nicht.
2. Sobald du externe Contributions annimmst (PRs von Dritten), brauchst du für
   einen Lizenzwechsel deren Zustimmung — oder ein CLA (Contributor License
   Agreement) ab dem ersten fremden PR. Aktuell: kein Thema, kein fremder Code.

### 2.3 Die drei realistischen Lizenz-Strategien

| Strategie | Was es heißt | Passt wenn… |
|---|---|---|
| **A. Offen bleiben (MIT), Convenience verkaufen** | Code bleibt öffentlich. GitHub-Release bleibt gratis. Auf Google Play kostet die App Geld (oder hat einen Pro-Unlock). Vorbild: Conversations, DAVx⁵. | …das „Verify it yourself"-Argument der Privacy-Story wichtig bleiben soll. **Empfohlen.** |
| **B. Repo privat stellen, proprietär weitermachen** | Ab jetzt closed source. Alte MIT-Stände bleiben draußen (Forks möglich). Die README-Zeile „Überprüf es selbst: das Manifest ist öffentlich" fällt weg — ausgerechnet für eine Privacy-App ein echter Verlust. | …du Werbung/heikle Geschäftslogik verbergen willst (was ohnehin nicht empfohlen ist). |
| **C. Open Core / Dual License** | Kern bleibt MIT (`:core-domain`, Basisfunktionen), Pro-Features (z. B. Regel-Import/Export, Backup) leben in einem privaten Modul/Repo. | …du später substanzielle Pro-Features baust. Für den heutigen Funktionsumfang Overkill. |

**Wichtig unabhängig von der Lizenz:** Der **Name** „VorwahlGuard" ist durch
die MIT-Lizenz *nicht* freigegeben — Markenrecht ist von Urheberrecht getrennt.
Ein Fork darf den Code nehmen, aber du kannst untersagen, dass er unter
demselben Namen auftritt. Eine DE-Wortmarke beim DPMA kostet ~290 € einmalig
(Schutzdauer 10 Jahre) und ist der wirksamste Fork-Schutz, den es für
Open-Source-Apps gibt. Nicht sofort nötig, aber vor ernsthafter Vermarktung
sinnvoll.

### 2.4 Antwort auf deine konkrete Frage

> „Sollte ich ein privates Projekt draus machen, wenn ich Werbung einbauen und
> eine werbefreie Pro-Version anbieten will?"

Du *müsstest* nicht (MIT erlaubt dir beides auch öffentlich), aber die Frage
ist fast müßig, weil das Werbemodell selbst für diese App nicht das richtige
ist (→ §1, §7.5). Für die empfohlenen Modelle (→ §4) ist **öffentlich bleiben
ein Verkaufsargument**, kein Risiko: Käufer einer Privacy-App bezahlen eher,
wenn sie nachprüfen können, dass die App hält, was sie verspricht.

---

## 3. Nüchterne Markteinschätzung (bevor Aufwand entsteht)

Ehrliche Zahlen zur Kalibrierung der Erwartungen:

- **Marktgröße:** Anrufblocker sind eine reale, aber besetzte Nische (Google
  Phone hat einen eingebauten Spamfilter, dazu Calls Blacklist, Truecaller
  u. a.). VorwahlGuards Differenzierung — Vorwahl-/Länderregeln statt
  Einzelnummern, komplett offline — ist echt, aber erklärungsbedürftig.
- **Realistische Größenordnungen** für eine Nischen-Utility ohne
  Marketing-Budget: hunderte bis niedrige tausende Installationen im ersten
  Jahr. Bei 2–5 % Kaufkonversion und ~3–5 € Preis: **eher Taschengeld als
  Einkommen** (grob 50–500 €/Jahr anfangs). Werbung wäre bei diesen
  Nutzerzahlen noch dramatisch schlechter (→ §7.5).
- **Was den Unterschied macht:** Sichtbarkeit (F-Droid-Listing, Reddit
  r/degoogle / r/androidapps, Heise/Golem-Erwähnung, XDA) — die
  Privacy-Community ist genau die Zielgruppe und honoriert offene Projekte.
- **Konsequenz:** Das Monetarisierungsmodell sollte **wenig laufenden Aufwand**
  erzeugen (kein Abo-Support, keine Werbe-Optimierung), damit es sich auch bei
  kleinen Zahlen lohnt. Genau das leisten Einmalkauf und Spenden.

---

## 4. Die Optionen im Vergleich

| Modell | Offline-Versprechen bleibt? | Aufwand | Ertragspotenzial | Empfehlung |
|---|---|---|---|---|
| **Einmalkauf auf Google Play** (App kostet z. B. 3,99 €) | ✅ komplett | niedrig | mittel | ⭐ **Empfohlen, einfachste Variante** |
| **Freemium: gratis + Pro-Unlock als In-App-Kauf** | ✅ komplett (siehe §6.3 — Play Billing braucht **kein** `INTERNET` in der App) | mittel | mittel–höher | ⭐ Empfohlen, wenn Gratis-Einstieg gewünscht |
| **Dual-Distribution: GitHub/F-Droid gratis, Play kostenpflichtig** | ✅ komplett | niedrig | mittel | ⭐ Beste Kombination mit Open Source (Conversations-Modell) |
| **Spenden** (GitHub Sponsors, Ko-fi, Liberapay, PayPal) | ✅ komplett | minimal | niedrig | ✅ Sofort machbar, kombinierbar mit allem |
| **Abo** | ✅ | hoch | für diese App unpassend | ❌ Es gibt keine laufenden Kosten, die ein Abo rechtfertigen — Nutzer merken das |
| **In-App-Werbung + werbefreie Pro-Version** | ❌ **zerstört es** | hoch (SDK, Consent, zwei Varianten pflegen) | bei realistischen Nutzerzahlen minimal | ❌ Abgeraten — vollständige Anleitung trotzdem in §7 |
| **B2B / White-Label** (z. B. für Unternehmen mit Auslands-Spam-Problem) | ✅ | hoch | spekulativ | Nur verfolgen, wenn konkrete Anfrage kommt |

### Die Kernerkenntnis zu Bezahlmodellen

**Google Play Billing benötigt keine `INTERNET`-Permission in deiner App.**
Die Billing-Bibliothek redet per Binder-IPC mit der lokal installierten
Play-Store-App; die Netzwerkkommunikation macht der Play Store selbst. Deine
App bekommt nur die (harmlose, nicht netzwerkbezogene) Permission
`com.android.vending.BILLING` in den Merge. Das heißt: **eine bezahlte
Pro-Version ist mit dem „kann nicht nach Hause telefonieren"-Versprechen
vollständig vereinbar** — der CI-Privacy-Guard bleibt grün. Das ist der
entscheidende Unterschied zu Werbung und der Grund, warum die Empfehlung so
eindeutig ausfällt.

---

## 5. Empfehlung

**Kurzfassung: Modell „Conversations" — offen bleiben, auf Play verkaufen,
Spenden annehmen. Keine Werbung.**

1. **Sofort (0 Aufwand):** Spenden-Links einrichten (§6.1). Kostet nichts,
   bricht nichts, bringt ab Tag 1 potenziell etwas.
2. **Hauptschritt:** Die App auf Google Play als **Einmalkauf** (3,99–5,99 €)
   veröffentlichen. GitHub-Release bleibt parallel gratis verfügbar — wer
   selbst baut oder sideloaded, zahlt nichts; wer Store-Komfort und
   Auto-Updates will, zahlt. Das ist bei Privacy-Apps etabliert und akzeptiert.
3. **Optional später:** F-Droid-Listing (gratis) für Reichweite in der
   Privacy-Community + DE-Wortmarke „VorwahlGuard" als Fork-Schutz.
4. **Lizenz:** MIT behalten. Privat stellen bringt für diese Modelle nichts
   und kostet das beste Marketing-Argument der App.

**Achtung — das reaktiviert eine frühere Entscheidung:** Im Store-Readiness-
Review (Juli 2026) wurde entschieden, *nicht* in den Play Store zu gehen,
wodurch mehrere Blocker entfielen. Monetarisierung über Play macht genau diese
Punkte wieder zur Pflicht: **Datenschutzerklärung (URL), Permissions-
Declaration-Form für `READ_CONTACTS` + Call-Screening-Rolle, AAB-Build,
Data-Safety-Formular, und die noch ausstehende R8-Verifikation des
Release-Builds auf einem echten Gerät.** Details standen im Review; §6.2
führt sie als Checkliste auf.

---

## 6. Konkretes Vorgehen (empfohlener Weg)

### 6.1 Stufe 0: Spenden — heute in 30 Minuten erledigt

1. **GitHub Sponsors:** github.com/sponsors → als Einzelperson registrieren
   (braucht Stripe-Konto-Verknüpfung, IBAN reicht). Dann im Repo eine Datei
   `.github/FUNDING.yml`:
   ```yaml
   github: [mjanda0815]
   custom: ["https://ko-fi.com/DEINNAME", "https://paypal.me/DEINNAME"]
   ```
   Das blendet den „Sponsor"-Button oben im Repo ein.
2. **Ko-fi** (ko-fi.com) und/oder **Liberapay** (liberapay.com, in der
   FOSS-Community verbreitet): Konto anlegen, Link in `FUNDING.yml` und in den
   README-Abschnitt „Installation" aufnehmen.
3. Optional ein Spenden-Hinweis im About-Bereich der App (nur Text + Link, der
   den System-Browser öffnet — wie der bestehende Quellcode-Link; kein
   Netzwerkzugriff durch die App selbst, Muster existiert schon in
   `EinstellungenScreen.kt`).

**Erwartung ehrlich:** Spenden bringen bei kleinen Projekten typischerweise
einstellige bis niedrige zweistellige Eurobeträge pro Monat. Es ist die
Basis, nicht das Modell.

### 6.2 Stufe 1: Google Play, Einmalkauf

**Einmalige Voraussetzungen (Konto & Recht):**

1. **Google Play Console Konto** erstellen (play.google.com/console):
   einmalig 25 USD. Für den Verkauf: als „Organisation" oder Einzelperson mit
   Zahlungsprofil registrieren, IBAN für Auszahlungen hinterlegen.
   Seit 2024 verlangt Google für neue persönliche Konten einen
   Identitätsnachweis und (für Apps mit Zahlungen) eine verifizierte
   Anschrift, die im Store-Listing öffentlich erscheint (Händlerpflicht).
2. **Gewerbeanmeldung:** Der Verkauf einer App ist eine gewerbliche Tätigkeit
   → Gewerbeanmeldung bei der Gemeinde (~20–60 €). Als Nebengewerbe
   unproblematisch.
3. **Steuern:** Bei Verkäufen über Google Play tritt Google für Endkunden
   als Händler auf und führt die Umsatzsteuer in den Käuferländern ab —
   du stellst effektiv Google in Rechnung (Reverse-Charge-Konstellation).
   Deine Einnahmen sind einkommensteuerpflichtig. Kleinunternehmerregelung
   (§ 19 UStG) und die Details hier: **einmal mit dem Steuerberater klären**,
   die Konstellation ist Standard und schnell erklärt.
4. **Impressum & Datenschutzerklärung:** Für eine kommerzielle App Pflicht.
   Die Datenschutzerklärung ist bei dieser App erfreulich ehrlich kurz („es
   werden keine Daten erhoben oder übertragen; Kontakte werden ausschließlich
   lokal gelesen") — als `PRIVACY.md` im Repo schreiben und via GitHub Pages
   als URL verfügbar machen (Play verlangt eine URL, keine Datei).

**Technische Checkliste (aus dem Store-Readiness-Review, jetzt Pflicht):**

- [ ] **R8-Release-Build auf echtem Gerät verifizieren** — der wichtigste
      offene Punkt: Release bauen, installieren, `+43*`-Regel anlegen,
      Länderauswahl prüfen (libphonenumber-Keep-Rules sind bis dahin
      unverifiziert, docs/RELEASE.md §4/§6).
- [ ] **AAB statt APK:** `./gradlew :app:bundleRelease` — Play akzeptiert nur
      App Bundles. Signing wie in docs/RELEASE.md, plus Play App Signing bei
      der ersten Einreichung (Google verwaltet den App-Signing-Key, dein
      Keystore wird zum Upload-Key).
- [ ] **Play Console Formulare:** Data-Safety-Formular („keine Daten erhoben,
      keine geteilt" — stimmt hier wirklich), Content-Rating-Fragebogen,
      **Permissions-Declaration** für `READ_CONTACTS` und die
      Call-Screening-Rolle (Standard-Rollen-Apps durchlaufen eine manuelle
      Prüfung; Begründung: Kontakte-Bypass, rein lokal — PROJECT.md §9 hat
      das als akzeptierten Use Case eingeordnet, die Prüfung dauert erfahrungsgemäß
      einige Tage extra).
- [ ] **Store-Listing:** Screenshots (Übersicht, Regeln, Länderauswahl,
      Einstellungen), Feature-Grafik 1024×500, Kurz-/Langbeschreibung. Die
      README liefert den Text fast fertig.
- [ ] **Interner Test-Track zuerst:** App als „Internal testing" hochladen,
      auf dem eigenen Gerät aus dem Store installieren, Rolle + Screening
      einmal real durchtesten, erst dann Production.
- [ ] Preis festlegen (3,99–5,99 € ist die übliche Spanne für Utility-Apps
      dieser Art; Play behält 15 % Servicegebühr bis 1 Mio. USD/Jahr).

**Parallel im Repo:**

- [ ] README: „Not currently on Google Play" ersetzen durch Play-Link +
      Hinweis „auf GitHub weiterhin gratis als APK".
- [ ] PROJECT.md §9 aktualisieren (die Store-Entscheidung ist damit revidiert)
      und die Entscheidung als ADR festhalten.

### 6.3 Stufe 2 (optional): Freemium statt Einmalkauf

Falls du lieber eine Gratis-Version mit Pro-Unlock willst (mehr Downloads,
dafür Konversions-Hürde in der App):

1. **Feature-Split festlegen.** Sinnvolle Pro-Kandidaten, die den Kern nicht
   verkrüppeln: unbegrenzte Regelanzahl (gratis z. B. max. 3), Regel-Import/
   Export (steht ohnehin auf der v1.1-Roadmap), erweiterte Statistiken.
   Der Anrufschutz selbst sollte **nie** eingeschränkt sein — eine
   Sicherheits-App, die Schutz hinter einer Paywall rationiert, bekommt zu
   Recht schlechte Bewertungen.
2. **Google Play Billing Library** einbinden (`com.android.billingclient:billing-ktx`,
   aktuelle Version im Version Catalog pinnen). Ein einziges nicht-verbrauchbares
   Produkt (`pro_unlock`) in der Play Console anlegen.
3. **Kein `INTERNET` nötig** (→ §4): Kaufstatus kommt per IPC vom Play Store,
   wird lokal gecacht (DataStore, analog `SettingsStore`). Der CI-Privacy-Guard
   bleibt unangetastet.
4. **Architektur-Hinweis:** Der Kaufstatus ist ein Settings-artiger Zustand →
   als `ProStatusProvider` im `:app`-Modul (Adapter), Gate-Logik über die
   bestehenden UiState-Muster. `:core-domain` bleibt frei davon — ob eine
   Regel angelegt werden *darf*, ist UI-Policy, nicht Matching-Logik.
5. Aufwand realistisch: 2–4 Entwicklungstage inkl. Tests und Edge-Cases
   (Kauf wiederherstellen, Play-Store-App fehlt, etc.) — deutlich mehr als
   Einmalkauf (0 Codeänderung). Deshalb: erst Einmalkauf, Freemium nur wenn
   die Downloadzahlen es rechtfertigen.

---

## 7. Werbung — vollständige Anleitung trotz Abratens

Damit die Entscheidung auf Fakten beruht, hier der komplette Weg, falls du
dich doch für Werbung entscheidest. Lies zuerst §7.5 (Rechenbeispiel).

### 7.1 Werbeanbieter (wo man sie findet)

| Anbieter | Zugang | Eignung hier |
|---|---|---|
| **Google AdMob** (admob.google.com) | Selbstregistrierung, braucht AdSense-Konto | Standard, beste Fill-Rate in DACH, einfachste Integration |
| AppLovin MAX (applovin.com) | Selbstregistrierung | Eher als Mediation-Schicht ab größeren Volumina |
| Unity Ads / ironSource (unity.com/products/unity-ads) | Selbstregistrierung | Gaming-fokussiert, für Utility-Apps schwächer |
| Meta Audience Network | Nur noch als Mediation-Partner | Nicht mehr eigenständig sinnvoll |
| InMobi, Start.io | Selbstregistrierung | Nischen-/Fallback-Netzwerke, niedrigere eCPMs |

Für eine kleine Utility-App ist **AdMob allein** die einzig sinnvolle Wahl —
Mediation (mehrere Netzwerke konkurrieren pro Impression) lohnt erst ab
zehntausenden täglichen Impressionen.

### 7.2 Technische Integration (AdMob, Banner)

1. AdMob-Konto anlegen → App registrieren → **App-ID** und **Ad-Unit-IDs**
   erzeugen (eine pro Platzierung).
2. Abhängigkeit: `com.google.android.gms:play-services-ads` in den Version
   Catalog und `app/build.gradle.kts`.
3. **Manifest-Änderungen** (⚠️ jede davon braucht laut CLAUDE.md §8/§9
   explizite Freigabe, und der CI-Privacy-Guard schlägt absichtlich fehl —
   er müsste bewusst entfernt werden):
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   <meta-data
       android:name="com.google.android.gms.ads.APPLICATION_ID"
       android:value="ca-app-pub-XXXXXXXX~YYYYYYYY" />
   ```
4. SDK-Initialisierung in `VorwahlGuardApp.onCreate()`, Banner-Composable
   (`AndroidView` um `AdView`) z. B. unten in der Übersicht.
5. **DSGVO-Consent:** Google **UMP SDK** (`user-messaging-platform`)
   integrieren, Consent-Formular in der AdMob-Konsole konfigurieren (TCF 2.2).
   Der Dialog muss vor der ersten Ad-Anfrage kommen. Ohne Consent nur
   eingeschränkte, schlechter bezahlte Anzeigen.
6. Zwei Build-Flavors (`free` mit Ads, `pro` ohne) oder ein Flavor + Unlock —
   beides verdoppelt effektiv die Test-Matrix.
7. Play-Konsequenzen: Data-Safety-Formular muss Werbe-Tracking deklarieren,
   Datenschutzerklärung muss AdMob/Partner auflisten, „enthält Werbung"-Badge
   im Listing.

### 7.3 Was sich an der App-Substanz ändert

- README §Datenschutz, CLAUDE.md §1/§12, der CI-Guard, die Data-Safety-Angaben
  — alles müsste umgeschrieben werden. Die App wäre eine andere App.
- Die bestehende Nutzerbasis (Privacy-Community) wäre verprellt; Rezensionen
  bei Privacy-Apps mit nachträglich eingebauter Werbung sind erfahrungsgemäß vernichtend.

### 7.4 Alternativen zu klassischer In-App-Werbung

- **Ethical/Privacy-Ads** (kontextuell statt tracking-basiert, kein Consent
  nötig): Anbieter wie EthicalAds oder Carbon bedienen fast nur
  Web/Developer-Zielgruppen — für eine Android-Utility-App praktisch nicht
  verfügbar. Kein realer Weg.
- **Sponsoring** (eine Firma zahlt für Erwähnung im About/README): realistisch
  erst bei nennenswerter Reichweite.

### 7.5 Das Rechenbeispiel, das die Entscheidung meist beendet

Banner-eCPM (Ertrag pro 1.000 Einblendungen) in DACH für Utility-Apps:
realistisch **0,20–1,50 €**. Eine App wie VorwahlGuard wird *selten geöffnet* —
das ist ihr Designziel (sie arbeitet im Hintergrund). Angenommen großzügige
1.000 aktive Nutzer, die die App 2× pro Woche öffnen und je 2 Banner sehen:

> 1.000 Nutzer × 2 Öffnungen/Woche × 2 Impressionen × 4,3 Wochen ≈ 17.000
> Impressionen/Monat × ~0,50 € eCPM ≈ **8,50 €/Monat**.

Dem stehen gegenüber: SDK-Pflege, Consent-Handling, zerstörtes
Alleinstellungsmerkmal, verprellte Zielgruppe, doppelte Build-Varianten.
**Dieselben 1.000 Nutzer bei 3 % Kaufquote und 3,99 € Einmalkauf: ~120 €** —
einmalig pro Kohorte, aber ohne jeden der genannten Kosten. Werbung verliert
diese Rechnung bei jeder realistischen Nutzerzahl dieser App-Kategorie.

---

## 8. Zusammenfassung der Antworten auf deine Fragen

1. **„Privates Projekt draus machen?"** — Nein. Für die empfohlenen Modelle
   (Einmalkauf/Freemium/Spenden) ist der offene Quellcode ein
   Verkaufsargument. Privat stellen wäre nur fürs Werbemodell plausibel, und
   das Werbemodell ist für diese App das falsche.
2. **„Gibt es neben Werbung andere Varianten?"** — Ja, und sie passen alle
   besser: Einmalkauf auf Play, Pro-Unlock per In-App-Kauf (geht ohne
   `INTERNET`-Permission!), Dual-Distribution (GitHub gratis / Play bezahlt),
   Spenden. Siehe §4/§5.
3. **„Geht das mit der MIT-Lizenz?"** — Ja, uneingeschränkt. MIT beschränkt
   dich nicht; sie erlaubt nur auch anderen das Forken. Als Alleinautor
   könntest du jederzeit umlizenzieren; nötig ist es nicht. Fork-Schutz
   liefert eine Wortmarke, nicht die Lizenz.
4. **„Wie genau vorgehen?"** — §6: Stufe 0 Spenden (heute), Stufe 1 Play
   Store Einmalkauf (der Hauptschritt, inkl. der wieder aktiven
   Store-Blocker-Checkliste), Stufe 2 optional Freemium. Werbeanbieter und
   deren Integration stehen vollständig in §7 — inklusive der Rechnung, warum
   sich das hier nicht trägt.

## 9. Nächste konkrete Schritte (wenn du der Empfehlung folgst)

1. Steuerberater-Termin (einmalig, Konstellation „App-Verkauf über Google
   Play als Nebengewerbe").
2. `FUNDING.yml` + Ko-fi/GitHub-Sponsors (30 Minuten, sofort).
3. `PRIVACY.md` schreiben + via GitHub Pages hosten.
4. R8-Release-Build auf dem Gerät verifizieren (der offene Blocker aus dem
   Review — ohnehin Pflicht vor jedem Release).
5. Play-Console-Konto anlegen, Gewerbe anmelden.
6. AAB bauen, interner Test-Track, Formulare, dann Production mit Einmalkauf.
7. PROJECT.md §9 + README aktualisieren, Entscheidung als ADR festhalten.
