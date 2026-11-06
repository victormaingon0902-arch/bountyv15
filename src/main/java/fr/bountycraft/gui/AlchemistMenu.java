package fr.bountycraft.gui;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.elixir.ElixirManager;
import fr.bountycraft.elixir.ElixirType;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Boutique de l'Alchimiste : les 8 élixirs. */
public final class AlchemistMenu extends Menu {
    private static final int[] SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};

    public AlchemistMenu(BountyCraft plugin, Player viewer) {
        super(plugin, viewer, 5, Text.mm("<light_purple><bold>Alchimiste <dark_gray>» <gray>Élixirs spéciaux"));
        build();
    }

    private void build() {
        ElixirManager em = plugin.elixirs();
        fill(Items.pane(), 0, 44);

        ElixirType[] types = ElixirType.values();
        for (int i = 0; i < types.length && i < SLOTS.length; i++) {
            ElixirType t = types[i];
            ItemStack shown = em.create(t);
            ItemMeta m = shown.getItemMeta();
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(Component.empty());
            lore.add(Text.item("<gold>Prix : <yellow>" + Text.money(em.price(t))));
            lore.add(Text.item("<gray>Achats restants serveur : <white>" + em.purchasesLeft(t)));
            lore.add(Text.item(em.purchasesLeft(t) > 0 ? "<green>Clic pour acheter" : "<red>Stock serveur épuisé"));
            m.lore(lore);
            shown.setItemMeta(m);

            set(SLOTS[i], shown, e -> {
                if (em.purchasesLeft(t) <= 0) { Text.send(viewer, "<red>La limite globale de cette potion est atteinte."); return; }
                if (!plugin.economy().withdraw(viewer.getUniqueId(), em.price(t))) {
                    Text.send(viewer, "<red>Fonds insuffisants. <gray>Il vous faut <gold>" + Text.money(em.price(t)) + "<gray>.");
                    return;
                }
                if (!em.registerPurchase(t)) { plugin.economy().deposit(viewer.getUniqueId(), em.price(t)); Text.send(viewer, "<red>Achat impossible : stock épuisé."); return; }
                Items.give(viewer, em.create(t));
                Text.send(viewer, "<green>Vous achetez la <white>" + t.displayName() + " <gray>pour <gold>"
                        + Text.money(em.price(t)) + "<gray>. Buvez-la pour l'activer !");
                later(() -> new AlchemistMenu(plugin, viewer).open());
            });
        }

        set(40, Items.make(Material.GOLD_INGOT, "<gold>Votre solde",
                "<yellow>" + Text.money(plugin.economy().balance(viewer.getUniqueId()))));
    }
}
