package io.github.maxijaxi.sponsor;

import com.mojang.authlib.GameProfile;
import io.github.maxijaxi.sponsor.core.AuditLog;
import io.github.maxijaxi.sponsor.core.CoreText;
import io.github.maxijaxi.sponsor.core.Lang;
import io.github.maxijaxi.sponsor.core.LogEntry;
import io.github.maxijaxi.sponsor.core.LogEntry.Action;
import io.github.maxijaxi.sponsor.core.LogEntry.Affected;
import io.github.maxijaxi.sponsor.core.LogEntry.PlayerRef;
import io.github.maxijaxi.sponsor.core.SponsorData;
import io.github.maxijaxi.sponsor.core.SponsorGraph;
import io.github.maxijaxi.sponsor.core.SponsorRules;
import io.github.maxijaxi.sponsor.core.SponsorService;
import io.github.maxijaxi.sponsor.core.SponsorService.AddResult;
import io.github.maxijaxi.sponsor.core.SponsorService.LostAccess;
import io.github.maxijaxi.sponsor.core.SponsorService.ReconcilePlan;
import io.github.maxijaxi.sponsor.core.SponsorService.RemoveResult;
import io.github.maxijaxi.sponsor.core.SponsorStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * Verbindet die Minecraft-unabhängige Fachlogik mit dem laufenden Server: Befehle ausführen, die Vanilla-Whitelist
 * pflegen, Spieler kicken, speichern und protokollieren. Alle Methoden laufen auf dem Server-Thread.
 */
