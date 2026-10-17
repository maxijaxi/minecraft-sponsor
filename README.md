# Sponsor

Serverseitiger NeoForge-Mod für **Minecraft 1.21.1** (NeoForge 21.1.252, Java 21). Er ersetzt die manuelle
Whitelist-Pflege durch ein Sponsoring-System.

**Grundregel:** Ein Spieler darf auf den Server, wenn eine Kette von Sponsorings vom Wurzelknoten `SERVER` zu ihm
führt. Die Sponsorings bilden einen gerichteten Graphen, Zyklen sind erlaubt, und die Erreichbarkeit wird per
Breitensuche ab `SERVER` berechnet.

- Die Vanilla-Whitelist bleibt der Türsteher (`white-list=true`, `enforce-whitelist=true`). Der Mod pflegt sie:
  Nach jeder Änderung stehen genau die erreichbaren Spieler darauf. Wer nicht mehr erreichbar ist, wird
  ausgetragen und, falls online, mit einer verständlichen Nachricht gekickt.
- Wer seinen Zugang verliert, behält seine eigenen Sponsorings im Speicher. Sie zählen erst wieder, wenn er
  selbst wieder erreichbar ist.
- Der Mod ist rein serverseitig. Clients brauchen ihn nicht: Er hat keine Netzwerk-Payloads, keine
  Registries/Items/Blöcke und nur Vanilla-Argumenttypen in Befehlen.

## Befehle

| Befehl | Wer | Wirkung |
|---|---|---|
| `/sponsor add <spieler>` | alle | Du sponserst `<spieler>`, er kommt auf die Whitelist. |
| `/sponsor remove <spieler>` | alle | Du beendest dein Sponsoring. Verliert dadurch jemand den Zugang (Kaskade), werden alle Betroffenen ausgetragen bzw. gekickt, und du bekommst eine Zusammenfassung. |
| `/sponsor list` | alle | Wen sponsere ich? (inkl. `x/3`) |
| `/sponsor me` | Spieler | Wer sponsert mich, plus ein Pfad `SERVER -> … -> ich`. |
| `/sponsor tree` | Admins (OP-Level 3+), Konsole | Alle erreichbaren Spieler als eingerückter Baum ab `SERVER` (kürzeste Ketten, weitere Sponsoren in Klammern). Spieler ohne Zugang mit ruhenden Sponsorings werden darunter aufgeführt. |
| `/sponsor log [n]` | Admins, Konsole | Die letzten `n` Protokolleinträge (Standard 10, max. 100). |
| `/sponsor remove <sponsor> <spieler>` | Admins, Konsole | Entfernt ein fremdes Sponsoring. |
| `/sponsor remove SERVER <spieler>` | Admins, Konsole | Entfernt ein Sponsoring durch `SERVER`. |
| `/sponsor` oder `/sponsor help` | alle | Kurzhilfe |

- In der **Konsole/RCON** ist der Sponsor `SERVER`: `/sponsor add Steve` lässt SERVER Steve sponsern, ohne Limit
  und ohne Spielzeit-Pflicht. `/sponsor remove Steve` entfernt das SERVER-Sponsoring.
- Spielernamen werden wie bei `/whitelist add` aufgelöst (GameProfile, notfalls über Mojang). Das klappt also auch
  für Spieler, die noch nie online waren. Gespeichert werden intern immer UUIDs.
- Befehlsblöcke dürfen nicht sponsern. Andere Quellen ohne Spieler ab Level 3 handeln als `SERVER`.

## Regeln

- Höchstens **3** aktive Sponsorings pro Spieler (konfigurierbar).
- Sponsern erst ab **10 Stunden** Spielzeit auf diesem Server (konfigurierbar; Statistik `minecraft:play_time`,
  10 h = 720 000 Ticks).
- Selbst-Sponsoring und doppelte Sponsorings werden abgelehnt.
- Wer selbst keinen Zugang hat (z. B. ein OP ohne Sponsor), kann niemanden sponsern.
- Ein Spieler mit mehreren Sponsoren bleibt, solange mindestens ein Pfad zu `SERVER` existiert.

## Installation auf dem Server

