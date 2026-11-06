package fr.bountycraft.command;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.npc.NpcManager;
import fr.bountycraft.npc.NpcType;
import fr.bountycraft.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /npc create <hunter|blacksmith|alchemist|guide|quest|merchant>
 * /npc remove                 (PNJ regardé)
 * /npc skin [joueur]          (PNJ regardé ; sans argument = VOTRE skin)
 * /npc skinall [joueur]       (TOUS les PNJ ; sans argument = VOTRE skin)
 * /npc list
 */
public final class NpcCommand implements CommandExecutor, TabCompleter {
    private final BountyCraft plugin;

    public NpcCommand(BountyCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) return usage(s);
        NpcManager npcs = plugin.npcs();
        String sub = a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            Text.send(s, "<gold>PNJ enregistrés : <white>" + npcs.registry().size());
            for (Map.Entry<UUID, NpcManager.NpcRecord> en : npcs.registry().entrySet()) {
                NpcManager.NpcRecord r = en.getValue();
                s.sendMessage(Text.mm("<gray> - <yellow>" + r.type().name() + " <gray>" + r.world() + " "
                        + (int) r.x() + " " + (int) r.y() + " " + (int) r.z() + " " + state(en.getKey(), r)));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            Text.send(s, "<red>Commande réservée aux joueurs.");
            return true;
        }

        switch (sub) {
            case "create" -> {
                NpcType type = a.length > 1 ? NpcType.parse(a[1]) : null;
                if (type == null) {
                    Text.send(p, "<red>Type invalide. Types : hunter, blacksmith, alchemist, guide, quest, merchant");
                    return true;
                }
                NpcManager.CreateResult res = npcs.create(p, type);
                if (!res.ok()) {
                    Text.send(p, "<red>Le PNJ n'a pas pu être créé : <white>" + res.error());
                    return true;
                }
                Text.send(p, "<green>PNJ <white>" + type.name() + "<green> créé là où vous êtes (reculez d'un pas pour le voir). "
                        + "Utilisez <white>/npc skin<green> pour lui donner votre skin.");
            }
            case "remove" -> {
                Mannequin m = looked(p);
                if (m == null) return notLooking(p);
                npcs.remove(m);
                Text.send(p, "<green>PNJ supprimé.");
            }
            case "skin" -> {
                Mannequin m = looked(p);
                if (m == null) return notLooking(p);
                ResolvableProfile profile = profile(p, a);
                if (profile == null) return true;
                npcs.applySkin(m, profile);
                Text.send(p, "<green>Skin appliqué à ce PNJ.");
            }
            case "skinall" -> {
                ResolvableProfile profile = profile(p, a);
                if (profile == null) return true;
                npcs.applySkinAll(p, profile);
            }
            default -> {
                return usage(s);
            }
        }
        return true;
    }

    /** État réel du PNJ dans le monde (pour repérer ceux qui sont enregistrés mais absents). */
    private String state(UUID id, NpcManager.NpcRecord r) {
        World w = Bukkit.getWorld(r.world());
        if (w == null) return "<red>[monde introuvable]";
        if (!w.isChunkLoaded(((int) Math.floor(r.x())) >> 4, ((int) Math.floor(r.z())) >> 4)) {
            return "<gray>[chunk non chargé]";
        }
        Entity e = Bukkit.getEntity(id);
        return e != null && e.isValid() ? "<green>[présent]" : "<red>[ABSENT]";
    }

    private ResolvableProfile profile(Player p, String[] a) {
        try {
            return plugin.npcs().profileOf(p, a.length > 1 ? a[1] : null);
        } catch (IllegalArgumentException ex) {
            Text.send(p, "<red>Pseudo invalide.");
            return null;
        }
    }

    private Mannequin looked(Player p) {
        Entity e = p.getTargetEntity(6);
        if (e instanceof Mannequin m && plugin.npcs().isNpc(m)) return m;
        return null;
    }

    private boolean notLooking(Player p) {
        Text.send(p, "<red>Regardez un PNJ (à moins de 6 blocs).");
        return true;
    }

    private boolean usage(CommandSender s) {
        Text.send(s, "<red>Usage : /npc <create <type> | remove | skin [joueur] | skinall [joueur] | list>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String o : List.of("create", "remove", "skin", "skinall", "list")) if (o.startsWith(last)) out.add(o);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("create")) {
            for (String o : List.of("hunter", "blacksmith", "alchemist", "guide", "quest", "merchant")) if (o.startsWith(last)) out.add(o);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("skin") || a[0].equalsIgnoreCase("skinall"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
            }
        }
        return out;
    }
}
