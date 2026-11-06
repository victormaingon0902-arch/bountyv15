package fr.bountycraft.smith;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Keys;
import fr.bountycraft.util.Text;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boutique du Forgeron entièrement pilotée par forge.yml :
 * pour ajouter un outil ou une arme il suffit d'ajouter une entrée dans ce fichier.
 */
public final class ForgeManager {

    public record ForgeItem(String id, long price, ItemStack template,
                            int areaRadius, boolean areaOnlyPreferred,
                            boolean treeFeller, int igniteSeconds, double lifesteal) {}

    private final BountyCraft plugin;
    private final Map<String, ForgeItem> items = new LinkedHashMap<>();
    private int maxTreeBlocks = 500;
    private boolean requireLeaves = true;
    private boolean sneakDisables = true;

    public ForgeManager(BountyCraft plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        items.clear();
        File f = new File(plugin.getDataFolder(), "forge.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        maxTreeBlocks = y.getInt("settings.max-tree-blocks", 500);
        requireLeaves = y.getBoolean("settings.require-leaves", true);
        sneakDisables = y.getBoolean("settings.sneak-disables-abilities", true);

        ConfigurationSection root = y.getConfigurationSection("items");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                ForgeItem fi = parse(id, s);
                if (fi != null) items.put(id, fi);
            } catch (Exception ex) {
                plugin.getLogger().warning("forge.yml : article '" + id + "' ignoré (" + ex.getMessage() + ")");
            }
        }
        plugin.getLogger().info(items.size() + " article(s) chargé(s) depuis forge.yml");
    }

    private ForgeItem parse(String id, ConfigurationSection s) {
        Material mat = Material.matchMaterial(s.getString("material", ""));
        if (mat == null || mat.isAir()) {
            plugin.getLogger().warning("forge.yml : matériau invalide pour '" + id + "'");
            return null;
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.item(s.getString("name", id)));
        List<String> lore = s.getStringList("lore");
        if (!lore.isEmpty()) m.lore(Text.item(lore));
        if (s.getBoolean("unbreakable", false)) m.setUnbreakable(true);

        String model = s.getString("item-model", "");
        if (model != null && !model.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(model.toLowerCase(Locale.ROOT));
            if (key != null) m.setItemModel(key);
        }

        ConfigurationSection ench = s.getConfigurationSection("enchantments");
        if (ench != null) {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            for (String name : ench.getKeys(false)) {
                Enchantment e = registry.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                if (e == null) {
                    plugin.getLogger().warning("forge.yml : enchantement inconnu '" + name + "' (" + id + ")");
                    continue;
                }
                m.addEnchant(e, ench.getInt(name), true);
            }
        }
        m.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, id);
        it.setItemMeta(m);

        int radius = 0;
        boolean preferred = true;
        boolean tree = false;
        int ignite = 0;
        double lifesteal = 0;
        ConfigurationSection ab = s.getConfigurationSection("abilities");
        if (ab != null) {
            ConfigurationSection am = ab.getConfigurationSection("area-mine");
            if (am != null) {
                radius = Math.max(0, am.getInt("radius", 1));
                preferred = am.getBoolean("only-preferred-tool", true);
            }
            tree = ab.isConfigurationSection("tree-feller") || ab.getBoolean("tree-feller", false);
            ignite = ab.getInt("ignite.seconds", 0);
            lifesteal = ab.getDouble("lifesteal.percent", 0) / 100.0;
        }
        return new ForgeItem(id, s.getLong("price", 1000), it, radius, preferred, tree, ignite, lifesteal);
    }

    // ------------------------------------------------------------------ accès

    public Collection<ForgeItem> all() { return items.values(); }
    public ForgeItem get(String id) { return items.get(id); }
    public int maxTreeBlocks() { return maxTreeBlocks; }
    public boolean requireLeaves() { return requireLeaves; }
    public boolean sneakDisables() { return sneakDisables; }

    public ForgeItem fromItem(ItemStack it) {
        String id = Items.getString(it, Keys.ITEM_ID);
        return id == null ? null : items.get(id);
    }

    public ItemStack create(ForgeItem fi) {
        return fi.template().clone();
    }

    /** Version affichée dans la boutique (avec prix). */
    public ItemStack display(ForgeItem fi) {
        ItemStack it = fi.template().clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        lore.add(Component.empty());
        lore.add(Text.item("<gold>Prix : <yellow>" + Text.money(fi.price())));
        lore.add(Text.item("<green>Clic pour acheter"));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    public void buy(Player p, ForgeItem fi) {
        if (!plugin.economy().withdraw(p.getUniqueId(), fi.price())) {
            Text.send(p, "<red>Fonds insuffisants. <gray>Il vous faut <gold>" + Text.money(fi.price())
                    + "<gray> (solde : " + Text.money(plugin.economy().balance(p.getUniqueId())) + ").");
            return;
        }
        Items.give(p, create(fi));
        Text.send(p, "<green>Achat effectué : <white>" + Text.plain(fi.template().getItemMeta().displayName())
                + " <gray>pour <gold>" + Text.money(fi.price()) + "<gray>.");
    }
}
