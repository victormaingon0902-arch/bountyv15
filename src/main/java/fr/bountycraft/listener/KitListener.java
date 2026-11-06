package fr.bountycraft.listener;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Player;

public final class KitListener implements Listener {
    private final BountyCraft plugin;
    public KitListener(BountyCraft plugin) { this.plugin = plugin; }
    @EventHandler(ignoreCancelled = true) public void onPlace(BlockPlaceEvent e) {
        String id = plugin.kits().boxId(e.getItemInHand());
        if (id != null) plugin.kits().registerBox(e.getBlockPlaced(), id);
    }
    /** Traité même si une protection de spawn a déjà annulé le clic : la boîte et la clé gèrent elles-mêmes leurs droits. */
    @EventHandler(priority = EventPriority.HIGH) public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) || e.getClickedBlock() == null) return;
        String box = plugin.kits().boxAt(e.getClickedBlock().getLocation()); if (box == null) return;
        String key = plugin.kits().keyId(e.getItem());
        if (key == null) { e.setCancelled(true); return; }
        e.setCancelled(true);
        if (!box.equals(key)) { Text.send(e.getPlayer(), "<red>Cette clé ne correspond pas à cette boîte."); return; }
        var kit = plugin.kits().get(box);
        if (kit == null) { Text.send(e.getPlayer(), "<red>Ce kit n'existe plus."); return; }
        if (!plugin.kits().canUse(e.getPlayer(), kit)) { plugin.kits().deny(e.getPlayer(), kit); return; } // la clé n'est pas consommée
        if (kit.contents().length == 0) { // kit vide : on ne consomme pas la clé
            Text.send(e.getPlayer(), "<red>Ce kit est vide, prévenez un administrateur."
                    + (e.getPlayer().hasPermission("bountycraft.admin") ? " <gray>(/kit update " + kit.id() + " avec une shulker remplie en main)" : ""));
            return;
        }
        var held = e.getPlayer().getInventory().getItemInMainHand();
        if (held.getAmount() > 1) { held.setAmount(held.getAmount() - 1); e.getPlayer().getInventory().setItemInMainHand(held); }
        else e.getPlayer().getInventory().setItemInMainHand(null);
        plugin.kits().open(e.getPlayer(), box);
    }
    /** Les textes flottants ne sont pas sauvegardés : on les recrée quand les entités d'un chunk se chargent. */
    @EventHandler public void onEntitiesLoad(EntitiesLoadEvent e) {
        var chunk = e.getChunk();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.kits().labels().refreshChunk(chunk)); // pas d'apparition d'entité pendant le chargement
    }
    @EventHandler(ignoreCancelled = true) public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof org.bukkit.block.ShulkerBox box && plugin.kits().boxAt(box.getLocation()) != null) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onBreak(BlockBreakEvent e) {
        if (plugin.kits().boxAt(e.getBlock().getLocation()) != null) {
            if (!e.getPlayer().hasPermission("bountycraft.admin")) { e.setCancelled(true); Text.send(e.getPlayer(), "<red>Seul un administrateur peut déplacer cette boîte."); }
            else {
                String id = plugin.kits().boxAt(e.getBlock().getLocation());
                var kit = plugin.kits().get(id);
                e.setCancelled(true);
                e.getBlock().setType(Material.AIR);
                plugin.kits().removeBox(e.getBlock().getLocation());
                if (kit != null) e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), plugin.kits().boxItem(id, kit.name()));
            }
        }
    }
}
