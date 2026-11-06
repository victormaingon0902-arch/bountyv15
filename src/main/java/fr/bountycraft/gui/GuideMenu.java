package fr.bountycraft.gui;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.npc.GuideConfig;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Menu du PNJ Guide : Discord, site, règles, fonctionnement. */
public final class GuideMenu extends Menu {

    public GuideMenu(BountyCraft plugin, Player viewer) {
        super(plugin, viewer, 3, Text.mm("<aqua><bold>Guide <dark_gray>» <gray>Informations"));
        build();
    }

    private void build() {
        GuideConfig g = plugin.guide();
        fill(Items.pane(), 0, 26);

        set(10, Items.make(Material.BLUE_BANNER, "<#5865F2><bold>Discord",
                "<gray>Rejoignez notre communauté !", "", "<green>Clic pour afficher le lien"),
                e -> sendLink(g.discord(), "Discord"));

        set(12, Items.make(Material.COMPASS, "<gold><bold>Site web",
                "<gray>Boutique, wiki et actualités.", "", "<green>Clic pour afficher le lien"),
                e -> sendLink(g.website(), "Site web"));

        set(14, Items.make(Material.WRITTEN_BOOK, "<red><bold>Règles du serveur",
                "<gray>À lire avant de jouer.", "", "<green>Clic pour ouvrir le livre"),
                e -> openBook("Règles", g.rulesPages()));

        set(16, Items.make(Material.KNOWLEDGE_BOOK, "<green><bold>Comment ça marche ?",
                "<gray>Argent, Hunter, chasses, forge,", "<gray>élixirs...", "", "<green>Clic pour ouvrir le livre"),
                e -> openBook("Fonctionnement", g.howtoPages()));
    }

    private void sendLink(String url, String label) {
        viewer.closeInventory();
        if (url == null || !url.startsWith("http")) {
            Text.send(viewer, "<red>Lien non configuré (guide.yml).");
            return;
        }
        Component link = Text.mm("<aqua><underlined>" + label + " : " + url)
                .clickEvent(ClickEvent.openUrl(url));
        viewer.sendMessage(link);
    }

    private void openBook(String title, List<Component> pages) {
        viewer.closeInventory();
        if (pages.isEmpty()) {
            Text.send(viewer, "<red>Aucun contenu configuré (guide.yml).");
            return;
        }
        later(() -> viewer.openBook(Book.book(Text.mm(title), Text.mm("Serveur"), pages)));
    }
}
