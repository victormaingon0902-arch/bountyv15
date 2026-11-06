package fr.bountycraft.command;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

/** /hunt <ban|event> <durée> <joueur> [durée-du-ban|def] */
public final class HuntCommand implements CommandExecutor, TabCompleter {
    private final BountyCraft plugin;
    public HuntCommand(BountyCraft plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1 && a[0].equalsIgnoreCase("stop")) {
            if (!plugin.hunts().active()) Text.send(s, "<red>Aucun hunt en cours.");
            else plugin.hunts().stop("<red>Le hunt a été arrêté par un administrateur.");
            return true;
        }
        if (a.length == 1 && a[0].equalsIgnoreCase("info")) {
            if (!plugin.hunts().active()) Text.send(s, "<gray>Aucun hunt en cours.");
            else Text.send(s, "<red>Hunt : <white>" + plugin.hunts().targetName() + " <dark_gray>| <red>Temps : <white>" + Text.time(plugin.hunts().secondsLeft()));
            return true;
        }
        if (a.length < 3 || a.length > 4) {
            Text.send(s, "<red>Usage : /hunt <ban|event> <temps> <joueur> [durée-du-ban|def]"); return true;
        }
        boolean ban = a[0].equalsIgnoreCase("ban");
        if (!ban && !a[0].equalsIgnoreCase("event")) { Text.send(s, "<red>Type attendu : ban ou event."); return true; }
        long seconds = parseDuration(a[1]);
        if (seconds <= 0) { Text.send(s, "<red>Durée invalide. Exemples : 30m, 1h, 90s."); return true; }
        Player target = Bukkit.getPlayerExact(a[2]);
        if (target == null) { Text.send(s, "<red>Joueur introuvable ou hors ligne."); return true; }
        long banSeconds = -1;
        if (ban && a.length == 4 && !a[3].equalsIgnoreCase("def")) {
            banSeconds = parseDuration(a[3]);
            if (banSeconds <= 0) { Text.send(s, "<red>Durée de ban invalide."); return true; }
        }
        String error = plugin.hunts().start(s, target, ban, seconds, banSeconds);
        if (error != null) Text.send(s, "<red>" + error);
        return true;
    }
    private long parseDuration(String raw) {
        try {
            String v = raw.toLowerCase(Locale.ROOT).trim();
            long mul = 60;
            if (v.endsWith("s")) { mul = 1; v = v.substring(0, v.length()-1); }
            else if (v.endsWith("m")) v = v.substring(0, v.length()-1);
            else if (v.endsWith("h")) { mul = 3600; v = v.substring(0, v.length()-1); }
            else if (v.endsWith("d")) { mul = 86400; v = v.substring(0, v.length()-1); }
            return Math.multiplyExact(Long.parseLong(v), mul);
        } catch (Exception ex) { return -1; }
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return starts(List.of("ban", "event", "stop", "info"), a[0]);
        if (a.length == 2 && (a[0].equalsIgnoreCase("ban") || a[0].equalsIgnoreCase("event"))) return starts(List.of("10m", "30m", "1h"), a[1]);
        if (a.length == 3 && (a[0].equalsIgnoreCase("ban") || a[0].equalsIgnoreCase("event"))) return starts(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[2]);
        if (a.length == 4 && a[0].equalsIgnoreCase("ban")) return starts(List.of("def", "1d", "7d", "30d"), a[3]);
        return List.of();
    }
    private List<String> starts(List<String> values, String prefix) { return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList(); }
}
