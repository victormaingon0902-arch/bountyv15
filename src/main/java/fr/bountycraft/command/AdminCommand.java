package fr.bountycraft.command;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.elixir.ElixirType;
import fr.bountycraft.smith.ForgeManager;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /bountycraft reload | give <joueur> <id> | pack | ids */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final BountyCraft plugin;

    public AdminCommand(BountyCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            Text.send(s, "<red>Usage : /bountycraft <reload | give <joueur> <id> | pack | ids | reset>");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                Text.send(s, "<green>Configuration, boutique du forgeron et guide rechargés.");
            }
            case "reset" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("confirm")) plugin.resets().confirm(s);
                else plugin.resets().request(s);
            }
            case "ids" -> {
                StringBuilder sb = new StringBuilder("<gold>Objets : <white>");
                for (ForgeManager.ForgeItem fi : plugin.forge().all()) sb.append(fi.id()).append(' ');
                for (ElixirType t : ElixirType.values()) sb.append(t.id()).append(' ');
                Text.send(s, sb.toString());
            }
            case "pack" -> {
                if (!(s instanceof Player p)) return true;
                String url = plugin.getConfig().getString("resource-pack.url", "");
                if (url == null || url.isBlank()) {
                    Text.send(p, "<red>resource-pack.url n'est pas renseigné dans config.yml.");
                } else {
                    p.setResourcePack(url, plugin.getConfig().getString("resource-pack.sha1", ""),
                            plugin.getConfig().getBoolean("resource-pack.required", false),
                            Text.mm(plugin.getConfig().getString("resource-pack.prompt", "")));
                }
            }
            case "give" -> {
                if (a.length < 3) {
                    Text.send(s, "<red>Usage : /bountycraft give <joueur> <id>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null) {
                    Text.send(s, "<red>Joueur introuvable.");
                    return true;
                }
                ItemStack item = null;
                ForgeManager.ForgeItem fi = plugin.forge().get(a[2]);
                if (fi != null) item = plugin.forge().create(fi);
                ElixirType t = ElixirType.byId(a[2]);
                if (item == null && t != null) item = plugin.elixirs().create(t);
                if (item == null) {
                    Text.send(s, "<red>Id inconnu (voir /bountycraft ids).");
                    return true;
                }
                Items.give(target, item);
                Text.send(s, "<green>Objet donné à <white>" + target.getName() + "<green>.");
            }
            default -> Text.send(s, "<red>Sous-commande inconnue.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String o : List.of("reload", "give", "pack", "ids", "reset")) if (o.startsWith(last)) out.add(o);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("reset")) {
            if ("confirm".startsWith(last)) out.add("confirm");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (ForgeManager.ForgeItem fi : plugin.forge().all()) if (fi.id().startsWith(last)) out.add(fi.id());
            for (ElixirType t : ElixirType.values()) if (t.id().startsWith(last)) out.add(t.id());
        }
        return out;
    }
}
