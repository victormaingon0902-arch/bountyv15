package fr.bountycraft.market;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Marchand : rachète des cultures et objets au prix défini dans sell.yml.
 * Seuls les objets "normaux" sont rachetés (jamais un objet renommé/enchanté/personnalisé).
 */
public final class MarketManager {

    public record Sale(int items, long money) {}

    private final BountyCraft plugin;
    private final Map<Material, Long> prices = new LinkedHashMap<>();

    public MarketManager(BountyCraft plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        prices.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "sell.yml"));
        ConfigurationSection s = y.getConfigurationSection("prices");
        // Liste blanche stricte : même un ancien sell.yml ne peut plus faire racheter d'autres objets.
        Map<Material, Long> allowed = new LinkedHashMap<>();
        allowed.put(Material.COD, 2L);
        allowed.put(Material.SALMON, 3L);
        allowed.put(Material.TROPICAL_FISH, 3L);
        allowed.put(Material.PUFFERFISH, 3L);
        allowed.put(Material.MELON_SLICE, 1L);
        allowed.put(Material.POTATO, 1L);
        allowed.put(Material.CARROT, 1L);
        allowed.put(Material.BEETROOT, 1L);
        allowed.put(Material.ROTTEN_FLESH, 1L);
        for (Map.Entry<Material, Long> entry : allowed.entrySet()) {
            long configured = s == null ? entry.getValue() : s.getLong(entry.getKey().name(), entry.getValue());
            prices.put(entry.getKey(), Math.max(1L, Math.min(entry.getValue(), configured)));
        }
        plugin.getLogger().info(prices.size() + " article(s) rachetés par le Marchand (sell.yml)");
    }

    public Map<Material, Long> prices() {
        return prices;
    }

    public long price(Material m) {
        return prices.getOrDefault(m, 0L);
    }

    private boolean sellable(ItemStack it, Material m) {
        return it != null && it.getType() == m && !it.hasItemMeta();
    }

    /** Nombre d'objets vendables de ce type dans l'inventaire. */
    public int count(Player p, Material m) {
        int total = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (sellable(it, m)) total += it.getAmount();
        }
        return total;
    }

    /** Valeur totale de tout ce qui est vendable dans l'inventaire. */
    public long totalValue(Player p) {
        long total = 0;
        for (Map.Entry<Material, Long> e : prices.entrySet()) {
            total += count(p, e.getKey()) * e.getValue();
        }
        return total;
    }

    /** Vend jusqu'à {@code max} objets de ce type. */
    public Sale sell(Player p, Material m, int max) {
        long price = price(m);
        if (price <= 0 || max <= 0) return new Sale(0, 0);
        PlayerInventory inv = p.getInventory();
        int sold = 0;
        for (int i = 0; i < 36 && sold < max; i++) {
            ItemStack it = inv.getItem(i);
            if (!sellable(it, m)) continue;
            int n = Math.min(it.getAmount(), max - sold);
            if (it.getAmount() - n <= 0) {
                inv.setItem(i, null);
            } else {
                ItemStack rest = it.clone();
                rest.setAmount(it.getAmount() - n);
                inv.setItem(i, rest);
            }
            sold += n;
        }
        long money = sold * price;
        if (sold > 0) plugin.economy().deposit(p.getUniqueId(), money);
        return new Sale(sold, money);
    }

    public Sale sellAll(Player p) {
        int items = 0;
        long money = 0;
        for (Material m : prices.keySet()) {
            Sale s = sell(p, m, Integer.MAX_VALUE);
            items += s.items();
            money += s.money();
        }
        return new Sale(items, money);
    }

    public void report(Player p, Sale s) {
        if (s.items() <= 0) {
            Text.send(p, "<red>Vous n'avez rien à vendre.");
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
        Text.send(p, "<green>Vendu <white>" + s.items() + "<green> objet(s) pour <gold>" + Text.money(s.money()) + "<green>.");
    }
}
