package io.github.maxijaxi.sponsor;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Einstiegspunkt. Der Mod ist rein serverseitig: keine Registries, keine Netzwerk-Payloads, nur Vanilla-Argumenttypen
 * in Befehlen. Clients brauchen ihn nicht. Auf Clients (Einzelspieler/LAN) wird er gar nicht erst geladen.
 */
@Mod(value = SponsorMod.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class SponsorMod {
    public static final String MOD_ID = "sponsor";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Nur auf dem Server-Thread verwendet; {@code null}, solange kein Server läuft oder das Laden fehlschlug. */
    private static SponsorController controller;

    public SponsorMod(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SponsorConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(SponsorMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SponsorMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SponsorMod::onServerStopping);
        NeoForge.EVENT_BUS.addListener(SponsorMod::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SponsorMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SponsorMod::onCommand);
        NeoForge.EVENT_BUS.addListener(SponsorMod::onServerTick);
    }

    static SponsorController controller() {
        return controller;
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        SponsorCommands.register(event.getDispatcher(), SponsorMod::controller);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            controller = SponsorController.start(event.getServer());
        } catch (Exception e) {
            controller = null;
            LOGGER.error("[Sponsor] Start fehlgeschlagen. Das Sponsor-System bleibt deaktiviert und verändert die Whitelist nicht. "
                    + "Bitte world/sponsor/graph.json prüfen (Backup?) und den Server neu starten.", e);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        if (controller != null) {
            controller.shutdown();
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        controller = null;
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (controller != null && event.getEntity() instanceof ServerPlayer player) {
            guarded("Login", () -> controller.onLogin(player));
        }
    }

    private static void onCommand(CommandEvent event) {
        if (controller != null) {
            guarded("Befehl", () -> controller.onCommandInput(
                    event.getParseResults().getContext().getSource(),
                    event.getParseResults().getReader().getString()));
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (controller != null) {
            guarded("Whitelist-Abgleich", controller::tick);
        }
    }

    /** Fehler im Mod dürfen niemals den Server-Tick oder einen Login abbrechen. */
    private static void guarded(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.error("[Sponsor] Fehler bei {}", what, e);
        }
    }
}
