package fr.bountycraft.command;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.smith.KitManager;
import fr.bountycraft.util.Text;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /kit create &lt;nom&gt; &lt;prix&gt;                       : crée un kit depuis la shulker tenue en main
 * /kit update &lt;kit&gt;                                : remplace le contenu d'un kit par la shulker tenue en main
 * /kit perm &lt;kit&gt; [permission|none] [libellé]      : réserve un kit à une permission (ex. group.vip)
 * /kit key &lt;kit&gt; [texture|none]                    : choisit la texture de la clé du kit
 * /kit list                                         : liste les kits, leur prix, leur permission et leur texture
 */
public final class KitCommand implements CommandExecutor, TabCompleter {
    private final BountyCraft plugin;

    public KitCommand(BountyCraft plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("bountycraft.admin")) { Text.send(s, "<red>Permission refusée."); return true; }
        if (a.length == 0) { usage(s); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(s, a);
            case "update" -> update(s, a);
            case "perm", "permission" -> perm(s, a);
            case "key", "cle", "clé" -> key(s, a);
            case "list" -> list(s);
            default -> usage(s);
        }
        return true;
    }

    private void usage(CommandSender s) {
        Text.send(s, "<red>Usage : <white>/kit create <nom> <prix>");
        Text.send(s, "<red>        <white>/kit update <kit> <gray>(shulker remplie en main)");
        Text.send(s, "<red>        <white>/kit perm <kit> [permission|none] [libellé]");
        Text.send(s, "<red>        <white>/kit key <kit> [texture|none]");
        Text.send(s, "<red>        <white>/kit list");
    }

    private void create(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Text.send(s, "<red>Commande réservée aux joueurs."); return; }
        if (a.length < 3) { Text.send(s, "<red>Usage : /kit create <nom> <prix>"); return; }
        try {
            long price = Long.parseLong(a[a.length - 1]);
            String name = String.join(" ", Arrays.copyOfRange(a, 1, a.length - 1));
            plugin.kits().create(p, name, price);
        } catch (NumberFormatException ex) {
            Text.send(s, "<red>Prix invalide.");
        }
    }

    private void update(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { Text.send(s, "<red>Commande réservée aux joueurs."); return; }
        if (a.length < 2) { Text.send(s, "<red>Usage : /kit update <kit> <gray>(tenez la shulker remplie en main principale)"); return; }
        String id = plugin.kits().resolve(a[1]);
        if (id == null) { Text.send(s, "<red>Kit introuvable. <gray>(/kit list pour voir les kits)"); return; }
        plugin.kits().update(p, id);
    }

    private void perm(CommandSender s, String[] a) {
        if (a.length < 2) { Text.send(s, "<red>Usage : /kit perm <kit> [permission|none] [libellé]"); return; }
        String id = plugin.kits().resolve(a[1]);
        if (id == null) { Text.send(s, "<red>Kit introuvable. <gray>(/kit list pour voir les kits)"); return; }
        String node = a.length >= 3 ? a[2] : "bountycraft.kit." + id;
        if (node.equalsIgnoreCase("none") || node.equalsIgnoreCase("off") || node.equalsIgnoreCase("aucune")) {
            plugin.kits().setPermission(id, null, null);
            Text.send(s, "<green>Le kit <white>" + id + "<green> est de nouveau accessible à tous.");
            return;
        }
        String label = a.length >= 4 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : null;
        plugin.kits().setPermission(id, node, label);
        Text.send(s, "<green>Le kit <white>" + id + "<green> est réservé à la permission <white>" + node
                + (label != null ? "<green> (grade affiché : <gold>" + label + "<green>)" : "") + "<green>.");
        if (node.equals("bountycraft.kit." + id))
            Text.send(s, "<gray>Donnez-la au grade : <white>/lp group <groupe> permission set " + node + " true");
    }

    private void key(CommandSender s, String[] a) {
        if (a.length < 2) { Text.send(s, "<red>Usage : /kit key <kit> [texture|none]"); return; }
        String id = plugin.kits().resolve(a[1]);
        if (id == null) { Text.send(s, "<red>Kit introuvable. <gray>(/kit list pour voir les kits)"); return; }
        if (a.length < 3) {
            KitManager.Kit k = plugin.kits().get(id);
            Text.send(s, "<gray>Texture actuelle de la clé <white>" + id + "<gray> : <gold>" + (k != null && k.keyModel() != null ? k.keyModel() : "aucune"));
            Text.send(s, "<gray>Textures disponibles : <white>" + String.join(", ", KitManager.KEY_TEXTURES));
            return;
        }
        String arg = a[2].toLowerCase(Locale.ROOT);
        if (arg.equals("none") || arg.equals("off") || arg.equals("defaut") || arg.equals("défaut")) {
            plugin.kits().setKeyModel(id, null);
            Text.send(s, "<green>Texture de la clé <white>" + id + "<green> remise à la valeur par défaut (config.yml, <white>kit-keys<green>).");
            return;
        }
        String shortName = arg.startsWith("key_") ? arg.substring(4) : arg;
        if (!arg.contains(":") && !KitManager.KEY_TEXTURES.contains(shortName)) {
            Text.send(s, "<red>Texture inconnue. <gray>Disponibles : <white>" + String.join(", ", KitManager.KEY_TEXTURES));
            return;
        }
        plugin.kits().setKeyModel(id, arg);
        Text.send(s, "<green>La clé du kit <white>" + id + "<green> utilise maintenant <gold>" + KitManager.normalizeModel(arg)
                + "<green>. <gray>(Les clés déjà achetées gardent leur ancien aspect.)");
    }

    private void list(CommandSender s) {
        List<String> ids = plugin.kits().ids();
        if (ids.isEmpty()) { Text.send(s, "<gray>Aucun kit créé."); return; }
        Text.send(s, "<gold>Kits (" + ids.size() + ") :");
        for (String id : ids) {
            KitManager.Kit k = plugin.kits().get(id);
            if (k == null) continue;
            String texture = k.keyModel() == null ? "aucune" : k.keyModel().replace("bountycraft:key_", "");
            Text.send(s, "<gray>• <white>" + id + " <gray>— <gold>" + Text.money(k.price())
                    + " <gray>— " + (k.contents().length == 0 ? "<red>VIDE" : "<white>" + k.contents().length + " piles")
                    + " <gray>— clé : <white>" + texture
                    + " <gray>— " + (k.restricted() ? "<red>" + k.permission() + (k.label() != null ? " <gray>(" + k.label() + ")" : "") : "<green>libre"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("bountycraft.admin")) return List.of();
        List<String> out = new ArrayList<>();
        String sub = a.length >= 1 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (a.length == 1) out.addAll(List.of("create", "update", "perm", "key", "list"));
        else if (a.length == 2 && List.of("update", "perm", "permission", "key").contains(sub)) out.addAll(plugin.kits().ids());
        else if (a.length == 3 && sub.equals("perm")) {
            out.add("none"); out.add("group.vip");
            String id = plugin.kits().resolve(a[1]);
            if (id != null) out.add("bountycraft.kit." + id);
        } else if (a.length == 3 && sub.equals("key")) {
            out.addAll(KitManager.KEY_TEXTURES); out.add("none");
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
