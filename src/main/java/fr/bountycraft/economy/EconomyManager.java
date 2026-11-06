package fr.bountycraft.economy;

import fr.bountycraft.BountyCraft;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Économie du serveur + statistiques de PvP (utilisées pour calculer les primes).
 * Toutes les méthodes sont à appeler depuis le thread principal.
 * Stockage : plugins/BountyCraft/data.yml
 */
public final class EconomyManager {
    private static final long MAX_BALANCE = 1_000_000_000_000_000L;

    public static final class Account {
        private final UUID id;
        private String name;
        private long balance;
        private int deathsByPlayers;
        private int kills;

        Account(UUID id, String name, long balance, int deathsByPlayers, int kills) {
            this.id = id;
            this.name = name;
            this.balance = balance;
            this.deathsByPlayers = deathsByPlayers;
            this.kills = kills;
        }

        public UUID id() { return id; }
        public String name() { return name; }
        public long balance() { return balance; }
        public int deathsByPlayers() { return deathsByPlayers; }
        public int kills() { return kills; }
    }

    private final BountyCraft plugin;
    private final File file;
    private final Object ioLock = new Object();
    private final Map<UUID, Account> accounts = new HashMap<>();
    private boolean dirty;

    public EconomyManager(BountyCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
        mergeTwins();
        long period = Math.max(1, plugin.getConfig().getLong("economy.autosave-minutes", 5)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAsync, period, period);
    }

    // ------------------------------------------------------------------ comptes

    public Account get(UUID id) {
        return accounts.get(id);
    }

    /**
     * UUID générée par le serveur en mode crack (offline-mode) : version 3 (hash du pseudo).
     * Les UUID Mojang (mode premium) sont en version 4.
     */
    private static boolean isOfflineId(UUID id) {
        return id.version() == 3;
    }

    /**
     * Récupère (ou crée) le compte d'un joueur en ligne et met à jour son pseudo.
     * <p>
     * Premium et crack n'ont pas le même UUID pour un même pseudo. Sans précaution, passer d'un mode
     * à l'autre créait un second compte vide (500 $) : achats impossibles et /eco give sur le mauvais compte.
     * Au premier passage sous un nouvel UUID, on récupère donc le compte existant du même pseudo.
     */
    public Account of(Player p) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        Account a = accounts.get(id);
        if (a != null) {
            if (!a.name.equals(name)) {
                a.name = name;
                dirty = true;
            }
            return a;
        }

        List<Account> twins = new ArrayList<>();
        for (Account x : accounts.values()) {
            // même pseudo, et au moins un des deux UUID est un UUID "crack" (sinon ce sont deux joueurs premium différents)
            if (x.name.equalsIgnoreCase(name) && (isOfflineId(id) || isOfflineId(x.id))) twins.add(x);
        }
        if (!twins.isEmpty()) {
            plugin.getLogger().info("Compte de " + name + " récupéré sous son nouvel UUID (passage premium/crack).");
            return mergeInto(id, name, twins);
        }

