# Changelog

## 0.1.0 – 2026-10-17

Erste Testversion für Minecraft 1.21.1 mit NeoForge 21.1.252. Der Mod gehört nur auf den Server, Clients brauchen ihn nicht.

- Zugang über Sponsor-Ketten ab `SERVER`. Die Vanilla-Whitelist wird automatisch gepflegt.
- Befehle: `/sponsor add`, `remove`, `list`, `me`, `tree`, `log`. Konsole und RCON handeln als `SERVER`.
- Regeln: höchstens 3 Sponsorings und 10 h Spielzeit (beides einstellbar). Wer durch ein entferntes Sponsoring den Zugang verliert, wird ausgetragen und gekickt; der Ausführende bekommt eine Zusammenfassung.
- Der erste Start übernimmt die bestehende Whitelist als `SERVER`-Sponsorings. Spätere manuelle `/whitelist add`-Einträge werden ebenfalls übernommen.
- Daten und Protokoll liegen in `world/sponsor/`, die Einstellungen in `config/sponsor-common.toml`.

Noch nicht mit echten Spielern getestet: `/sponsor add` als Spieler (Spielzeit, Limit), `/sponsor me` und der Kick beim Zugangsverlust. Bitte erst auf einem Testserver prüfen, nicht auf dem Produktivserver.

Installation: Jar nach `mods/` kopieren, Details in der README.
