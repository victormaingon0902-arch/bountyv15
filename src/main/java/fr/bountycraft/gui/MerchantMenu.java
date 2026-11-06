package fr.bountycraft.gui;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.market.MarketManager;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Menu du Marchand : vente de cultures et d'objets. */
public final class MerchantMenu extends Menu {
    private static final int PER_PAGE = 45;
    private final int page;

    public MerchantMenu(BountyCraft plugin, Player viewer, int page) {
        super(plugin, viewer, 6, Text.mm("<green><bold>Marchand <dark_gray>» <gray>Vendre"));
        this.page = page;
        build();
    }

    private void refresh() {
        later(() -> new MerchantMenu(plugin, viewer, page).open());
    }

    private void build() {
        MarketManager market = plugin.market();
        fill(Items.pane(), 45, 53);

        List<Map.Entry<Material, Long>> list = new ArrayList<>(market.prices().entrySet());
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
        int from = page * PER_PAGE;
        int to = Math.min(list.size(), from + PER_PAGE);

        for (int i = from; i < to; i++) {
            Material m = list.get(i).getKey();
            long price = list.get(i).getValue();
            int have = market.count(viewer, m);

            ItemStack icon = new ItemStack(m);
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Prix : <gold>" + Text.money(price) + "<gray> l'unité");
            lore.add("<gray>Vous en avez : " + (have > 0 ? "<white>" : "<red>") + have);
            lore.add("");
            lore.add("<green>Clic gauche : <white>vendre 1");
            lore.add("<green>Clic droit : <white>vendre 16");
            lore.add("<green>Shift + clic : <white>tout vendre");
            meta.lore(Text.item(lore));
            icon.setItemMeta(meta);

            set(i - from, icon, e -> {
                ClickType click = e.getClick();
                int amount = click.isShiftClick() ? Integer.MAX_VALUE : (click.isRightClick() ? 16 : 1);
                market.report(viewer, market.sell(viewer, m, amount));
                refresh();
            });
        }

        long total = market.totalValue(viewer);
        set(45, Items.make(Material.GOLD_INGOT, "<gold>Votre solde",
                "<yellow>" + Text.money(plugin.economy().balance(viewer.getUniqueId()))));
        set(53, Items.make(Material.HOPPER, "<green><bold>Tout vendre",
                "<gray>Vend tout ce que le Marchand rachète", "<gray>dans votre inventaire.", "",
                "<gray>Valeur actuelle : <gold>" + Text.money(total), "", "<green>Clic pour vendre"), e -> {
            market.report(viewer, market.sellAll(viewer));
            refresh();
        });

        set(49, Items.make(Material.PAPER, "<white>Page " + (page + 1) + "/" + pages));
        if (page > 0) {
            set(48, Items.make(Material.ARROW, "<yellow>« Page précédente"),
                    e -> later(() -> new MerchantMenu(plugin, viewer, page - 1).open()));
        }
        if (page + 1 < pages) {
            set(50, Items.make(Material.ARROW, "<yellow>Page suivante »"),
                    e -> later(() -> new MerchantMenu(plugin, viewer, page + 1).open()));
        }
    }
}