1. Server stoppen und ein **Backup** der Welt sowie der `whitelist.json` anlegen.
2. `sponsor-0.1.0.jar` von der [Releases-Seite](https://github.com/maxijaxi/minecraft-sponsor/releases) herunterladen und nach `~/mc_tinkers/mods/` kopieren. Andere Mods sind nicht nötig.
3. In `server.properties` müssen `white-list=true` und `enforce-whitelist=true` stehen. `online-mode=true`
   bleibt, wie es ist.
4. Server starten. Beim **ersten Start** werden alle bestehenden Whitelist-Einträge als „gesponsert von SERVER“
   übernommen, niemand fliegt raus. Im Server-Log steht dann z. B.:
   ```
   [Sponsor] Erster Start: 42 Whitelist-Einträge als Sponsoring durch SERVER übernommen.
   [Sponsor] Bereit: 42 Spieler mit Zugang, 42 Sponsorings, Daten in .../world/sponsor
   ```
5. Optional `config/sponsor-common.toml` anpassen (wird beim ersten Start angelegt und zur Laufzeit neu geladen).

**Entfernen:** Jar löschen. Die Whitelist bleibt im zuletzt gepflegten Zustand und funktioniert normal weiter.

## Abgleich mit der Whitelist

Der Mod gleicht Whitelist und Graph **bei jedem Start**, **nach jeder Sponsor-Änderung**, nach jedem
`whitelist`-Befehl und nach jedem Login ab:

- **Manuelles `/whitelist add <name>`:** Der Eintrag wird als Sponsoring durch `SERVER` übernommen und im
  Protokoll vermerkt (`WHITELIST_ADOPT`). Admins können also weiterhin einfach `/whitelist add` benutzen. Wird so
  ein Spieler wieder eingetragen, der früher den Zugang verloren hat, gelten auch seine ruhenden Sponsorings wieder.
- **Manuelles `/whitelist remove <name>`** eines gesponserten Spielers wird rückgängig gemacht
  (`WHITELIST_READD`), denn maßgeblich ist der Graph. Zum Entziehen `/sponsor remove …` verwenden.
- **OPs** kommen in Vanilla auch ohne Whitelist-Eintrag auf den Server. Verliert ein OP den Zugang, wird er
  ausgetragen, aber nicht gekickt, sondern nur informiert.

## Konfiguration (`config/sponsor-common.toml`)

```toml
[rules]
  maxSponsorships = 3        # Sponsorings pro Spieler
  minPlayTimeTicks = 720000  # 10 h Spielzeit (20 Ticks = 1 s)

[messages]
  kick = "Dein Zugang zu diesem Server wurde entzogen: ..."
  # ... alle Spielernachrichten, mit Platzhaltern wie {player}, {count}, {max}
```

Die Datei liegt bewusst unter `config/` (Typ COMMON). Eine SERVER-Config würde NeoForge an die Clients
synchronisieren, das widerspräche dem rein serverseitigen Ansatz.

## Daten & Protokoll

Alles liegt im Weltordner und landet damit im Welt-Backup:

- `world/sponsor/graph.json`: Sponsorings (UUIDs), zuletzt bekannte Namen und der Whitelist-Stand nach dem
  letzten Abgleich. Geschrieben wird atomar (temporäre Datei + fsync + Rename).
- `world/sponsor/log.jsonl`: Append-only-Protokoll, eine JSON-Zeile pro Ereignis mit Zeitstempel, Aktion,
  Ausführendem, Ziel und bei Kaskaden allen Betroffenen mit Grund, z. B.:
  ```json
  {"time":"2026-10-06T18:00:00Z","action":"REMOVE","actor":{"uuid":"SERVER","name":"SERVER"},"sponsor":{"uuid":"SERVER","name":"SERVER"},"target":{"uuid":"…","name":"Alice"},"lostAccess":[{"uuid":"…","name":"Alice","reason":"Sponsoring durch SERVER entfernt, keine weiteren Sponsoren"},{"uuid":"…","name":"Dave","reason":"Kette zu SERVER unterbrochen, Sponsoren ohne Zugang: Alice"}]}
  ```
  Aktionen: `ADD`, `REMOVE`, `MIGRATION`, `WHITELIST_ADOPT`, `WHITELIST_READD`, `WHITELIST_ADD`, `WHITELIST_REMOVE`.

Kann `graph.json` nicht gelesen werden (z. B. von Hand kaputt editiert), bleibt der Mod **deaktiviert und verändert
die Whitelist nicht**. Er überschreibt die Datei auch nicht. Dann die Datei aus dem Backup zurückholen und neu
starten.

## Bauen & Testen (nur Entwicklungsumgebung)

```bash
./gradlew build      # kompiliert, führt die Unit-Tests aus, Jar unter build/libs/sponsor-0.1.0.jar
./gradlew test       # nur die Unit-Tests der Graph-Logik
./gradlew runServer  # Dev-Server in ./run mit leerer Welt (vorher eula=true in run/eula.txt setzen)
```

- Die Graph-Logik (`io.github.maxijaxi.sponsor.core`) kennt Minecraft nicht und ist mit JUnit getestet: Zyklen,
  Mehrfach-Sponsoren, Kaskaden, Limit, Spielzeit, Selbst-Sponsoring, Migration/Abgleich, Speichern und Protokoll.
- `.github/scripts/smoke_test.py` startet `./gradlew runServer` in einem **frischen** `run/`-Ordner (wird vorher
  gelöscht) und prüft Migration, Konsolenbefehle, Kaskade, `whitelist add/remove` und das Protokoll per RCON.
  Es nutzt nur erfundene Spieler. **Nie** auf dem Produktivserver oder mit dessen Welt ausführen.
- GitHub Actions (`.github/workflows/build.yml`) führt Build, Unit-Tests und den Smoke-Test bei jedem Push aus und
  stellt die fertige Jar als Artefakt `sponsor-mod-jar` bereit.

## Release erstellen

1. `mod_version` in `gradle.properties` erhöhen und in `CHANGELOG.md` einen Abschnitt `## <version>` ergänzen.
2. Auf GitHub unter **Actions → Build & Test → Run workflow** den Branch `main` wählen und `release` anhaken.
3. Nur wenn Build, Unit-Tests und Smoke-Test grün sind, legt der Workflow den Tag `v<version>` und ein Release mit
   der Jar an. Die Release-Notizen kommen aus dem passenden `CHANGELOG.md`-Abschnitt.

## Technisches

- Der Mod lauscht nur auf Befehle (`RegisterCommandsEvent`, `CommandEvent` zum Erkennen von `whitelist`-Befehlen),
  Login, Serverstart/-stopp und das Tick-Ende, an dem ein vorgemerkter Abgleich ausgeführt wird. Er hat keine
  Abhängigkeiten zu anderen Mods.
- Ein Access Transformer macht `StoredUserEntry#getUser()` lesbar, damit UUID und Name der Whitelist-Einträge
  gelesen werden können.
- Auf Clients (Einzelspieler/LAN) wird der Mod nicht geladen (`dist = DEDICATED_SERVER`).
