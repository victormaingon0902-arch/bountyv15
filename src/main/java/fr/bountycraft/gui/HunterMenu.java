package fr.bountycraft.gui;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.hunter.ContractManager;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Menu du PNJ Hunter : choix de la cible / suivi du contrat en cours. */
public final class HunterMenu extends Menu {
    private static final int PER_PAGE = 45;
    private final int page;

    public HunterMenu(BountyCraft plugin, Player viewer, int page) {
        super(plugin, viewer, 6, Text.mm("<dark_red><bold>Hunter <dark_gray>» <gray>Contrats de chasse"));
        this.page = page;
        build();
    }

    private void build() {
        ContractManager cm = plugin.contracts();
        fill(Items.pane(), 45, 53);

        ContractManager.Contract c = cm.get(viewer.getUniqueId());
        if (c != null) {
            buildActive(cm, c);
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.remove(viewer);
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (int) Math.ceil(players.size() / (double) PER_PAGE));
        int from = page * PER_PAGE;
        int to = Math.min(players.size(), from + PER_PAGE);

        if (players.isEmpty()) {
            set(22, Items.make(Material.BARRIER, "<red>Aucune cible disponible",
                    "<gray>Aucun autre joueur n'est en ligne."));
        }
        for (int i = from; i < to; i++) {
            Player target = players.get(i);
            UUID id = target.getUniqueId();
            long protection = cm.protectionLeft(id);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Prime : <gold>" + Text.money(cm.bountyFor(id)));
            lore.add("<gray>Morts par des joueurs : <white>" + plugin.economy().deathsByPlayers(id));
            lore.add("");
            if (protection > 0) {
                lore.add("<red>Tué récemment - indisponible (" + Text.time(protection / 1000) + ")");
            } else {
                lore.add("<green>Clic pour accepter le contrat");
            }
            set(i - from, Items.head(target, "<yellow>" + target.getName(), lore), e -> {
                String err = cm.start(viewer, target);
                if (err != null) Text.send(viewer, "<red>" + err);
                viewer.closeInventory();
            });
        }

        set(49, Items.make(Material.PAPER, "<white>Page " + (page + 1) + "/" + pages,
                "<gray>Plus un joueur est mort de la main",
                "<gray>d'autres joueurs, plus sa prime baisse."));
        if (page > 0) {
            set(48, Items.make(Material.ARROW, "<yellow>« Page précédente"),
                    e -> later(() -> new HunterMenu(plugin, viewer, page - 1).open()));
        }
        if (page + 1 < pages) {
            set(50, Items.make(Material.ARROW, "<yellow>Page suivante »"),
                    e -> later(() -> new HunterMenu(plugin, viewer, page + 1).open()));
        }
    }

    private void buildActive(ContractManager cm, ContractManager.Contract c) {
        long left = (c.expiresAt() - System.currentTimeMillis()) / 1000;
        set(22, Items.make(Material.COMPASS, "<red><bold>Contrat en cours",
                "<gray>Cible : <white>" + c.targetName(),
                "<gray>Prime : <gold>" + Text.money(cm.bountyFor(c.target())),
                "<gray>Temps restant : <aqua>" + Text.time(left),
                "",
                "<dark_gray>Tuez la cible pour toucher la prime."));
        set(31, Items.make(Material.BARRIER, "<red>Annuler le contrat",
                "<gray>Vous pourrez en choisir un autre."), e -> {
            cm.cancel(viewer.getUniqueId());
            Text.send(viewer, "<yellow>Contrat annulé.");
            viewer.closeInventory();
        });
    }
}
