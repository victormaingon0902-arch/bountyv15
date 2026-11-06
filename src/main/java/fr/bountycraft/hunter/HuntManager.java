package fr.bountycraft.hunter;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import io.papermc.paper.ban.BanListType;
import java.util.Date;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Évènement /hunt : pendant X minutes tous les joueurs doivent traquer la cible désignée.
 * Une boss bar affiche le temps restant, la prime et les coordonnées de la cible.
 */
public final class HuntManager {
    private final BountyCraft plugin;

    private UUID target;
    private String targetName;
    private long reward;
    private long startMs;
    private long endMs;
    private BossBar hunterBar;
    private BossBar targetBar;
    private Location lastKnown;
    private boolean banHunt;
    private long banDurationSeconds;

    public HuntManager(BountyCraft plugin) {
        this.plugin = plugin;
    }

    public boolean active() {
        return target != null;
    }

    public boolean isTarget(UUID id) {
        return target != null && target.equals(id);
    }

    public long reward() { return reward; }
    public String targetName() { return targetName; }
    public long secondsLeft() { return Math.max(0, (endMs - System.currentTimeMillis()) / 1000); }

    /** @return null si OK, sinon un message d'erreur. */
    public String start(CommandSender admin, Player tgt, boolean banMode, long durationSeconds, long banSeconds) {
        if (active()) return "Un hunt est déjà en cours (/hunt stop pour l'arrêter).";
        banHunt = banMode;
        banDurationSeconds = banSeconds;
        int minutes = (int) Math.max(1, Math.ceil(durationSeconds / 60.0));
        long min = plugin.getConfig().getLong("hunt-event.min-reward", 5000);
        double mult = plugin.getConfig().getDouble("hunt-event.reward-multiplier", 5);
        reward = Math.max(min, Math.round(plugin.contracts().bountyFor(tgt.getUniqueId()) * mult));
        target = tgt.getUniqueId(); targetName = tgt.getName(); lastKnown = tgt.getLocation();
        startMs = System.currentTimeMillis(); endMs = startMs + durationSeconds * 1000L;
        hunterBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        targetBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        update();
        for (Player p : Bukkit.getOnlinePlayers()) {
            show(p);
            p.showTitle(Title.title(Text.mm("<red><bold>HUNT " + (banMode ? "BAN" : "EVENT")), Text.mm("<red>" + targetName + " <dark_gray>• <white>" + Text.time(durationSeconds))));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
        }
        Text.broadcast("<red><bold>HUNT " + (banMode ? "BAN" : "EVENT") + " ! <white>La cible est <red>" + targetName + "<white>. Temps : <red>" + Text.time(durationSeconds) + (banMode ? "<dark_red> — sa mort entraînera un bannissement." : ""));
        return null;
    }

    /** Compatibilité avec les anciens appels internes. */
    public String start(CommandSender admin, Player tgt, Long rewardOverride) {
        return start(admin, tgt, false, plugin.getConfig().getLong("hunt-event.duration-minutes", 30) * 60L, -1);
    }

    /** Arrête la chasse. */
    public void stop(String reasonMiniMessage) {
        if (!active()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(hunterBar);
            p.hideBossBar(targetBar);
        }
        target = null;
        targetName = null;
        hunterBar = null;
        targetBar = null;
        lastKnown = null;
        if (reasonMiniMessage != null) Text.broadcast(reasonMiniMessage);
    }

    /** Arrêt silencieux (extinction du plugin). */
    public void shutdown() {
        if (active()) stop(null);
    }

    public void show(Player p) {
        if (!active()) return;
        p.showBossBar(p.getUniqueId().equals(target) ? targetBar : hunterBar);
    }

    public void onJoin(Player p) {
        show(p);
    }

    /** Appelé quand un joueur en tue un autre. */
    public void onKill(Player killer, Player victim) {
        if (!active() || !victim.getUniqueId().equals(target)) return;
        if (!banHunt && killer == null) return;
        String victimName = targetName;
        boolean shouldBan = banHunt;
        long duration = banDurationSeconds;
        long won = reward;
        if (shouldBan) {
            Date expires = duration < 0 ? null : new Date(System.currentTimeMillis() + duration * 1000L);
            Bukkit.getBanList(BanListType.PROFILE).addBan(victim.getPlayerProfile(), "Mort pendant un hunt BAN", expires, "BountyCraft");
            Bukkit.getScheduler().runTask(plugin, () -> victim.kick(Component.text("Banni : mort pendant un hunt BAN")));
            stop("<red><bold>HUNT BAN TERMINÉ ! <white>" + victimName + "<red> a été banni après sa mort.");
        } else {
            plugin.economy().deposit(killer.getUniqueId(), won);
            stop("<red><bold>HUNT TERMINÉ ! <white>" + killer.getName() + "<gray> a éliminé " + victimName + " et remporte <gold>" + Text.money(won) + "<gray> !");
            Text.send(killer, "<green>Vous recevez <gold>" + Text.money(won) + "<green> !");
        }
    }

    /** Appelé chaque seconde. */
    public void tick() {
        if (!active()) return;
        if (System.currentTimeMillis() >= endMs) {
            stop("<aqua><bold>CHASSE TERMINÉE ! <yellow>" + targetName
                    + "<gray> a survécu. Personne ne remporte la prime.");
            return;
        }
        update();
    }

    private void update() {
        long now = System.currentTimeMillis();
        long left = Math.max(0, (endMs - now) / 1000);
        float progress = (float) Math.max(0.0, Math.min(1.0, (endMs - now) / (double) (endMs - startMs)));

        Player t = Bukkit.getPlayer(target);
        if (t != null) lastKnown = t.getLocation();

        String coords = "?";
        if (lastKnown != null) {
            coords = "X " + lastKnown.getBlockX() + "  Y " + lastKnown.getBlockY() + "  Z " + lastKnown.getBlockZ()
                    + " (" + ContractManager.dimension(lastKnown.getWorld()) + ")";
        }
        String offline = t == null ? " <gray>[hors-ligne]" : "";

        hunterBar.name(Text.mm("<red><bold>HUNT <white>» <red>" + targetName + offline
                + " <dark_gray>| <red>" + Text.time(left)
                + " <dark_gray>| <red>" + Text.money(reward)
                + " <dark_gray>| <red>" + coords));
        hunterBar.progress(progress);

        targetBar.name(Text.mm("<red><bold>VOUS ÊTES LA CIBLE ! <white>» <red>" + Text.time(left)
                + " <dark_gray>| <red>" + Text.money(reward)
                + " <dark_gray>| <red>" + coords));
        targetBar.progress(progress);
    }
}
