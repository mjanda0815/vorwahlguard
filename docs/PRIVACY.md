# Datenschutzerklärung – VorwahlGuard

**Stand: 15. Juli 2026**

> English version: [PRIVACY.en.md](PRIVACY.en.md)

## Kurzfassung

VorwahlGuard ist eine reine Offline-App. Die App besitzt **keine
Internet-Berechtigung** und kann technisch keine Daten an einen Server, an den
Entwickler oder an Dritte senden. Alle Verarbeitung findet ausschließlich auf
deinem Gerät statt. Es gibt keine Analyse, kein Crash-Reporting, keine Werbung
und kein Tracking.

## 1. Verantwortlicher

Martin Janda
Kontakt: `<deine-kontakt-email-hier-eintragen>`

## 2. Grundprinzip: keine Datenübertragung

Die App deklariert in ihrem Android-Manifest **keine der Berechtigungen**
`INTERNET` oder `ACCESS_NETWORK_STATE`. Ohne diese Berechtigungen kann eine
Android-App keine Netzwerkverbindung aufbauen. Das ist keine Absichtserklärung,
sondern eine technische Sperre: Telefonnummern und Nutzungsdaten **verlassen
das Gerät nicht** und können es nicht verlassen.

## 3. Welche Daten die App verarbeitet – und wo sie bleiben

Alle folgenden Daten werden ausschließlich lokal auf dem Gerät gespeichert
(in der privaten, nur der App zugänglichen App-Datenbank):

| Daten | Zweck | Speicherort | Aufbewahrung |
|---|---|---|---|
| Rufnummer eingehender Anrufe (E.164) | Abgleich mit deinen Regeln | nur lokal | siehe unten |
| Von dir angelegte Sperr-/Regel-Muster | Anruf-Screening | nur lokal | bis du sie löschst |
| Protokoll gescreenter Anrufe (Nummer/Region, Zeitpunkt, Aktion) | Statistik & Nachvollziehbarkeit | nur lokal | Standard 90 Tage, einstellbar |

- **Ausgehende Anrufe werden nicht verarbeitet und nicht protokolliert.**
- Optional kannst du in den Einstellungen aktivieren, dass Nummern im Protokoll
  **pseudonymisiert** (als kryptografischer Hash mit gerätelokalem Salt) statt
  im Klartext gespeichert werden.
- Das Protokoll wird automatisch nach der eingestellten Aufbewahrungsdauer
  (Standard 90 Tage) bereinigt.

## 4. Berechtigungen und ihr Zweck

Die App fordert nur die für ihre Funktion nötigen Berechtigungen an:

- **Anruf-Screening-Rolle** (`ROLE_CALL_SCREENING`): Du wählst VorwahlGuard im
  System-Dialog aktiv als Anrufschutz-App. Nur damit kann die App eingehende
  Anrufe prüfen und – **ausschließlich nach den von dir angelegten Regeln** –
  blockieren oder stummschalten.
- **Kontakte lesen** (`READ_CONTACTS`, **optional, standardmäßig deaktiviert**):
  Nur wenn du die Kontakte-Ausnahme einschaltest, gleicht die App eingehende
  Nummern mit deinem Adressbuch ab, um bekannte Kontakte durchzulassen. Der
  Abgleich passiert vollständig auf dem Gerät; es werden keine Kontaktdaten
  gespeichert oder übertragen. Wird die Berechtigung entzogen, verhält sich die
  App fail-closed (kein Abgleich).

## 5. Keine Weitergabe an Dritte

Es werden keine Daten an Dritte weitergegeben, verkauft oder mit ihnen geteilt,
weil die App technisch nicht in der Lage ist, Daten zu übertragen. Es sind keine
SDKs für Analyse, Werbung oder Absturzberichte eingebunden.

## 6. Löschung

- Du kannst Regeln jederzeit in der App löschen.
- Das Protokoll wird nach der eingestellten Aufbewahrungsdauer automatisch
  bereinigt.
- **Deinstallierst du die App, werden alle lokal gespeicherten Daten
  vollständig entfernt.** Ein Cloud-Backup findet nicht statt (`allowBackup`
  ist deaktiviert).

## 7. Deine Rechte

Da keine personenbezogenen Daten das Gerät verlassen und der Entwickler zu
keinem Zeitpunkt Zugriff auf deine Daten hat, kann und muss der Entwickler
keine Auskunft, Löschung oder Herausgabe vornehmen – du hast die volle Kontrolle
direkt in der App und auf deinem Gerät. Die dir nach der DSGVO zustehenden
Rechte (Auskunft, Berichtigung, Löschung, Einschränkung) übst du unmittelbar
über die App und die Geräteeinstellungen aus.

## 8. Änderungen dieser Erklärung

Bei funktionalen Änderungen der App wird diese Erklärung angepasst. Das oben
genannte Datum weist die jeweils aktuelle Fassung aus.
