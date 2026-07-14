<h1 align="center">VorwahlGuard</h1>

<p align="center">
  <strong>Spam-Anrufe nach Vorwahl blockieren — nicht eine Nummer nach der anderen.</strong><br>
  Ein rein offline arbeitender Android-Anrufschutz mit Länder- und Vorwahl-Regeln.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%2010%2B-3DDC84">
  <img alt="Language" src="https://img.shields.io/badge/core-Java%2017-orange">
  <img alt="UI" src="https://img.shields.io/badge/ui-Kotlin%20%2F%20Compose-7F52FF">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
  <img alt="Network" src="https://img.shields.io/badge/internet%20permission-none-success">
</p>

---

## Das Problem

Spam-Anruf-Kampagnen wechseln bei jedem Versuch die Anrufer-ID. Eine einzelne Nummer zu
blockieren bringt nichts — der nächste Anruf kommt von einer anderen, und die angezeigte Nummer
ist ohnehin meist gefälscht. Androids eingebaute Sperrliste akzeptiert nur exakte Nummern.

Was über eine Kampagne hinweg einigermaßen konstant bleibt, ist die **Vorwahl**: die
Landesvorwahl, oft sogar der Bereich eines bestimmten Betreibers.

## Die Lösung

VorwahlGuard registriert sich als Androids Anrufschutz-App und prüft jeden eingehenden Anruf
gegen deine eigenen Vorwahl-Regeln — direkt auf dem Gerät, bevor das Telefon klingelt.

Regeln entstehen auf zwei Wegen. Ein Land auswählen:

```
🇦🇹 Österreich   →   +43*      Lautlos
```

…oder eine Vorwahl direkt eingeben:

```
+43663*          →   Sperren      ein Betreiberbereich
+436631234567    →   Zulassen     …außer dieser einen Kontakt
PRIVATE          →   Lautlos      unterdrückte Anrufer-ID
```

Die spezifischste Regel gewinnt. Bei Gleichstand schlägt `Zulassen` `Lautlos` schlägt `Sperren`.

## Drei Aktionen

| | Was passiert |
|---|---|
| **Sperren** | Der Anruf wird sofort abgewiesen. Der Anrufer hört ein Besetzt- oder Ablehnungssignal — oder landet direkt in deiner Mailbox, falls eine eingerichtet ist (die Umleitung macht dein Mobilfunkanbieter, wie beim manuellen Wegdrücken). |
| **Lautlos** | Das Telefon klingelt nicht. Der Anruf erscheint weiterhin im Anrufprotokoll und landet in der Mailbox, falls der Anrufer wartet. |
| **Zulassen** | Ein Whitelist-Eintrag. Schlägt jede ebenfalls zutreffende Sperrregel. |

Für eine ganze Landesvorwahl ist **Lautlos** die sicherere Wahl — ein legitimer Anruf aus diesem
Land erreicht trotzdem deine Mailbox. Die App schlägt das automatisch vor, wenn du eine
`+XX*`-Regel anlegst.

## Deine Kontakte sind die Ausnahme

Eine Vorwahl-Sperrliste ist absichtlich grob: `+43* Sperren` stoppt eine ganze Spam-Kampagne,
würde aber auch deine österreichischen Freunde stoppen. Statt für jeden von ihnen von Hand eine
Zulassen-Regel anzulegen, schalte **Kontakte immer zulassen** ein — dann wird *jeder, der bereits
in deinem Adressbuch steht, durchgewunken*, egal welche `Sperren`- oder `Lautlos`-Regel sonst
greifen würde. Die Kontakt-Ausnahme hat bedingungslose Priorität: Sie wird geprüft, bevor deine
Regeln überhaupt herangezogen werden.

- Sie läuft komplett auf dem Gerät gegen deine lokalen Kontakte. Wie alles andere hier braucht
  sie kein Netzwerk, und nichts verlässt das Telefon.
- Sie ist **standardmäßig aus** und benötigt die Leseberechtigung für Kontakte, die du explizit
  erteilst.
- Sie schlägt fehl, indem sie schließt (fail closed): Verweigerst oder entziehst du die
  Berechtigung später, schaltet sich die Ausnahme einfach ab — sie öffnet sich nie zu „jeden
  zulassen", sie hört nur auf, irgendjemanden als bekannten Kontakt zu behandeln.

## Funktionen

