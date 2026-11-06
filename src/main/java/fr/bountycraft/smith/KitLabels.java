package fr.bountycraft.smith;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Texte flottant au-dessus de chaque boîte de kit : nom du kit, clé qui l'ouvre, prix, grade requis.
 * Les textes ne sont pas sauvegardés dans le monde (setPersistent(false)) : ils sont recréés au démarrage
 * et au chargement de chaque chunk, donc il ne peut pas rester de texte orphelin ou en double.
 */
public final class KitLabels {
    private final BountyCraft plugin;
    private final KitManager kits;
    private final NamespacedKey tag;

    KitLabels(BountyCraft plugin, KitManager kits) {
        this.plugin = plugin;
        this.kits = kits;
        this.tag = new NamespacedKey(plugin, "kit_label");
    }

    /** (Re)crée le texte d'une boîte ; sans effet si son chunk n'est pas chargé. */
    public void refresh(Location block) {
        World w = block.getWorld();
        if (w == null || !w.isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)) return;
        String locKey = KitManager.locationKey(block);
        Location center = new Location(w, block.getBlockX() + 0.5, block.getBlockY() + 1.55, block.getBlockZ() + 0.5);
        removeNear(center, locKey);
        String id = kits.boxAt(block);
        KitManager.Kit kit = id == null ? null : kits.get(id);
        if (kit == null) return;
        Component text = render(kit);
        w.spawn(center, TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            td.setInvulnerable(true);
            td.setPersistent(false);
            td.getPersistentDataContainer().set(tag, PersistentDataType.STRING, locKey);
        });
    }

    /** Supprime le texte d'une boîte qui vient d'être retirée. */
    public void remove(Location block) {
        World w = block.getWorld();
        if (w == null || !w.isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)) return;
        removeNear(new Location(w, block.getBlockX() + 0.5, block.getBlockY() + 1.55, block.getBlockZ() + 0.5), KitManager.locationKey(block));
    }

    public void refreshAll() {
        for (Location l : kits.allBoxes().keySet()) safe(() -> refresh(l));
    }

    public void refreshKit(String kitId) {
        for (Map.Entry<Location, String> e : kits.allBoxes().entrySet()) if (e.getValue().equals(kitId)) safe(() -> refresh(e.getKey()));
    }

    public void refreshChunk(Chunk c) {
        for (Location l : kits.allBoxes().keySet())
            if (c.getWorld().equals(l.getWorld()) && (l.getBlockX() >> 4) == c.getX() && (l.getBlockZ() >> 4) == c.getZ()) safe(() -> refresh(l));
    }

    public void removeAll() {
        for (Location l : kits.allBoxes().keySet()) safe(() -> remove(l));
    }

    private void removeNear(Location center, String locKey) {
        for (TextDisplay d : center.getWorld().getNearbyEntitiesByType(TextDisplay.class, center, 2.0))
            if (locKey.equals(d.getPersistentDataContainer().get(tag, PersistentDataType.STRING))) d.remove();
    }

    private void safe(Runnable r) {
        try { r.run(); } catch (Exception e) { plugin.getLogger().warning("Texte de boîte de kit : " + e.getMessage()); }
    }

    private Component render(KitManager.Kit kit) {
        StringBuilder sb = new StringBuilder();
        sb.append("<gold><bold>Kit ").append(kit.name());
        sb.append("<newline><gray>Clé : <yellow>Clé du kit ").append(kit.name());
        sb.append("<newline><gray>Prix : <gold>").append(Text.money(kit.price()));
        if (kit.restricted()) sb.append("<newline><red>Réservé au grade <gold>").append(kit.label() != null ? kit.label() : "autorisé");
        return Text.mm(sb.toString());
    }
}
