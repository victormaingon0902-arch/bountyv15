package fr.bountycraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Utilitaires de texte (MiniMessage, argent, temps). */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static String currency = "$";
    private static String prefix = "<dark_gray>[<gold>Bounty<dark_gray>] <gray>";

    private Text() {}

    public static void configure(String currencySymbol, String chatPrefix) {
        currency = currencySymbol;
        prefix = chatPrefix;
    }

    /** MiniMessage -> Component. */
    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** MiniMessage -> Component sans italique (noms et lores d'objets). */
    public static Component item(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> item(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(item(l));
        return out;
    }

    public static void send(CommandSender to, String body) {
        to.sendMessage(mm(prefix + body));
    }

    public static void broadcast(String body) {
        Bukkit.broadcast(mm(prefix + body));
    }

    public static String money(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', ' ') + " " + currency;
    }

    /** mm:ss */
    public static String time(long seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
