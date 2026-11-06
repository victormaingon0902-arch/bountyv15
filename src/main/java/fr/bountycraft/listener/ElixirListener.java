package fr.bountycraft.listener;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.elixir.ElixirType;
import fr.bountycraft.util.Fx;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Keys;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ListIterator;

/** Effets passifs et compétences des élixirs. */
public final class ElixirListener implements Listener {
    private final BountyCraft plugin;

    public ElixirListener(BountyCraft plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ boire / utiliser

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ElixirType t = plugin.elixirs().typeOf(e.getItem());
        if (t != null) plugin.elixirs().drink(e.getPlayer(), t);
    }

    /** Touche F (échange de main) = compétence ; Shift+F = compétence secondaire. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        ElixirType t = plugin.elixirs().type(e.getPlayer());
        if (t == null || !t.hasSkill()) return;
        e.setCancelled(true);
        plugin.elixirs().cast(e.getPlayer(), e.getPlayer().isSneaking());
    }

    // ------------------------------------------------------------------ passifs

    /** Potion du vent : zéro dégât de chute. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player p
                && plugin.elixirs().has(p, ElixirType.WIND)) {
            e.setCancelled(true);
        }
    }

    /** Potion d'invisibilité : les monstres ne ciblent jamais le porteur. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && plugin.elixirs().has(p, ElixirType.INVISIBILITY)) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Mob mob) mob.setTarget(null);
        }
    }

    /** Filet de sécurité : aucun dégât de monstre sur le porteur de la potion d'invisibilité. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !plugin.elixirs().has(victim, ElixirType.INVISIBILITY)) return;
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr) {
            ProjectileSource src = pr.getShooter();
            if (src instanceof Entity shooter) damager = shooter;
        }
        if (damager instanceof Mob) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ potion de feu

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p
                && plugin.elixirs().isSkillActive(p, ElixirType.FIRE)
                && e.getEntity() instanceof LivingEntity le
                && !(le instanceof ArmorStand)
                && !plugin.npcs().isNpc(le)) {
            le.setFireTicks(plugin.elixirs().igniteTicks());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (!plugin.elixirs().isSkillActive(p, ElixirType.FIRE)) return;
        boolean cooked = false;
        for (Item item : e.getItems()) {
            ItemStack out = plugin.elixirs().smeltResult(item.getItemStack());
            if (out != null) {
                item.setItemStack(out);
                cooked = true;
            }
        }
        if (cooked) {
            Fx.particle(e.getBlock().getWorld(), Particle.FLAME, e.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    8, 0.25, 0.25, 0.25, 0.01);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null || !plugin.elixirs().isSkillActive(killer, ElixirType.FIRE)) return;
        ListIterator<ItemStack> it = e.getDrops().listIterator();
        while (it.hasNext()) {
            ItemStack out = plugin.elixirs().smeltResult(it.next());
            if (out != null) it.set(out);
        }
    }

    // ------------------------------------------------------------------ souffle du dragon

    @EventHandler
    public void onBreathApply(AreaEffectCloudApplyEvent e) {
        AreaEffectCloud cloud = e.getEntity();
        if (!cloud.getPersistentDataContainer().has(Keys.BREATH, PersistentDataType.BYTE)) return;
        if (cloud.getSource() instanceof Player owner) e.getAffectedEntities().remove(owner);
        e.getAffectedEntities().removeIf(le -> plugin.npcs().isNpc(le));
    }

    // ------------------------------------------------------------------ trident temporaire

    private boolean isTempTrident(ItemStack it) {
        return it != null && Items.getLong(it, Keys.TEMP_UNTIL) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isTempTrident(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    /** Empêche de ranger le trident temporaire dans un coffre. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;
        if (isTempTrident(e.getCurrentItem()) || isTempTrident(e.getCursor())) e.setCancelled(true);
    }
}
