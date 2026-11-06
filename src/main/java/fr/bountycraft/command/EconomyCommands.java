package fr.bountycraft.command;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.economy.EconomyManager;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /bank, /pay, /baltop, /eco */
public final class EconomyCommands implements CommandExecutor, TabCompleter {
    private final BountyCraft plugin;

    public EconomyCommands(BountyCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        EconomyManager eco = plugin.economy();
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "bank" -> bank(s, a, eco);
            case "pay" -> pay(s, a, eco);
            case "baltop" -> baltop(s, eco);
            case "eco" -> admin(s, a, eco);
            default -> { return false; }
        }
        return true;
    }

    private void bank(CommandSender s, String[] a, EconomyManager eco) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                Text.send(s, "<red>Usage : /bank <joueur>");
                return;
            }
            Text.send(p, "<gray>Votre solde : <gold>" + Text.money(eco.of(p).balance()));
            return;
        }
        if (!s.hasPermission("bountycraft.bank.others") && !(s instanceof Player p && p.getName().equalsIgnoreCase(a[0]))) {
            Text.send(s, "<red>Vous ne pouvez pas voir le solde des autres joueurs.");
            return;
        }
        EconomyManager.Account acc = eco.findByName(a[0]);
        if (acc == null) {
            Text.send(s, "<red>Joueur inconnu : " + a[0]);
            return;
        }
        Text.send(s, "<gray>Solde de <white>" + acc.name() + "<gray> : <gold>" + Text.money(acc.balance()));
    }

    private void pay(CommandSender s, String[] a, EconomyManager eco) {
        if (!(s instanceof Player p)) {
            Text.send(s, "<red>Commande réservée aux joueurs.");
            return;
        }
        if (a.length < 2) {
            Text.send(p, "<red>Usage : /pay <joueur> <montant>");
            return;
        }
        EconomyManager.Account target = eco.findByName(a[0]);
        if (target == null) {
            Text.send(p, "<red>Joueur inconnu : " + a[0]);
            return;
        }
        if (target.id().equals(p.getUniqueId())) {
            Text.send(p, "<red>Vous ne pouvez pas vous payer vous-même.");
            return;
        }
        Long amount = parse(a[1]);
        if (amount == null || amount <= 0) {
            Text.send(p, "<red>Montant invalide (nombre entier positif).");
            return;
        }
        eco.of(p);
        if (!eco.transfer(p.getUniqueId(), target.id(), amount)) {
            Text.send(p, "<red>Fonds insuffisants. <gray>Solde : <gold>" + Text.money(eco.balance(p.getUniqueId())));
            return;
        }
        Text.send(p, "<green>Vous avez envoyé <gold>" + Text.money(amount) + "<green> à <white>" + target.name() + "<green>.");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null) {
            Text.send(online, "<green>Vous avez reçu <gold>" + Text.money(amount) + "<green> de <white>" + p.getName() + "<green>.");
        }
    }

    private void baltop(CommandSender s, EconomyManager eco) {
        List<EconomyManager.Account> top = eco.top(10);
        Text.send(s, "<gold><bold>Top 10 des joueurs les plus riches");
        int i = 1;
        for (EconomyManager.Account acc : top) {
            s.sendMessage(Text.mm("<yellow>" + i++ + ". <white>" + acc.name() + " <dark_gray>- <gold>" + Text.money(acc.balance())));
        }
    }

    private void admin(CommandSender s, String[] a, EconomyManager eco) {
        if (a.length < 2) {
            Text.send(s, "<red>Usage : /eco <give|take|set|reset> <joueur> [montant]");
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        EconomyManager.Account acc = eco.findByName(a[1]);
        if (acc == null) {
            Text.send(s, "<red>Joueur inconnu : " + a[1]);
            return;
        }
        if (sub.equals("reset")) {
            eco.set(acc.id(), plugin.getConfig().getLong("economy.starting-balance", 500));
            Text.send(s, "<green>Solde de <white>" + acc.name() + "<green> réinitialisé.");
            return;
        }
        Long amount = a.length > 2 ? parse(a[2]) : null;
        if (amount == null || amount < 0) {
            Text.send(s, "<red>Montant invalide.");
            return;
        }
        switch (sub) {
            case "give" -> eco.deposit(acc.id(), amount);
            case "take" -> eco.set(acc.id(), Math.max(0, eco.balance(acc.id()) - amount));
            case "set" -> eco.set(acc.id(), amount);
            default -> {
                Text.send(s, "<red>Usage : /eco <give|take|set|reset> <joueur> [montant]");
                return;
            }
        }
        Text.send(s, "<green>Nouveau solde de <white>" + acc.name() + "<green> : <gold>" + Text.money(eco.balance(acc.id())));
    }

    private static Long parse(String raw) {
        try {
            return Long.parseLong(raw.replace("_", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (name.equals("eco") && a.length == 1) {
            for (String o : List.of("give", "take", "set", "reset")) if (o.startsWith(last)) out.add(o);
        } else if ((name.equals("bank") && a.length == 1)
                || (name.equals("pay") && a.length == 1)
                || (name.equals("eco") && a.length == 2)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
            }
        }
        return out;
    }
}