        long start = plugin.getConfig().getLong("economy.starting-balance", 500);
        a = new Account(id, name, start, 0, 0);
        accounts.put(a.id, a);
        dirty = true;
        return a;
    }

    /**
     * Fusionne plusieurs comptes d'un même joueur en un seul, enregistré sous {@code keepId}.
     * Solde : le plus élevé parmi les comptes "utilisés" (un compte resté à l'état neuf est ignoré) ;
     * morts et kills : le maximum, pour ne rien perdre.
     */
    private Account mergeInto(UUID keepId, String name, List<Account> group) {
        long start = plugin.getConfig().getLong("economy.starting-balance", 500);
        Account best = null;
        int deaths = 0;
        int kills = 0;
        for (Account x : group) {
            boolean fresh = x.balance == start && x.deathsByPlayers == 0 && x.kills == 0;
            if (!fresh && (best == null || x.balance > best.balance)) best = x;
            deaths = Math.max(deaths, x.deathsByPlayers);
            kills = Math.max(kills, x.kills);
        }
        long balance = best != null ? best.balance : start;
        for (Account x : group) accounts.remove(x.id);
        Account merged = new Account(keepId, name, balance, deaths, kills);
        accounts.put(keepId, merged);
        dirty = true;
        return merged;
    }

    /** Au démarrage : fusionne les doublons déjà présents dans data.yml (même pseudo en premium ET en crack). */
    private void mergeTwins() {
        Map<String, List<Account>> byName = new HashMap<>();
        for (Account a : accounts.values()) {
            byName.computeIfAbsent(a.name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(a);
        }
        int count = 0;
        for (List<Account> group : byName.values()) {
            if (group.size() < 2) continue;
            List<Account> offline = new ArrayList<>();
            List<Account> premium = new ArrayList<>();
            for (Account a : group) (isOfflineId(a.id) ? offline : premium).add(a);

            List<Account> toMerge;
            Account keep;
            if (!offline.isEmpty() && premium.size() <= 1) {
                toMerge = group;                                   // le même joueur : 1 compte premium au plus + comptes crack
                keep = premium.isEmpty() ? offline.get(0) : premium.get(0);
            } else if (offline.size() >= 2) {
                toMerge = offline;                                 // même pseudo avec une casse différente en crack
                keep = offline.get(0);
            } else {
                continue;                                          // plusieurs comptes premium : personnes différentes
            }
            mergeInto(keep.id, keep.name, toMerge);
            count++;
        }
        if (count > 0) {
            plugin.getLogger().info("Comptes en double premium/crack fusionnés : " + count + " joueur(s).");
        }
    }

    /** Compte à utiliser pour un pseudo (priorité au joueur connecté, pour ne jamais viser un ancien doublon). */
    public Account findByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return of(online);
        Account best = null;
        for (Account a : accounts.values()) {
            if (a.name.equalsIgnoreCase(name) && (best == null || a.balance > best.balance)) best = a;
        }
        return best;
    }

    /** UUID de tous les comptes connus (connectés ou non). */
    public List<UUID> accountIds() {
        return new ArrayList<>(accounts.keySet());
    }

    /** Remet TOUS les comptes (connectés ou non) au solde de départ et sauvegarde. Renvoie le nombre de comptes. */
    public int resetAllBalances() {
        long start = plugin.getConfig().getLong("economy.starting-balance", 500);
        for (Account a : accounts.values()) a.balance = start;
        dirty = true;
        saveNow();
        return accounts.size();
    }

    public List<String> knownNames() {
        List<String> out = new ArrayList<>();
        for (Account a : accounts.values()) out.add(a.name);
        return out;
    }

    // ------------------------------------------------------------------ argent

    public long balance(UUID id) {
        Account a = accounts.get(id);
        return a == null ? 0 : a.balance;
    }

    public void deposit(UUID id, long amount) {
        Account a = accounts.get(id);
        if (a == null || amount <= 0) return;
        a.balance = (a.balance > MAX_BALANCE - amount) ? MAX_BALANCE : a.balance + amount;
        dirty = true;
    }

    public boolean withdraw(UUID id, long amount) {
        Account a = accounts.get(id);
        if (a == null || amount < 0 || a.balance < amount) return false;
        a.balance -= amount;
        dirty = true;
        return true;
    }

    public void set(UUID id, long amount) {
        Account a = accounts.get(id);
        if (a == null) return;
        a.balance = Math.max(0, Math.min(MAX_BALANCE, amount));
        dirty = true;
    }

    public boolean transfer(UUID from, UUID to, long amount) {
        if (!withdraw(from, amount)) return false;
        deposit(to, amount);
        return true;
    }

    public List<Account> top(int n) {
        List<Account> all = new ArrayList<>(accounts.values());
        all.sort(Comparator.comparingLong((Account a) -> a.balance).reversed());
        return all.subList(0, Math.min(n, all.size()));
    }

    // ------------------------------------------------------------------ stats PvP

    /** Nombre de fois où le joueur est mort de la main d'un autre joueur. */
    public int deathsByPlayers(UUID id) {
        Account a = accounts.get(id);
        return a == null ? 0 : a.deathsByPlayers;
    }

    public void addDeathByPlayer(UUID id) {
        Account a = accounts.get(id);
        if (a != null) { a.deathsByPlayers++; dirty = true; }
    }

    public void addKill(UUID id) {
        Account a = accounts.get(id);
        if (a != null) { a.kills++; dirty = true; }
    }

    // ------------------------------------------------------------------ persistance

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("accounts");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection a = s.getConfigurationSection(key);
                if (a == null) continue;
                accounts.put(id, new Account(id, a.getString("name", "?"), a.getLong("balance"),
                        a.getInt("deaths"), a.getInt("kills")));
            } catch (IllegalArgumentException ignored) {
                // clé invalide : on ignore
            }
        }
    }

    private String snapshot() {
        YamlConfiguration y = new YamlConfiguration();
        for (Account a : accounts.values()) {
            String base = "accounts." + a.id;
            y.set(base + ".name", a.name);
            y.set(base + ".balance", a.balance);
            y.set(base + ".deaths", a.deathsByPlayers);
            y.set(base + ".kills", a.kills);
        }
        return y.saveToString();
    }

    private void write(String data) {
        synchronized (ioLock) {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Path tmp = new File(file.getParentFile(), "data.yml.tmp").toPath();
                Files.writeString(tmp, data, StandardCharsets.UTF_8);
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + e.getMessage());
            }
        }
    }

    public void saveAsync() {
        if (!dirty) return;
        dirty = false;
        String data = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    public void saveNow() {
        dirty = false;
        write(snapshot());
    }
}