- **Länderauswahl und Freitext-Vorwahlen** — beide erzeugen dieselbe Art von Regel
- **Whitelist/Blacklist auf einen Blick** — die Regeln-Liste gruppiert exakte `Zulassen`-Einträge
  als Whitelist und alles andere als Blacklist, dieselben zugrunde liegenden Regeln, keine
  getrennte Speicherung; eine Regel zum Löschen wegwischen
- **Unterdrückte Nummern** — ein eigenes `PRIVATE`-Token für Anrufe ohne Anrufer-ID, direkt
  als angepinnter Eintrag in der Länderauswahl wählbar
- **Kontakte-Whitelist** — optional jeden in deinem Adressbuch von jeder Sperrregel ausnehmen,
  lokal ausgewertet mit bedingungsloser Priorität
- **Ehrlich bei Mehrdeutigkeit** — `+1` ist nicht „die USA", sondern die USA, Kanada und zwanzig
  karibische Gebiete. Die App sagt dir das, bevor du sie alle sperrst.
- **Statistiken** — was geprüft wurde, wann, von wo, durch welche Regel, mit einer
  Verteilungsanzeige nach Aktion
- **Optionales Protokollieren zugelassener Anrufe** — standardmäßig aus; wenn aktiviert,
  erscheinen auch durchgelassene Anrufe im Protokoll, samt Grund (Kontakt oder keine
  passende Regel)
- **Wirklich offline** — die App besitzt nicht die `INTERNET`-Berechtigung und *kann* keine
  Netzwerkanfrage stellen. Keine Konten, keine Telemetrie, kein Crash-Reporting, keine Werbung.
- **Optionale Pseudonymisierung** — speichert gesalzene Hashes statt Nummern; Statistiken
  funktionieren weiterhin

## Datenschutz

Das ist der ganze Sinn der App, deshalb wird es strukturell erzwungen statt nur in einer
Erklärung versprochen:

| | |
|---|---|
| Netzwerk-Berechtigung | **Nicht im Manifest deklariert.** Jeder Netzwerkaufruf würde zur Laufzeit fehlschlagen. CI weist einen PR ab, der sie hinzufügt. |
| Das Gerät verlassende Daten | Keine. Es gibt keinen Codepfad, der das könnte. |
| Drittanbieter-SDKs | Keine. |
| Ländernamen und Flaggen | Abgeleitet aus `Locale` und berechneten Unicode-Codepoints. Keine Downloads, keine Assets. |
| Aufbewahrung der Nummern | Lokales SQLite, standardmäßig 90 Tage (in den Einstellungen konfigurierbar: 30/90/180/365 Tage). Abgelaufene Einträge werden automatisch bereinigt, sobald die App oder der Anrufschutz-Dienst startet. Optional werden Nummern stattdessen als gesalzene Hashes gespeichert — Statistiken funktionieren weiterhin; umschaltbar in den Einstellungen. |

Überprüf es selbst: `app/src/main/AndroidManifest.xml` ist kurz und enthält kein
`android.permission.INTERNET`.

## Voraussetzungen

- Android 10 (API 29) oder neuer — erforderlich für `RoleManager.ROLE_CALL_SCREENING` und um
  einen Anruf stummzuschalten, ohne ihn abzuweisen
- Die Anrufschutz-Rolle, die du beim ersten Start erteilst

> **Hinweis:** Android erlaubt immer nur eine Anrufschutz-App gleichzeitig. Erteilst du
> VorwahlGuard die Rolle, deaktiviert das den Spamfilter deiner Telefon-App (meist Google
> Phone). Du kannst jederzeit in Androids Standard-App-Einstellungen zurückwechseln.

## Installation

