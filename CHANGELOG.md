# Changelog

## 0.2.0 – 2026-10-17

- Englisch ist jetzt die Standardsprache. Spieler mit deutscher Spielsprache (`de_*`) bekommen alle Nachrichten auf
  Deutsch, auch die Kick-Nachricht. Der Server liest die Spielsprache des Clients, einen Client-Mod braucht es nicht.
- Neue Config-Optionen unter `[language]`: `default` (`en`/`de`) für Konsole, RCON und Protokoll sowie
  `useClientLanguage`.
- Die Nachrichtentexte stehen jetzt getrennt in `[messages.en]` und `[messages.de]`. Wer in 0.1.0 Texte unter
  `[messages]` angepasst hat, muss sie dort neu eintragen; die alten Einträge entfernt NeoForge beim Start.
- `/sponsor tree` und `/sponsor log` sind ebenfalls zweisprachig, die Befehlsargumente heißen jetzt `<player>` und
  `<target>`.
- Deutsche Mundarten des Spiels (Boarisch, Kölsch, Plattdüütsch, Schwäbisch, Säggs'sch) bekommen ebenfalls Deutsch.
- Server-Log-Meldungen des Mods sind auf Englisch, unabhängig von `default`.
- Der Hinweis nach `/whitelist remove` erklärt jetzt, dass Minecraft einen Spieler, der online ist, dabei einmal
  kickt, bevor der Mod ihn wieder einträgt.

## 0.1.0 – 2026-10-17

Erste Testversion für Minecraft 1.21.1 mit NeoForge 21.1.252. Der Mod gehört nur auf den Server, Clients brauchen ihn nicht.

- Zugang über Sponsor-Ketten ab `SERVER`. Die Vanilla-Whitelist wird automatisch gepflegt.
- Befehle: `/sponsor add`, `remove`, `list`, `me`, `tree`, `log`. Konsole und RCON handeln als `SERVER`.
- Regeln: höchstens 3 Sponsorings und 10 h Spielzeit (beides einstellbar). Wer durch ein entferntes Sponsoring den Zugang verliert, wird ausgetragen und gekickt; der Ausführende bekommt eine Zusammenfassung.
- Der erste Start übernimmt die bestehende Whitelist als `SERVER`-Sponsorings. Spätere manuelle `/whitelist add`-Einträge werden ebenfalls übernommen.
- Daten und Protokoll liegen in `world/sponsor/`, die Einstellungen in `config/sponsor-common.toml`.

Noch nicht mit echten Spielern getestet: `/sponsor add` als Spieler (Spielzeit, Limit), `/sponsor me` und der Kick beim Zugangsverlust. Bitte erst auf einem Testserver prüfen, nicht auf dem Produktivserver.

Installation: Jar nach `mods/` kopieren, Details in der README.
