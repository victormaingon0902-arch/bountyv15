package fr.bountycraft.gui;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.quest.QuestManager;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Menu du Quêtier : une quête par heure. */
public final class QuestMenu extends Menu {

    public QuestMenu(BountyCraft plugin, Player viewer) {
        super(plugin, viewer, 3, Text.mm("<yellow><bold>Quêtier <dark_gray>» <gray>Quête horaire"));
        build();
    }

    private void refresh() {
        later(() -> new QuestMenu(plugin, viewer).open());
    }

    private void build() {
        fill(Items.pane(), 0, 26);
        QuestManager qm = plugin.quests();
        QuestManager.Quest q = qm.active(viewer);

        if (q == null) {
            long cd = qm.cooldownLeft(viewer);
            if (cd > 0) {
                set(13, Items.make(Material.CLOCK,
                        "<yellow>Prochaine quête dans <white>" + Text.time((cd + 999) / 1000),
                        "<gray>Une nouvelle quête est disponible", "<gray>toutes les heures."));
            } else {
                set(13, Items.make(Material.WRITABLE_BOOK, "<green><bold>Accepter une quête",
                        "<gray>Une quête au hasard, bien payée !", "", "<green>Clic pour accepter"), e -> {
                    String err = qm.take(viewer);
                    if (err != null) Text.send(viewer, "<red>" + err);
                    refresh();
                });
            }
            return;
        }

        int prog = Math.min(qm.progress(viewer), q.amount());
        boolean done = prog >= q.amount();
        Material icon = q.type() == QuestManager.Type.DELIVER && !q.items().isEmpty()
                ? q.items().iterator().next() : Material.IRON_SWORD;

        set(13, Items.make(icon, "<yellow><bold>" + q.name(), List.of(
                "<gray>" + q.description(),
                "",
                "<gray>Progression : <white>" + prog + "/" + q.amount(),
                "<gray>Récompense : <gold>" + Text.money(q.reward()))));

        if (done) {
            set(11, Items.make(Material.EMERALD, "<green><bold>Récupérer la récompense",
                    "<gray>" + Text.money(q.reward()), "", "<green>Clic pour encaisser"), e -> {
                qm.claim(viewer);
                refresh();
            });
        } else if (q.type() == QuestManager.Type.DELIVER) {
            set(11, Items.make(Material.CHEST, "<green><bold>Rendre les objets",
                    "<gray>Remet les objets demandés", "<gray>que vous avez sur vous.", "", "<green>Clic pour livrer"), e -> {
                qm.deliver(viewer);
                refresh();
            });
        } else {
            set(11, Items.make(Material.BARRIER, "<gray>Tuez les créatures demandées",
                    "<gray>Revenez ici une fois terminé."));
        }

        set(15, Items.make(Material.RED_DYE, "<red>Abandonner la quête",
                "<gray>Délai avant la suivante : quelques minutes."), e -> {
            qm.abandon(viewer);
            refresh();
        });
    }
}