Lade die neueste APK aus den [Releases](https://github.com/mjanda0815/vorwahlguard/releases)
herunter und installiere sie. Erteile beim ersten Start die Anrufschutz-Rolle, wenn du dazu
aufgefordert wirst.

Aktuell nicht bei Google Play verfügbar — siehe [`PROJECT.md`](PROJECT.md) §9.

## Aus dem Quellcode bauen

```bash
git clone https://github.com/mjanda0815/vorwahlguard.git
cd vorwahlguard
./gradlew build              # kompilieren, Unit-Tests, Lint
./gradlew :app:assembleDebug # → app/build/outputs/apk/debug/
```

Benötigt **JDK 17** (das Android Gradle Plugin unterstützt für den Build selbst keine neueren
JDKs) und das Android SDK. Alles andere löst Gradle automatisch auf.

## Architektur

Eine hexagonale Trennung entlang der einen Grenze, die hier wirklich zählt: Die
Screening-Entscheidung hat nichts mit Android zu tun.

```
┌──────────────────────────────────────────────────────────┐
│  :app                    Kotlin, Android                 │
│                                                          │
│  VorwahlGuardScreeningService ┐  Compose UI (M3)         │
│  Room · Hilt · RoleManager    │  Übersicht/Regeln/…      │
└───────────────────────────────┼──────────────────────────┘
                                │ Ports
┌───────────────────────────────▼──────────────────────────┐
│  :core-domain            Java 17, keine Android-Deps     │
│                                                          │
│  PhoneNumber · Pattern · Rule · RuleMatcher              │
│  CountryCatalog · Konfliktauflösung                      │
│                                                          │
│  → läuft auf einer reinen JVM, getestet mit JUnit 5,     │
│    kein Emulator nötig                                   │
└──────────────────────────────────────────────────────────┘
```

Warum die Trennung: Die Matching-Regeln sind dort, wo die Bugs leben, und ein Emulator ist ein
furchtbarer Ort, um sie zu jagen. `./gradlew :core-domain:test` läuft in unter einer Sekunde.

**Warum nicht überall Kotlin?** Die Domäne hat keinen Grund, zu wissen, was ein `Context` ist.
Warum nicht überall Java? Compose ist ein Kotlin-Compiler-Plugin. **Warum nicht Spring?** Spring
ist auf Android seit Jahren tot; Hilt liefert Dependency Injection zur Kompilierzeit, ganz ohne
Reflection.

Siehe [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) für die Modulgrenzen und
[`PROJECT.md`](PROJECT.md) für die fachliche Spezifikation.

## Die interessante Einschränkung

`CallScreeningService.onScreenCall()` muss innerhalb weniger Sekunden antworten, sonst gibt das
System auf und lässt den Anruf durch. Dieses Zeitbudget schließt eine Datenbankabfrage auf dem
aufrufenden Thread aus — Regeln werden aus einem In-Memory-Snapshot bedient, und das
Anrufereignis wird erst *nachdem* die Antwort bereits gesendet wurde, gespeichert.

Alles andere im Code folgt daraus, und aus einer Regel: **Wirft irgendetwas eine Exception, wird
der Anruf zugelassen.** Ein Anrufschutz, der einen echten Anruf verschluckt, ist schlimmer als
gar kein Anrufschutz.

## Release & Deployment

Einen Release-Build signieren, R8/libphonenumber-Fallstricke und ein GitHub-Release
veröffentlichen: [`docs/RELEASE.md`](docs/RELEASE.md). Auf ein physisches Gerät aus WSL2
deployen (standardmäßig kein USB-Passthrough): [`docs/WSL-ADB.md`](docs/WSL-ADB.md).

## Mitwirken

Git Flow, Feature-Branches, Conventional Commits. Siehe [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Roadmap

- [x] M0 — Projekt-Grundgerüst, CI
- [x] M1 — Domänenkern, vollständig unit-getestet
- [x] M2 — Anrufschutz-Dienst + Rollen-Onboarding
- [x] M3 — Room-Persistenz, Aufbewahrungs-Bereinigung
- [x] M4 — Compose-UI, Länderauswahl, de/en-Lokalisierung
- [x] Opt-in-Kontakte-Whitelist (vorgezogen aus v1.1, siehe oben)
- [ ] M5 — Härtung, `v1.0.0`-Release
- [ ] v1.1 — Import/Export von Regeln

## Lizenz

MIT — siehe [`LICENSE`](LICENSE).

## Haftungsausschluss

Das Verhalten des Anrufschutzes hängt von deiner Android-Version, der Hersteller-Oberfläche und
deinem Mobilfunkanbieter ab. VorwahlGuard kann nicht garantieren, dass ein bestimmter Anruf
blockiert wird. Verlass dich nicht darauf, wenn ein verpasster Anruf Konsequenzen hätte.

Eine Landesvorwahl zu sperren blockiert auch legitime Anrufe aus diesem Land — das ist kein Bug,
das ist das Feature. Und weil Spammer ihre Anrufer-ID fälschen, blockiert `+43*` auch Anrufe, die
nie etwas mit Österreich zu tun hatten, während die Kampagne von morgen stattdessen eine deutsche
Nummer vortäuschen könnte. Diese App verengt den Trichter. Sie schließt ihn nicht.
