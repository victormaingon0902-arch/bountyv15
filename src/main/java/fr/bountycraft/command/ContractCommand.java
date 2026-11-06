package fr.bountycraft.command;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.hunter.ContractManager;
import fr.bountycraft.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /contract [cancel] */
public final class ContractCommand implements CommandExecutor, TabCompleter {
    private final BountyCraft plugin;

    public ContractCommand(BountyCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Text.send(s, "<red>Commande réservée aux joueurs.");
            return true;
        }
        ContractManager cm = plugin.contracts();
        ContractManager.Contract c = cm.get(p.getUniqueId());
        if (c == null) {
            Text.send(p, "<gray>Vous n'avez aucun contrat. Parlez au PNJ <dark_red>Hunter<gray> pour en prendre un.");
            return true;
        }
        if (a.length > 0 && (a[0].equalsIgnoreCase("cancel") || a[0].equalsIgnoreCase("annuler"))) {
            cm.cancel(p.getUniqueId());
            Text.send(p, "<yellow>Contrat annulé.");
            return true;
        }
        long left = (c.expiresAt() - System.currentTimeMillis()) / 1000;
        Text.send(p, "<gray>Contrat : <white>" + c.targetName() + "<gray> | Prime : <gold>"
                + Text.money(cm.bountyFor(c.target())) + "<gray> | Reste : <aqua>" + Text.time(left)
                + " <dark_gray>(/contract cancel)");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        return a.length == 1 && "cancel".startsWith(a[0].toLowerCase()) ? List.of("cancel") : List.of();
    }
}
