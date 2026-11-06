package fr.bountycraft.gui;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.smith.ForgeManager;
import fr.bountycraft.smith.KitManager;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Boutique du Forgeron (articles lus depuis forge.yml). */
public final class ForgeMenu extends Menu {
    private static final int PER_PAGE = 45;
    private final int page;

    public ForgeMenu(BountyCraft plugin, Player viewer, int page) {
        super(plugin, viewer, 6, Text.mm("<gold><bold>Forgeron <dark_gray>» <gray>Armes & outils"));
        this.page = page;
        build();
    }

    private void build() {
        ForgeManager forge = plugin.forge();
        fill(Items.pane(), 45, 53);

        List<ForgeManager.ForgeItem> list = new ArrayList<>(forge.all());
        List<KitManager.Kit> kits = new ArrayList<>(plugin.kits().all());
        int total = list.size() + kits.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) PER_PAGE));
        int from = page * PER_PAGE;
        int to = Math.min(total, from + PER_PAGE);
        for (int i = from; i < to; i++) {
            int slot = i - from;
            if (i < list.size()) {
                ForgeManager.ForgeItem fi = list.get(i);
                set(slot, forge.display(fi), e -> { forge.buy(viewer, fi); later(() -> new ForgeMenu(plugin, viewer, page).open()); });
            } else {
                KitManager.Kit kit = kits.get(i - list.size());
                set(slot, plugin.kits().shopItem(viewer, kit), e -> { plugin.kits().buy(viewer, kit); later(() -> new ForgeMenu(plugin, viewer, page).open()); });
            }
        }

        set(49, Items.make(Material.GOLD_INGOT, "<gold>Votre solde",
                "<yellow>" + Text.money(plugin.economy().balance(viewer.getUniqueId())),
                "",
                "<gray>Page " + (page + 1) + "/" + pages));
        if (page > 0) {
            set(48, Items.make(Material.ARROW, "<yellow>« Page précédente"),
                    e -> later(() -> new ForgeMenu(plugin, viewer, page - 1).open()));
        }
        if (page + 1 < pages) {
            set(50, Items.make(Material.ARROW, "<yellow>Page suivante »"),
                    e -> later(() -> new ForgeMenu(plugin, viewer, page + 1).open()));
        }
    }
}
