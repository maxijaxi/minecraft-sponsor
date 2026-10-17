#!/usr/bin/env python3
"""Smoke-Test für den Sponsor-Mod.

Startet den Dev-Server des MDK (``./gradlew runServer``) in einer frischen, leeren ``run/``-Umgebung, steuert ihn
per RCON als Konsole (Sponsor "SERVER") und prüft whitelist.json, world/sponsor/graph.json und log.jsonl.

Es werden ausschließlich erfundene Spieler verwendet; ihre Namen werden über eine vorbereitete usercache.json
aufgelöst, damit keine Anfragen an Mojang nötig sind.

Achtung: Das Skript LÖSCHT ``run/`` vor dem Start. Niemals mit einer echten Welt verwenden.
"""
import json
import os
import pathlib
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
RUN = ROOT / "run"
WORLD = RUN / "world"
PASSWORD = "sponsor-ci"
PORT = 25575
NAMES = ["Alice", "Bob", "Carol", "Dave", "Eve", "Frank"]
UUIDS = {name: str(uuid.uuid5(uuid.NAMESPACE_URL, "sponsor-ci/" + name)) for name in NAMES}
SERVER = "SERVER"
FAILURES = []


# ----------------------------------------------------------------------------- helpers

def check(condition, message):
    status = "OK  " if condition else "FAIL"
    print(f"[{status}] {message}", flush=True)
    if not condition:
        FAILURES.append(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def whitelist_names():
    return sorted(entry["name"] for entry in read_json(RUN / "whitelist.json"))


def graph():
    return read_json(WORLD / "sponsor" / "graph.json")


def edges():
    names = {v: k for k, v in UUIDS.items()}
    names[SERVER] = SERVER
    return sorted((names.get(e["sponsor"], e["sponsor"]), names.get(e["target"], e["target"])) for e in graph()["edges"])


def log_actions():
    path = WORLD / "sponsor" / "log.jsonl"
    if not path.exists():
        return []
    return [(e["action"], e.get("target", {}).get("name")) for e in map(json.loads, path.read_text(encoding="utf-8").splitlines())]


def wait_for(predicate, message, timeout=10.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if predicate():
                check(True, message)
                return
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(0.25)
    check(False, message)


class Rcon:
    def __init__(self):
        self.sock = socket.create_connection(("127.0.0.1", PORT), timeout=30)
        self.next_id = 0
        request_id, _ = self._request(3, PASSWORD)
        if request_id == -1:
            raise RuntimeError("RCON-Login fehlgeschlagen")

    def _recv_exact(self, length):
        data = b""
        while len(data) < length:
            chunk = self.sock.recv(length - len(data))
            if not chunk:
                raise ConnectionError("RCON-Verbindung geschlossen")
            data += chunk
        return data

    def _read_packet(self):
        length = struct.unpack("<i", self._recv_exact(4))[0]
        body = self._recv_exact(length)
        request_id, _kind = struct.unpack("<ii", body[:8])
        return request_id, body[8:-2].decode("utf-8")

    def _request(self, kind, payload):
        self.next_id += 1
        data = struct.pack("<ii", self.next_id, kind) + payload.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)
        request_id, text = self._read_packet()
        # Lange Antworten teilt Minecraft in mehrere Pakete auf.
        self.sock.settimeout(0.5)
        try:
            while True:
                more_id, more = self._read_packet()
                if more_id != request_id:
                    break
                text += more
        except (socket.timeout, TimeoutError):
            pass
        finally:
            self.sock.settimeout(30)
        return request_id, text

    def command(self, command):
        _, text = self._request(2, command)
        print(f"> {command}\n{text.rstrip()}\n", flush=True)
        return text

    def close(self):
        self.sock.close()


# ------------------------------------------------------------------------ server control

def prepare_run_dir():
    if RUN.exists():
        shutil.rmtree(RUN)
    RUN.mkdir()
    # Zum Starten eines Minecraft-Servers muss die Mojang-EULA akzeptiert sein (nur für diesen CI-Testserver).
    (RUN / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (RUN / "server.properties").write_text("\n".join([
        "enable-rcon=true",
        f"rcon.password={PASSWORD}",
        f"rcon.port={PORT}",
        "white-list=true",
        "enforce-whitelist=true",
        "online-mode=true",
        "level-type=minecraft\\:flat",
        "generate-structures=false",
        "spawn-protection=0",
        "",
    ]), encoding="utf-8")
    (RUN / "whitelist.json").write_text(json.dumps(
        [{"uuid": UUIDS["Alice"], "name": "Alice"}, {"uuid": UUIDS["Bob"], "name": "Bob"}], indent=2), encoding="utf-8")
    (RUN / "usercache.json").write_text(json.dumps(
        [{"name": name, "uuid": UUIDS[name], "expiresOn": "2099-01-01 00:00:00 +0000"} for name in NAMES], indent=2),
        encoding="utf-8")


def start_server(log_name):
    log = open(ROOT / log_name, "w", encoding="utf-8")
    process = subprocess.Popen(["./gradlew", "--no-daemon", "--console=plain", "runServer"], cwd=ROOT,
                               stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
    deadline = time.time() + 900
    while time.time() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Server beendet (Exit {process.returncode}), siehe {log_name}")
        try:
            rcon = Rcon()
        except (OSError, RuntimeError):
            time.sleep(3)
            continue
        # RCON nimmt Logins schon vor dem ersten Tick an; ein Befehl läuft aber erst, wenn der Server
        # vollständig gestartet ist (also nach ServerStartedEvent und damit nach dem ersten Abgleich).
        rcon.command("list")
        print(f"Server bereit ({log_name})", flush=True)
        return process, rcon
    process.kill()
    raise RuntimeError("Server ist nicht rechtzeitig gestartet")


def stop_server(process, rcon):
    try:
        rcon.command("stop")
    except (OSError, ConnectionError):
        pass
    finally:
        rcon.close()
    try:
        process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        process.kill()
        check(False, "Server hat sich nicht rechtzeitig beendet")


# --------------------------------------------------------------------------------- tests

def phase_one():
    print("=== Phase 1 (Englisch): Migration, Konsole als SERVER, manuelle Whitelist-Befehle", flush=True)
    process, rcon = start_server("server-phase1.log")
    try:
        check(edges() == [(SERVER, "Alice"), (SERVER, "Bob")], f"Migration: Whitelist als SERVER-Sponsoring übernommen {edges()}")
        check(log_actions().count(("MIGRATION", "Alice")) == 1 and log_actions().count(("MIGRATION", "Bob")) == 1,
              "Migration im Protokoll vermerkt")
        check(whitelist_names() == ["Alice", "Bob"], "Migration: niemand von der Whitelist entfernt")

        out = rcon.command("sponsor add Carol")
        check("SERVER now sponsors Carol" in out, "Konsole sponsert als SERVER (Englisch als Standard)")
        check("Carol" in whitelist_names(), "Gesponserter Spieler steht auf der Whitelist")
        check((SERVER, "Carol") in edges(), "Kante SERVER -> Carol gespeichert")

        out = rcon.command("sponsor add Carol")
        check("SERVER already sponsors Carol" in out, "Doppelte Kante wird abgelehnt")

        out = rcon.command("sponsor list")
        check("SERVER sponsors 3 player(s): Alice, Bob, Carol" in out, "/sponsor list für die Konsole")

        out = rcon.command("sponsor me")
        check("for players only" in out, "/sponsor me ist für die Konsole gesperrt")

        rcon.command("whitelist add Frank")
        wait_for(lambda: (SERVER, "Frank") in edges(), "Manuelles 'whitelist add' wird als SERVER-Sponsoring übernommen")
        check(("WHITELIST_ADOPT", "Frank") in log_actions(), "Übernahme im Protokoll vermerkt")

        rcon.command("whitelist remove Bob")
        wait_for(lambda: "Bob" in whitelist_names(), "Manuell entfernter, aber gesponserter Spieler wird wieder eingetragen")
        check(("WHITELIST_READD", "Bob") in log_actions(), "Wiedereintrag im Protokoll vermerkt")

        out = rcon.command("sponsor remove Frank")
        check("Sponsorship SERVER -> Frank ended" in out, "Konsole entfernt SERVER-Sponsoring")
        check("Frank" not in whitelist_names(), "Spieler ohne Sponsor fliegt von der Whitelist")

        out = rcon.command("sponsor log 2")
        check("SERVER ended the sponsorship of Frank | lost access: Frank (sponsorship by SERVER removed, no other sponsors)" in out,
              "/sponsor log auf Englisch, Grund in der Standardsprache gespeichert")
        config = (RUN / "config" / "sponsor-common.toml").read_text(encoding="utf-8")
        check("useClientLanguage" in config and "[messages.en]" in config and "[messages.de]" in config,
              "Config enthält Sprachwahl und Texte für Englisch und Deutsch")
    finally:
        stop_server(process, rcon)


def phase_two():
    print("=== Phase 2 (Deutsch): Spieler-Kanten (offline eingetragen), Baum, Kaskade, ruhende Kanten", flush=True)
    data = graph()
    data["players"].update({UUIDS["Dave"]: "Dave", UUIDS["Eve"]: "Eve"})
    data["edges"] += [
        {"sponsor": UUIDS["Alice"], "target": UUIDS["Dave"], "since": "2026-01-01T12:00:00Z"},
        {"sponsor": UUIDS["Dave"], "target": UUIDS["Eve"], "since": "2026-01-01T12:00:00Z"},
        {"sponsor": UUIDS["Eve"], "target": UUIDS["Alice"], "since": "2026-01-01T12:00:00Z"},
        {"sponsor": UUIDS["Bob"], "target": UUIDS["Carol"], "since": "2026-01-01T12:00:00Z"},
    ]
    (WORLD / "sponsor" / "graph.json").write_text(json.dumps(data, indent=2), encoding="utf-8")
    # Ab hier Deutsch als Standardsprache (Konsole, RCON, Protokoll).
    config_file = RUN / "config" / "sponsor-common.toml"
    config, replaced = re.subn(r'(?m)^(\s*default\s*=\s*)"en"', r'\1"de"', config_file.read_text(encoding="utf-8"))
    check(replaced == 1, "Standardsprache in der Config auf Deutsch umgestellt")
    config_file.write_text(config, encoding="utf-8")

    process, rcon = start_server("server-phase2.log")
    try:
        check({"Dave", "Eve"} <= set(whitelist_names()), "Abgleich beim Start trägt neu erreichbare Spieler ein")

        out = rcon.command("sponsor tree")
        check("  - Alice" in out and "    - Dave" in out and "      - Eve" in out, "/sponsor tree zeigt die Ketten eingerückt")
        check("- Carol  (auch: Bob)" in out, "/sponsor tree nennt weitere Sponsoren")

        out = rcon.command("sponsor remove Bob Carol")
        check("Sponsoring Bob -> Carol beendet" in out and "Carol behält den Zugang" in out,
              "Admin entfernt fremde Kante, Mehrfach-Sponsoring bleibt erhalten")
        check("Carol" in whitelist_names(), "Carol bleibt über SERVER auf der Whitelist")

        out = rcon.command("sponsor remove Bob Carol")
        check("Bob sponsert Carol nicht" in out, "Nicht vorhandene Kante wird gemeldet")

        out = rcon.command("sponsor remove SERVER Alice")
        check("3 Spieler ihren Zugang verloren" in out, "Kaskade wird zusammengefasst")
        check(not ({"Alice", "Dave", "Eve"} & set(whitelist_names())), "Kaskade entfernt Alice, Dave und Eve (inkl. Zyklus)")
        check(("Alice", "Dave") in edges() and ("Dave", "Eve") in edges(), "Kanten nicht erreichbarer Spieler bleiben gespeichert")
        removal = [json.loads(line) for line in (WORLD / "sponsor" / "log.jsonl").read_text(encoding="utf-8").splitlines()][-1]
        check(removal["action"] == "REMOVE" and len(removal.get("lostAccess", [])) == 3
              and all(item.get("reason") for item in removal["lostAccess"]), "Protokoll enthält alle Betroffenen mit Grund")

        out = rcon.command("sponsor log 5")
        check("SERVER beendet Sponsoring für Alice | Zugang verloren: Alice" in out, "/sponsor log zeigt lesbare Einträge")

        rcon.command("whitelist add Alice")
        wait_for(lambda: {"Alice", "Dave", "Eve"} <= set(whitelist_names()),
                 "Wieder eingetragener Spieler aktiviert seine ruhenden Sponsorings")
    finally:
        stop_server(process, rcon)


def main():
    os.chdir(ROOT)
    prepare_run_dir()
    phase_one()
    phase_two()
    for log_name in ("server-phase1.log", "server-phase2.log"):
        text = (ROOT / log_name).read_text(encoding="utf-8", errors="replace")
        check("[Sponsor] Ready" in text, f"{log_name}: Mod gestartet")
        check("[Sponsor] Error" not in text and "[Sponsor] Startup failed" not in text, f"{log_name}: keine Mod-Fehler")
    if FAILURES:
        print(f"\n{len(FAILURES)} Prüfung(en) fehlgeschlagen:", *FAILURES, sep="\n - ")
        sys.exit(1)
    print("\nAlle Smoke-Test-Prüfungen bestanden.")


if __name__ == "__main__":
    main()
