package fr.bountycraft.listener;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Connexions, morts (primes / chasses) et resource pack. */
public final class CoreListener implements Listener {
    private final BountyCraft plugin;

    public CoreListener(BountyCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.economy().of(p);
        plugin.elixirs().load(p);
        plugin.hunts().onJoin(p);

        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url != null && !url.isBlank()) {
            String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
            boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
            String prompt = plugin.getConfig().getString("resource-pack.prompt", "");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.setResourcePack(url, sha1, required, Text.mm(prompt));
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.elixirs().unload(e.getPlayer());
    }

    /** Quêtes KILL : chaque créature (non joueur) tuée fait progresser la quête du tueur. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer != null) plugin.quests().onKill(killer, e.getEntity().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        plugin.elixirs().onDeath(victim, e);

        // Le mode ban sanctionne toute mort de la cible, même sans joueur tueur.
        plugin.hunts().onKill(killer, victim);
        if (killer == null || killer.equals(victim)) return;

        // 2) contrat Hunter (la prime dépend des morts AVANT celle-ci)
        plugin.contracts().onKill(killer, victim);

        // 3) statistiques
        plugin.economy().of(killer);
        plugin.economy().addKill(killer.getUniqueId());
        plugin.economy().of(victim);
        plugin.economy().addDeathByPlayer(victim.getUniqueId());
    }
}
