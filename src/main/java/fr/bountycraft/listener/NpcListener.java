package fr.bountycraft.listener;

import fr.bountycraft.BountyCraft;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NpcListener implements Listener {
    private final BountyCraft plugin;
    private final Map<UUID, Integer> lastOpen = new HashMap<>();

    public NpcListener(BountyCraft plugin) {
        this.plugin = plugin;
    }

    /** Reçoit aussi PlayerInteractAtEntityEvent (sous-classe) : on dédoublonne par tick. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Mannequin m) || !plugin.npcs().isNpc(m)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        int now = Bukkit.getCurrentTick();
        Integer last = lastOpen.get(p.getUniqueId());
        if (last != null && now - last < 5) return;
        lastOpen.put(p.getUniqueId(), now);

        plugin.npcs().open(p, m);
    }

    /**
     * Un autre plugin (protection de zone, anti-lag...) ne doit pas pouvoir annuler le spawn de nos PNJ :
     * sinon l'entité n'est jamais ajoutée au monde et reste invisible. Le tag npc_type est posé avant
     * l'événement (dans le consumer de spawn), donc isNpc() fonctionne ici.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.isCancelled() && plugin.npcs().isNpc(e.getEntity())) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (plugin.npcs().isNpc(e.getEntity())) e.setCancelled(true);
    }
}
