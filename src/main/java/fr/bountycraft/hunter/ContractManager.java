package fr.bountycraft.hunter;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Contrats du PNJ Hunter : un chasseur choisit une cible et touche la prime en la tuant.
 * prime = max(min, base * decay ^ nombreDeMortsParJoueurs)
 * Les contrats sont gardés en mémoire (ils ne survivent pas à un redémarrage).
 */
public final class ContractManager {

    public record Contract(UUID target, String targetName, long expiresAt) {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final BountyCraft plugin;
    private final Map<UUID, Contract> contracts = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();

    public ContractManager(BountyCraft plugin) {
        this.plugin = plugin;
    }

    public Contract get(UUID hunter) {
        return contracts.get(hunter);
    }

    /** Prime actuelle sur un joueur. */
    public long bountyFor(UUID target) {
        double base = plugin.getConfig().getDouble("bounty.base", 2000);
        double decay = plugin.getConfig().getDouble("bounty.decay", 0.85);
        long min = plugin.getConfig().getLong("bounty.min", 100);
        int deaths = plugin.economy().deathsByPlayers(target);
        return Math.max(min, Math.round(base * Math.pow(decay, deaths)));
    }

    /** Millisecondes de protection restantes sur une cible récemment tuée. */
    public long protectionLeft(UUID target) {
        Long until = protectedUntil.get(target);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    /** @return null si OK, sinon le message d'erreur. */
    public String start(Player hunter, Player target) {
        if (!target.isOnline()) return "Cette cible n'est plus en ligne.";
        if (hunter.getUniqueId().equals(target.getUniqueId())) return "Vous ne pouvez pas vous cibler vous-même.";
        if (contracts.containsKey(hunter.getUniqueId())) return "Vous avez déjà un contrat en cours (/contract).";
        long left = protectionLeft(target.getUniqueId());
        if (left > 0) return "Cette cible vient d'être tuée. Réessayez dans " + Text.time(left / 1000) + ".";
        if (target.getGameMode() == GameMode.SPECTATOR) return "Cette cible est injoignable.";

        long duration = plugin.getConfig().getLong("bounty.contract-duration-minutes", 60) * 60_000L;
        contracts.put(hunter.getUniqueId(),
                new Contract(target.getUniqueId(), target.getName(), System.currentTimeMillis() + duration));

        Text.send(hunter, "<green>Contrat accepté ! <gray>Cible : <white>" + target.getName()
                + " <gray>| Prime : <gold>" + Text.money(bountyFor(target.getUniqueId()))
                + " <gray>| Durée : <aqua>" + Text.time(duration / 1000));
        if (plugin.getConfig().getBoolean("bounty.notify-target", true)) {
            Text.send(target, "<red>Un chasseur de primes a pris un contrat sur votre tête !");
        }
        return null;
    }

    public void cancel(UUID hunter) {
        contracts.remove(hunter);
    }

    /**
     * À appeler AVANT d'incrémenter le compteur de morts de la victime
     * (la prime dépend du nombre de morts précédentes).
     */
    public void onKill(Player killer, Player victim) {
        Contract c = contracts.get(killer.getUniqueId());
        if (c != null && c.target().equals(victim.getUniqueId())) {
            long bounty = bountyFor(victim.getUniqueId());
            plugin.economy().deposit(killer.getUniqueId(), bounty);
            contracts.remove(killer.getUniqueId());
            long prot = plugin.getConfig().getLong("bounty.target-protection-minutes", 10) * 60_000L;
            protectedUntil.put(victim.getUniqueId(), System.currentTimeMillis() + prot);
            Text.send(killer, "<green>Contrat rempli ! <gray>Vous touchez <gold>" + Text.money(bounty)
                    + "<gray> pour la tête de <white>" + victim.getName() + "<gray>.");
        }
        // les autres chasseurs de cette cible perdent leur contrat
        Iterator<Map.Entry<UUID, Contract>> it = contracts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Contract> e = it.next();
            if (e.getValue().target().equals(victim.getUniqueId())) {
                Player other = Bukkit.getPlayer(e.getKey());
                if (other != null) {
                    Text.send(other, "<yellow>Votre cible a été éliminée par <white>" + killer.getName()
                            + "<yellow>. Contrat terminé.");
                }
                it.remove();
            }
        }
    }

    /** Appelé chaque seconde : expire les contrats. */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Contract>> it = contracts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Contract> e = it.next();
            if (e.getValue().expiresAt() <= now) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) Text.send(p, "<red>Votre contrat sur <white>" + e.getValue().targetName() + "<red> a expiré.");
                it.remove();
            }
        }
        protectedUntil.values().removeIf(t -> t <= now);
    }

    /** Texte d'action bar (MiniMessage) pour un chasseur, ou null. */
    public String hud(Player hunter) {
        Contract c = contracts.get(hunter.getUniqueId());
        if (c == null) return null;
        long left = (c.expiresAt() - System.currentTimeMillis()) / 1000;
        String base = "<red>Cible : <white>" + c.targetName() + " <dark_gray>| <gold>"
                + Text.money(bountyFor(c.target())) + " <dark_gray>| <aqua>" + Text.time(left);

        Player t = Bukkit.getPlayer(c.target());
        if (t == null || !plugin.getConfig().getBoolean("bounty.show-distance", true)) {
            return t == null ? base + " <gray>(hors-ligne)" : base;
        }
        Location a = hunter.getLocation();
        Location b = t.getLocation();
        if (!a.getWorld().equals(b.getWorld())) {
            return base + " <dark_gray>| <yellow>" + dimension(b.getWorld());
        }
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = targetYaw - a.getYaw();
        while (rel > 180) rel -= 360;
        while (rel < -180) rel += 360;
        int idx = (int) Math.round(rel / 45.0);
        if (idx < 0) idx += 8;
        idx %= 8;
        int dist = (int) Math.round(a.distance(b));
        return base + " <dark_gray>| <green>" + ARROWS[idx] + " " + dist + " m";
    }

    public static String dimension(World w) {
        return switch (w.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> w.getName();
        };
    }
}