public final class SponsorController {
    private static final Logger LOGGER = SponsorMod.LOGGER;
    /** Erkennt Whitelist-Befehle, auch verschachtelt (z. B. {@code execute ... run whitelist add}). */
    private static final Pattern WHITELIST_ANYWHERE = Pattern.compile("(^|[\\s:/])whitelist(\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITELIST_EDIT = Pattern.compile("^/?(?:minecraft:)?whitelist\\s+(add|remove)(\\s|$)", Pattern.CASE_INSENSITIVE);

    private final MinecraftServer server;
    private final SponsorData data;
    private final SponsorService service;
    private final SponsorStorage storage;
    private final AuditLog auditLog;
    private final Comparator<UUID> byName;
    private boolean syncPending;

    private SponsorController(MinecraftServer server, SponsorData data, SponsorStorage storage, AuditLog auditLog) {
        this.server = server;
        this.data = data;
        this.service = new SponsorService(data);
        this.storage = storage;
        this.auditLog = auditLog;
        this.byName = Comparator.comparing((UUID id) -> data.nameOf(id), String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.naturalOrder());
    }

    /**
     * Lädt {@code <welt>/sponsor/graph.json} (oder migriert beim ersten Start die bestehende Whitelist) und gleicht
     * die Whitelist mit dem Graphen ab.
     *
     * @throws IOException wenn die Daten nicht gelesen oder gespeichert werden können; der Mod bleibt dann inaktiv
     */
    public static SponsorController start(MinecraftServer server) throws IOException {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("sponsor").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        SponsorStorage storage = new SponsorStorage(dir.resolve("graph.json"));
        boolean firstStart = !storage.exists();
        SponsorData data = firstStart ? new SponsorData() : storage.load();
        SponsorController controller = new SponsorController(server, data, storage, new AuditLog(dir.resolve("log.jsonl")));
        controller.startup(firstStart, dir);
        return controller;
    }

    private void startup(boolean firstStart, Path dir) throws IOException {
        if (!server.getPlayerList().isUsingWhitelist()) {
            LOGGER.warn("[Sponsor] The whitelist is turned off (white-list=false). The mod still maintains it, "
                    + "but Minecraft lets every player in. Set white-list=true to use the sponsor system.");
        }
        ReconcilePlan plan = synchronize(firstStart ? Action.MIGRATION : Action.WHITELIST_ADOPT, Set.of());
        storage.save(data);
        if (firstStart) {
            LOGGER.info("[Sponsor] First start: took over {} whitelist entries as sponsorships by SERVER.", plan.adopted().size());
        }
        LOGGER.info("[Sponsor] Ready: {} players with access, {} sponsorships, data in {}",
                data.graph().reachable().size(), data.graph().edges().size(), dir);
    }

    public void shutdown() {
        save();
    }

    // ------------------------------------------------------------- events

    /** Aufgerufen vor jedem Befehl: Whitelist-Befehle lösen nach ihrer Ausführung einen Abgleich aus. */
    public void onCommandInput(CommandSourceStack source, String input) {
        String command = input.strip();
        if (!WHITELIST_ANYWHERE.matcher(command).find()) {
            return;
        }
        syncPending = true;
        Matcher edit = WHITELIST_EDIT.matcher(command);
        if (edit.find() && source.hasPermission(Commands.LEVEL_ADMINS)) {
            Msg hint = "add".equalsIgnoreCase(edit.group(1)) ? Msg.WHITELIST_ADD_HINT : Msg.WHITELIST_REMOVE_HINT;
            Lang lang = SponsorConfig.languageOf(source);
            source.sendSuccess(() -> hint.component(lang).withStyle(ChatFormatting.GRAY), false);
        }
    }

    public void onLogin(ServerPlayer player) {
        data.rememberName(player.getUUID(), player.getGameProfile().getName());
        syncPending = true;
    }

    /** Am Ende jedes Server-Ticks: ausstehenden Abgleich (nach Whitelist-Befehlen oder Logins) ausführen. */
    public void tick() {
        if (!syncPending) {
            return;
        }
        syncPending = false;
        synchronize(Action.WHITELIST_ADOPT, Set.of());
        save();
    }

    // ----------------------------------------------------------- commands

    public int add(CommandSourceStack source, GameProfile target) {
        Lang lang = SponsorConfig.languageOf(source);
        Actor actor = actor(source);
        if (actor == null) {
            return fail(source, Msg.NOT_ALLOWED.component(lang));
        }
        SponsorRules rules = SponsorConfig.rules();
        long playTime = actor.isServer() ? Long.MAX_VALUE : actor.player().getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        AddResult result = service.add(actor.id(), target.getId(), playTime, rules, Instant.now());
        String targetName = target.getName();
        Component error = switch (result.status()) {
            case OK -> null;
            case SELF -> Msg.SELF.component(lang);
            case ALREADY_SPONSORING -> (actor.isServer() ? Msg.ALREADY_SPONSORING_SERVER : Msg.ALREADY_SPONSORING).component(lang, "player", targetName);
            case NO_ACCESS -> Msg.NO_ACCESS.component(lang);
            case NOT_ENOUGH_PLAYTIME -> Msg.NOT_ENOUGH_PLAYTIME.component(lang, 
                    "required", formatPlayTime(rules.minPlayTimeTicks()), "current", formatPlayTime(playTime));
            case LIMIT_REACHED -> Msg.LIMIT_REACHED.component(lang, "count", result.sponsorshipCount(), "max", rules.maxSponsorships());
        };
        if (error != null) {
            return fail(source, error);
        }

        data.rememberName(target.getId(), targetName);
        boolean saved = save();
        List<Affected> gained = result.gainedAccess().stream()
                .map(id -> new Affected(ref(id), (id.equals(target.getId()) ? CoreText.DETAIL_NEWLY_SPONSORED : CoreText.DETAIL_DORMANT_REACTIVATED)
                        .format(SponsorConfig.defaultLanguage())))
                .toList();
        appendLog(new LogEntry(Instant.now(), Action.ADD, actor.ref(), null, ref(target.getId()), List.of(), gained, null));
        synchronize(Action.WHITELIST_ADOPT, Set.copyOf(result.gainedAccess()));
        saved &= save();

        send(source, (actor.isServer()
                ? Msg.ADD_SUCCESS_SERVER.component(lang, "player", targetName)
                : Msg.ADD_SUCCESS.component(lang, "player", targetName, "count", result.sponsorshipCount(), "max", rules.maxSponsorships()))
                .withStyle(ChatFormatting.GREEN));
        send(source, (result.targetHadAccess()
                ? Msg.ADD_ALREADY_HAD_ACCESS.component(lang, "player", targetName)
                : Msg.ADD_NEW_ACCESS.component(lang, "player", targetName)).withStyle(ChatFormatting.GRAY));
        List<UUID> alsoRestored = result.gainedAccess().stream().filter(id -> !id.equals(target.getId())).toList();
        if (!alsoRestored.isEmpty()) {
            send(source, Msg.ADD_ALSO_RESTORED.component(lang, "players", names(alsoRestored)).withStyle(ChatFormatting.GRAY));
        }
        if (!saved) {
            source.sendFailure(Msg.SAVE_FAILED.component(lang));
        }
        ServerPlayer online = server.getPlayerList().getPlayer(target.getId());
        if (online != null) {
            online.sendSystemMessage(Msg.SPONSORED_NOTICE.component(SponsorConfig.languageOf(online), "sponsor", actor.ref().name()).withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    /** {@code /sponsor remove <spieler>}: eigenes Sponsoring beenden (Konsole: Sponsoring durch SERVER). */
    public int remove(CommandSourceStack source, GameProfile target) {
        Lang lang = SponsorConfig.languageOf(source);
        Actor actor = actor(source);
        if (actor == null) {
            return fail(source, Msg.NOT_ALLOWED.component(lang));
        }
        return removeEdge(source, lang, actor, actor.id(), target.getId(), target.getName());
    }

    /** {@code /sponsor remove <sponsor> <spieler>}: fremdes Sponsoring entfernen (nur Admins, per Befehlsbaum geprüft). */
    public int removeAsAdmin(CommandSourceStack source, UUID sponsor, GameProfile target) {
        Lang lang = SponsorConfig.languageOf(source);
        Actor actor = actor(source);
        if (actor == null) {
            return fail(source, Msg.NOT_ALLOWED.component(lang));
        }
        return removeEdge(source, lang, actor, sponsor, target.getId(), target.getName());
    }

    private int removeEdge(CommandSourceStack source, Lang lang, Actor actor, UUID sponsor, UUID target, String targetName) {
        RemoveResult result = service.remove(sponsor, target);
        if (!result.removed()) {
            return fail(source, sponsor.equals(actor.id()) && !actor.isServer()
                    ? Msg.NOT_SPONSORING.component(lang, "player", targetName)
                    : Msg.NOT_SPONSORING_OTHER.component(lang, "sponsor", data.nameOf(sponsor), "player", targetName));
        }

        boolean saved = save();
        List<Affected> lost = result.lostAccess().stream()
                .map(loss -> new Affected(ref(loss.player()), SponsorService.describeLoss(loss, sponsor, target, data::nameOf, SponsorConfig.defaultLanguage())))
                .toList();
        String detail = result.targetStillHasAccess() ? CoreText.DETAIL_KEEPS_ACCESS.format(SponsorConfig.defaultLanguage(), "player", targetName) : null;
        appendLog(new LogEntry(Instant.now(), Action.REMOVE, actor.ref(), ref(sponsor), ref(target), lost, List.of(), detail));
        List<UUID> lostIds = result.lostAccess().stream().map(LostAccess::player).toList();
        synchronize(Action.WHITELIST_ADOPT, Set.copyOf(lostIds));
        saved &= save();

        send(source, Msg.REMOVE_SUCCESS.component(lang, "sponsor", data.nameOf(sponsor), "player", targetName).withStyle(ChatFormatting.GREEN));
        if (result.targetStillHasAccess()) {
            send(source, Msg.REMOVE_STILL_HAS_ACCESS.component(lang, "player", targetName).withStyle(ChatFormatting.GRAY));
        }
        if (!lostIds.isEmpty()) {
            send(source, Msg.REMOVE_CASCADE.component(lang, "count", lostIds.size(), "players", names(lostIds)).withStyle(ChatFormatting.YELLOW));
        }
        if (!saved) {
            source.sendFailure(Msg.SAVE_FAILED.component(lang));
        }
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null && result.targetStillHasAccess()) {
            online.sendSystemMessage(Msg.REMOVED_NOTICE.component(SponsorConfig.languageOf(online), "sponsor", data.nameOf(sponsor)).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    public int list(CommandSourceStack source) {
        Lang lang = SponsorConfig.languageOf(source);
        Actor actor = actor(source);
        if (actor == null) {
            return fail(source, Msg.NOT_ALLOWED.component(lang));
        }
        Set<UUID> targets = data.graph().sponsoredBy(actor.id());
        int max = SponsorConfig.rules().maxSponsorships();
        Component message;
        if (actor.isServer()) {
            message = Msg.LIST_SERVER.component(lang, "count", targets.size(), "players", targets.isEmpty() ? "-" : namesWithAccessMarker(targets, lang));
        } else if (targets.isEmpty()) {
            message = Msg.LIST_EMPTY.component(lang, "count", 0, "max", max);
        } else {
            message = Msg.LIST.component(lang, "count", targets.size(), "max", max, "players", namesWithAccessMarker(targets, lang));
        }
        send(source, message);
        return targets.size();
    }

    public int me(CommandSourceStack source) {
        Lang lang = SponsorConfig.languageOf(source);
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return fail(source, Msg.PLAYER_ONLY.component(lang));
        }
        UUID id = player.getUUID();
        Set<UUID> sponsors = data.graph().sponsorsOf(id);
        send(source, sponsors.isEmpty()
                ? Msg.ME_NO_SPONSORS.component(lang)
                : Msg.ME_SPONSORS.component(lang, "players", namesWithAccessMarker(sponsors, lang)));
        Optional<List<UUID>> path = data.graph().pathFromServer(id);
        send(source, path
                .map(p -> Msg.ME_PATH.component(lang, "path", p.stream().map(data::nameOf).collect(Collectors.joining(" -> "))))
                .orElseGet(() -> Msg.ME_NO_PATH.component(lang)));
        return path.isPresent() ? 1 : 0;
    }

    /** Admin-Übersicht: Kürzeste-Wege-Baum ab SERVER, weitere Sponsoren werden in Klammern genannt. */
    public int tree(CommandSourceStack source) {
        Lang lang = SponsorConfig.languageOf(source);
        SponsorGraph graph = data.graph();
        Map<UUID, UUID> parents = graph.shortestPathTree();
        Set<UUID> withAccess = new HashSet<>(parents.keySet());
        withAccess.add(SponsorGraph.SERVER);
        Map<UUID, List<UUID>> children = new HashMap<>();
        parents.forEach((child, parent) -> children.computeIfAbsent(parent, key -> new ArrayList<>()).add(child));
        children.values().forEach(list -> list.sort(byName));

        send(source, Msg.TREE_HEADER.component(lang, "count", parents.size()).withStyle(ChatFormatting.GOLD));
        send(source, Component.literal(SponsorData.SERVER_NAME).withStyle(ChatFormatting.YELLOW));
        Deque<TreeFrame> stack = new ArrayDeque<>();
        pushChildren(stack, children.get(SponsorGraph.SERVER), 1);
        while (!stack.isEmpty()) {
            TreeFrame frame = stack.pop();
            UUID parent = parents.get(frame.player());
            List<UUID> otherSponsors = graph.sponsorsOf(frame.player()).stream()
                    .filter(sponsor -> !sponsor.equals(parent) && withAccess.contains(sponsor))
                    .sorted(byName)
                    .toList();
            StringBuilder line = new StringBuilder("  ".repeat(frame.depth())).append("- ").append(data.nameOf(frame.player()));
            if (!otherSponsors.isEmpty()) {
                line.append("  ").append(Msg.TREE_ALSO.text(lang, "players", names(otherSponsors)));
            }
            send(source, Component.literal(line.toString()));
            pushChildren(stack, children.get(frame.player()), frame.depth() + 1);
        }

        List<UUID> dormant = graph.players().stream().filter(id -> !withAccess.contains(id)).sorted(byName).toList();
        if (!dormant.isEmpty()) {
            send(source, Msg.TREE_DORMANT.component(lang, "players", names(dormant)).withStyle(ChatFormatting.GRAY));
        }
        return parents.size();
    }

    private static void pushChildren(Deque<TreeFrame> stack, List<UUID> children, int depth) {
        if (children == null) {
            return;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(new TreeFrame(children.get(i), depth));
        }
    }

    public int log(CommandSourceStack source, int count) {
        Lang lang = SponsorConfig.languageOf(source);
        List<LogEntry> entries;
        try {
            entries = auditLog.readLast(count);
        } catch (IOException e) {
            LOGGER.error("[Sponsor] Could not read log {}", auditLog.file(), e);
            return fail(source, Msg.LOG_READ_FAILED.component(lang, "error", String.valueOf(e.getMessage())));
        }
        if (entries.isEmpty()) {
            send(source, Msg.LOG_EMPTY.component(lang).withStyle(ChatFormatting.GRAY));
            return 0;
        }
        send(source, Msg.LOG_HEADER.component(lang, "count", entries.size()).withStyle(ChatFormatting.GOLD));
        ZoneId zone = ZoneId.systemDefault();
        for (LogEntry entry : entries) {
            send(source, Component.literal(entry.toDisplayString(zone, lang)));
        }
        return entries.size();
    }

    // -------------------------------------------------------- suggestions

    /** Namen der Spieler, die der Ausführende sponsert (Konsole: die SERVER sponsert). */
    public List<String> ownTargetNames(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return sortedNames(data.graph().sponsoredBy(player != null ? player.getUUID() : SponsorGraph.SERVER));
    }

    public List<String> targetNamesOf(UUID sponsor) {
        return sortedNames(data.graph().sponsoredBy(sponsor));
    }

    /** Alle Spieler, die mindestens eine Person sponsern. */
    public List<String> sponsorNames() {
        return sortedNames(data.graph().edges().stream().map(SponsorGraph.Edge::sponsor)
                .filter(id -> !SponsorGraph.SERVER.equals(id)).collect(Collectors.toSet()));
    }

    /** Alle Spieler, die von mindestens einer Person gesponsert werden. */
    public List<String> sponsoredNames() {
        return sortedNames(data.graph().edges().stream().map(SponsorGraph.Edge::target).collect(Collectors.toSet()));
    }

    // ------------------------------------------------------------ whitelist

    /**
     * Gleicht die Vanilla-Whitelist mit dem Graphen ab: manuelle Einträge übernehmen, erreichbare Spieler eintragen,
     * nicht erreichbare austragen und – falls online – kicken.
     *
     * @param adoptAction   Protokoll-Aktion für übernommene Einträge ({@link Action#MIGRATION} beim ersten Start)
     * @param alreadyLogged Spieler, deren Zugangsänderung der Aufrufer bereits protokolliert hat
     */
    private ReconcilePlan synchronize(Action adoptAction, Set<UUID> alreadyLogged) {
        Instant now = Instant.now();
        Lang logLang = SponsorConfig.defaultLanguage();
        PlayerList players = server.getPlayerList();
        UserWhiteList whitelist = players.getWhiteList();
        Map<UUID, String> current = new LinkedHashMap<>();
        for (UserWhiteListEntry entry : whitelist.getEntries()) {
            GameProfile profile = entry.getUser();
            if (profile != null) {
                current.put(profile.getId(), profile.getName());
            }
        }

        ReconcilePlan plan = service.reconcile(current, now);
        for (UUID id : plan.adopted()) {
            appendLog(LogEntry.of(now, adoptAction, PlayerRef.server(), ref(id), null));
        }
        for (UUID id : plan.toWhitelist()) {
            whitelist.add(new UserWhiteListEntry(profile(id)));
            if (!alreadyLogged.contains(id)) {
                boolean readded = plan.previousSnapshot().contains(id);
                appendLog(LogEntry.of(now, readded ? Action.WHITELIST_READD : Action.WHITELIST_ADD, PlayerRef.server(), ref(id),
                        (readded ? CoreText.DETAIL_MISSING_FROM_WHITELIST : CoreText.DETAIL_RECONNECTED).format(logLang)));
            }
        }
        for (UUID id : plan.fromWhitelist()) {
            whitelist.remove(profile(id));
            if (!alreadyLogged.contains(id)) {
                appendLog(LogEntry.of(now, Action.WHITELIST_REMOVE, PlayerRef.server(), ref(id), CoreText.DETAIL_NO_CHAIN.format(logLang)));
            }
            disconnect(id);
        }
        if (!plan.adopted().isEmpty() || plan.changesWhitelist()) {
            LOGGER.info("[Sponsor] Whitelist sync: {} taken over, {} added, {} removed",
                    plan.adopted().size(), plan.toWhitelist().size(), plan.fromWhitelist().size());
        }
        return plan;
    }

    /** Kickt einen Spieler, der seinen Zugang verloren hat. OPs umgehen die Vanilla-Whitelist und werden nur informiert. */
    private void disconnect(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            return;
        }
        if (server.getPlayerList().isOp(player.getGameProfile())) {
            player.sendSystemMessage(Msg.OP_NOT_KICKED.component(SponsorConfig.languageOf(player)).withStyle(ChatFormatting.YELLOW));
            return;
        }
        LOGGER.info("[Sponsor] {} lost access and is being kicked.", player.getGameProfile().getName());
        player.connection.disconnect(Msg.KICK.component(SponsorConfig.languageOf(player)));
    }

    // -------------------------------------------------------------- helpers

    private record Actor(UUID id, ServerPlayer player) {
        boolean isServer() {
            return player == null;
        }

        PlayerRef ref() {
            return isServer() ? PlayerRef.server() : new PlayerRef(id, player.getGameProfile().getName());
        }
    }

    private record TreeFrame(UUID player, int depth) {
    }

    /**
     * Spieler handeln als sie selbst, Konsole/RCON (und andere Quellen ab Permission-Level 3 ohne Spieler) als SERVER.
     * Andere Quellen (z. B. Befehlsblöcke) dürfen nicht sponsern.
     */
    private Actor actor(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            data.rememberName(player.getUUID(), player.getGameProfile().getName());
            return new Actor(player.getUUID(), player);
        }
        if (source.hasPermission(Commands.LEVEL_ADMINS)) {
            return new Actor(SponsorGraph.SERVER, null);
        }
        return null;
    }

    private GameProfile profile(UUID id) {
        return new GameProfile(id, data.nameOf(id));
    }

    private PlayerRef ref(UUID id) {
        return PlayerRef.of(id, data);
    }

    private String names(Collection<UUID> ids) {
        return ids.stream().map(data::nameOf).collect(Collectors.joining(", "));
    }

    private List<String> sortedNames(Collection<UUID> ids) {
        return ids.stream().sorted(byName).map(data::nameOf).toList();
    }

    private String namesWithAccessMarker(Collection<UUID> ids, Lang lang) {
        Set<UUID> reachable = data.graph().reachable();
        String marker = Msg.NO_ACCESS_MARKER.text(lang);
        return ids.stream().sorted(byName)
                .map(id -> SponsorGraph.SERVER.equals(id) || reachable.contains(id) ? data.nameOf(id) : data.nameOf(id) + " " + marker)
                .collect(Collectors.joining(", "));
    }

    static String formatPlayTime(long ticks) {
        long minutes = Math.max(0, ticks) / 1200L;
        return (minutes / 60L) + " h " + (minutes % 60L) + " min";
    }

    private boolean save() {
        try {
            storage.save(data);
            return true;
        } catch (IOException e) {
            LOGGER.error("[Sponsor] Could not save sponsor data to {}", storage.file(), e);
            return false;
        }
    }

    private void appendLog(LogEntry entry) {
        try {
            auditLog.append(entry);
        } catch (IOException e) {
            LOGGER.error("[Sponsor] Could not write log entry to {}: {}", auditLog.file(), entry.toJson(), e);
        }
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static int fail(CommandSourceStack source, Component message) {
        source.sendFailure(message);
        return 0;
    }
}
