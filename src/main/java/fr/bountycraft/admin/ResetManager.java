package fr.bountycraft.admin;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /bountycraft reset : remise à zéro générale du serveur.
 * <ul>
 *   <li>argent : tous les comptes (connectés ou non) retournent au solde de départ ;</li>
 *   <li>limite d'achats des élixirs : remise à 0 ;</li>
 *   <li>inventaire, armure, main secondaire, coffre de l'Ender et élixir actif : vidés tout de suite pour les
 *       joueurs connectés ; pour les autres, la remise à zéro est mémorisée (pending-reset.yml) et appliquée
 *       à leur prochaine connexion (un joueur déconnecté n'est pas modifiable via l'API du serveur).</li>
 * </ul>
 * Action irréversible : confirmation obligatoire + sauvegarde de data.yml et elixir-purchases.yml.
 */
public final class ResetManager implements Listener {
    private static final long CONFIRM_WINDOW_MS = 30_000L;

    private final BountyCraft plugin;
    private final File pendingFile;
    private final Set<UUID> pending = new HashSet<>();
    private final Map<String, Long> confirmations = new HashMap<>();   // expéditeur -> fin du délai de confirmation

    public ResetManager(BountyCraft plugin) {
        this.plugin = plugin;
        this.pendingFile = new File(plugin.getDataFolder(), "pending-reset.yml");
        load();
    }

    // ------------------------------------------------------------------ commande

    /** /bountycraft reset : demande de confirmation. */
    public void request(CommandSender s) {
        confirmations.put(s.getName(), System.currentTimeMillis() + CONFIRM_WINDOW_MS);
        long start = plugin.getConfig().getLong("economy.starting-balance", 500);
        Text.send(s, "<red><bold>ATTENTION : action irréversible !");
        Text.send(s, "<gray>Pour <white>tous<gray> les joueurs (connectés ou non) : inventaire et coffre de l'Ender vidés, "
                + "élixirs retirés, argent remis à <gold>" + Text.money(start) + "<gray>. "
                + "La limite d'achats des élixirs repasse à 0.");
        Text.send(s, "<gray>Confirmez dans les 30 s avec <white>/bountycraft reset confirm<gray>.");
    }

    /** /bountycraft reset confirm */
    public void confirm(CommandSender s) {
        Long until = confirmations.remove(s.getName());
        if (until == null || until < System.currentTimeMillis()) {
            Text.send(s, "<red>Aucune demande en cours (ou délai dépassé). Faites d'abord <white>/bountycraft reset<red>.");
            return;
        }
        execute(s);
    }

    private void execute(CommandSender s) {
        // 1) sauvegarde (le reset est annulé si elle échoue)
        plugin.economy().saveNow();
        File backup;
        try {
            backup = backup();
        } catch (IOException ex) {
            plugin.getLogger().severe("Reset annulé : sauvegarde impossible : " + ex.getMessage());
            Text.send(s, "<red>Reset annulé : la sauvegarde a échoué (" + ex.getMessage() + ").");
            return;
        }

        // 2) argent + limite des élixirs
        int accounts = plugin.economy().resetAllBalances();
        plugin.elixirs().resetPurchases();

        // 3) inventaires : joueurs connus (comptes + fichiers de joueurs du serveur) + joueurs connectés
        Set<UUID> targets = new HashSet<>(plugin.economy().accountIds());
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) targets.add(op.getUniqueId());
        for (Player p : Bukkit.getOnlinePlayers()) targets.add(p.getUniqueId());

        int online = 0;
        int offline = 0;
        for (UUID id : targets) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                resetPlayer(p);
                pending.remove(id);
                online++;
            } else {
                pending.add(id);
                offline++;
            }
        }
        save();

        plugin.getLogger().info("Reset général lancé par " + s.getName() + " : " + accounts + " compte(s), "
                + online + " joueur(s) connecté(s), " + offline + " en attente de connexion. Sauvegarde : " + backup.getName());
        Text.send(s, "<green>Reset terminé :");
        Text.send(s, "<gray> - argent : <white>" + accounts + "<gray> compte(s) remis à <gold>"
                + Text.money(plugin.getConfig().getLong("economy.starting-balance", 500)));
        Text.send(s, "<gray> - limite des élixirs : <white>remise à 0");
        Text.send(s, "<gray> - inventaires vidés : <white>" + online + "<gray> connecté(s) maintenant, <white>"
                + offline + "<gray> hors ligne (vidés à leur prochaine connexion)");
        Text.send(s, "<gray> - sauvegarde : <white>plugins/BountyCraft/backups/" + backup.getName());
    }

    // ------------------------------------------------------------------ application

    /** Vide l'inventaire, l'armure, la main secondaire, le curseur, le coffre de l'Ender et retire l'élixir. */
    private void resetPlayer(Player p) {
        p.closeInventory();
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        p.setItemOnCursor(new ItemStack(Material.AIR));
        p.getEnderChest().clear();
        plugin.elixirs().resetPlayer(p);
    }

    /** Joueur hors ligne lors du reset : on applique la remise à zéro dès qu'il se connecte. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!pending.remove(p.getUniqueId())) return;
        resetPlayer(p);
        save();
        Text.send(p, "<yellow>Reset général du serveur : votre inventaire et votre coffre de l'Ender ont été vidés.");
    }

    // ------------------------------------------------------------------ fichiers

    private File backup() throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File dir = new File(plugin.getDataFolder(), "backups/reset-" + stamp);
        Files.createDirectories(dir.toPath());
        for (String name : List.of("data.yml", "elixir-purchases.yml")) {
            File f = new File(plugin.getDataFolder(), name);
            if (f.exists()) Files.copy(f.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return dir;
    }

    private void load() {
        if (!pendingFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(pendingFile);
        for (String raw : y.getStringList("pending")) {
            try {
                pending.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // entrée invalide
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : pending) ids.add(id.toString());
        y.set("pending", ids);
        try {
            y.save(pendingFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder pending-reset.yml : " + ex.getMessage());
        }
    }
}
