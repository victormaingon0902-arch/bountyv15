package fr.bountycraft.listener;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.smith.ForgeManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Capacités des objets du Forgeron : mine 3x3x3, abattage d'arbre, feu, vol de vie. */
public final class ToolListener implements Listener {
    private static final BlockFace[] SIDES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final BountyCraft plugin;
    /** Évite la récursion : Player#breakBlock redéclenche BlockBreakEvent. */
    private final Set<UUID> busy = new HashSet<>();

    public ToolListener(BountyCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId())) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        ForgeManager forge = plugin.forge();
        ForgeManager.ForgeItem fi = forge.fromItem(tool);
        if (fi == null) return;
        if (p.isSneaking() && forge.sneakDisables()) return;
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.ADVENTURE) return;

        busy.add(p.getUniqueId());
        try {
            if (fi.areaRadius() > 0) mineCube(p, e.getBlock(), tool, fi);
            if (fi.treeFeller() && Tag.LOGS.isTagged(e.getBlock().getType())) fellTree(p, e.getBlock());
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ mine en cube

    private void mineCube(Player p, Block center, ItemStack tool, ForgeManager.ForgeItem fi) {
        int r = fi.areaRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block b = center.getRelative(dx, dy, dz);
                    if (canMine(b, tool, fi.areaOnlyPreferred())) p.breakBlock(b);
                }
            }
        }
    }

    private boolean canMine(Block b, ItemStack tool, boolean onlyPreferred) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid()) return false;
        if (m.getHardness() < 0) return false;                  // bedrock, barrières...
        if (onlyPreferred && !b.isPreferredTool(tool)) return false;
        // on ne casse pas les blocs à inventaire / spawners (évite les vols et pertes d'objets)
        return !(b.getState(false) instanceof org.bukkit.block.Container)
                && !(b.getState(false) instanceof org.bukkit.block.CreatureSpawner);
    }

    // ------------------------------------------------------------------ abattage d'arbre

    private void fellTree(Player p, Block origin) {
        ForgeManager forge = plugin.forge();
        Set<Block> logs = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        logs.add(origin);
        queue.add(origin);

        while (!queue.isEmpty() && logs.size() < forge.maxTreeBlocks()) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = cur.getRelative(dx, dy, dz);
                        if (logs.size() >= forge.maxTreeBlocks()) break;
                        if (Tag.LOGS.isTagged(n.getType()) && logs.add(n)) queue.add(n);
                    }
                }
            }
        }

        if (forge.requireLeaves() && !touchesNaturalLeaves(logs)) return;

        for (Block b : logs) {
            if (!b.equals(origin)) p.breakBlock(b);
        }
    }

    private boolean touchesNaturalLeaves(Set<Block> logs) {
        for (Block b : logs) {
            for (BlockFace f : SIDES) {
                Block n = b.getRelative(f);
                if (Tag.LEAVES.isTagged(n.getType())
                        && n.getBlockData() instanceof Leaves leaves && !leaves.isPersistent()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ armes

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ForgeManager.ForgeItem fi = plugin.forge().fromItem(p.getInventory().getItemInMainHand());
        if (fi == null) return;

        if (fi.igniteSeconds() > 0 && e.getEntity() instanceof LivingEntity le && !plugin.npcs().isNpc(le)) {
            le.setFireTicks(fi.igniteSeconds() * 20);
        }
        if (fi.lifesteal() > 0) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = max != null ? max.getValue() : 20.0;
            p.setHealth(Math.min(maxHealth, p.getHealth() + e.getFinalDamage() * fi.lifesteal()));
        }
    }
}
