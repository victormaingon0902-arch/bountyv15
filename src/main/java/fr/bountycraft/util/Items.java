package fr.bountycraft.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Items {
    private Items() {}

    public static ItemStack make(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) m.lore(Text.item(lore));
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack make(Material mat, String name, String... lore) {
        return make(mat, name, Arrays.asList(lore));
    }

    public static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = make(Material.PLAYER_HEAD, name, lore);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(owner);
        it.setItemMeta(m);
        return it;
    }

    public static String getString(ItemStack it, NamespacedKey key) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static Long getLong(ItemStack it, NamespacedKey key) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.LONG);
    }

    /** Donne l'objet ; s'il n'y a plus de place il tombe aux pieds du joueur. */
    public static void give(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }
}
