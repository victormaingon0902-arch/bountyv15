package fr.bountycraft.quest;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Keys;
import fr.bountycraft.util.Text;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quête horaire : un joueur accepte une quête au hasard (parmi quests.yml), la termine, touche la récompense,
 * puis doit attendre {@code cooldown-minutes} avant la suivante. L'état est stocké dans le joueur (PDC).
 * Types : DELIVER (rapporter des objets) et KILL (tuer des monstres).
 */
public final class QuestManager {

    public enum Type { DELIVER, KILL }

    public record Quest(String id, Type type, String name, String description,
                        Set<Material> items, Set<EntityType> targets, int amount, long reward) {}

    private final BountyCraft plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private long cooldownMs = 3_600_000L;
    private long abandonCooldownMs = 300_000L;

    public QuestManager(BountyCraft plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        quests.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "quests.yml"));
        cooldownMs = y.getLong("settings.cooldown-minutes", 60) * 60_000L;
        abandonCooldownMs = y.getLong("settings.abandon-cooldown-minutes", 5) * 60_000L;

        ConfigurationSection root = y.getConfigurationSection("quests");
        if (root == null) return;
        var entityRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENTITY_TYPE);

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Type type = Type.valueOf(s.getString("type", "DELIVER").toUpperCase(Locale.ROOT));
                Set<Material> items = EnumSet.noneOf(Material.class);
                for (String n : s.getStringList("items")) {
                    Material m = Material.matchMaterial(n);
                    if (m != null) items.add(m);
                }
                Set<EntityType> targets = new java.util.HashSet<>();
                for (String n : s.getStringList("targets")) {
                    EntityType t = entityRegistry.get(NamespacedKey.minecraft(n.toLowerCase(Locale.ROOT)));
                    if (t != null) targets.add(t);
                }
                if (type == Type.DELIVER && items.isEmpty()) throw new IllegalArgumentException("aucun objet valide");
                if (type == Type.KILL && targets.isEmpty()) throw new IllegalArgumentException("aucune cible valide");
                quests.put(id, new Quest(id, type, s.getString("name", id), s.getString("description", ""),
                        items, targets, Math.max(1, s.getInt("amount", 10)), s.getLong("reward", 500)));
            } catch (Exception ex) {
                plugin.getLogger().warning("quests.yml : quête '" + id + "' ignorée (" + ex.getMessage() + ")");
            }
        }
        plugin.getLogger().info(quests.size() + " quête(s) chargée(s) depuis quests.yml");
    }

    // ------------------------------------------------------------------ état du joueur

    public Quest active(Player p) {
        String id = p.getPersistentDataContainer().get(Keys.QUEST_ID, PersistentDataType.STRING);
        return id == null ? null : quests.get(id);
    }

    public int progress(Player p) {
        Integer v = p.getPersistentDataContainer().get(Keys.QUEST_PROGRESS, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public boolean isComplete(Player p) {
        Quest q = active(p);
        return q != null && progress(p) >= q.amount();
    }

    /** Millisecondes avant de pouvoir accepter une nouvelle quête. */
    public long cooldownLeft(Player p) {
        Long next = p.getPersistentDataContainer().get(Keys.QUEST_NEXT, PersistentDataType.LONG);
        return next == null ? 0 : Math.max(0, next - System.currentTimeMillis());
    }

    private void clear(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(Keys.QUEST_ID);
        pdc.remove(Keys.QUEST_PROGRESS);
    }

    // ------------------------------------------------------------------ actions

    /** @return null si OK, sinon un message d'erreur. */
    public String take(Player p) {
        if (active(p) != null) return "Vous avez déjà une quête en cours.";
        long cd = cooldownLeft(p);
        if (cd > 0) return "Prochaine quête dans " + Text.time((cd + 999) / 1000) + ".";
        if (quests.isEmpty()) return "Aucune quête n'est disponible pour le moment.";

        List<Quest> list = new ArrayList<>(quests.values());
        Quest q = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(Keys.QUEST_ID, PersistentDataType.STRING, q.id());
        pdc.set(Keys.QUEST_PROGRESS, PersistentDataType.INTEGER, 0);
        Text.send(p, "<yellow>Nouvelle quête : <white>" + q.name() + "<gray> - " + q.description()
                + " <gray>(récompense <gold>" + Text.money(q.reward()) + "<gray>)");
        return null;
    }

    public void abandon(Player p) {
        if (active(p) == null) return;
        clear(p);
        p.getPersistentDataContainer().set(Keys.QUEST_NEXT, PersistentDataType.LONG,
                System.currentTimeMillis() + abandonCooldownMs);
        Text.send(p, "<yellow>Quête abandonnée. Nouvelle quête possible dans <white>"
                + Text.time(abandonCooldownMs / 1000) + "<yellow>.");
    }

    /** Quête DELIVER : retire les objets demandés de l'inventaire (progression cumulative). */
    public void deliver(Player p) {
        Quest q = active(p);
        if (q == null || q.type() != Type.DELIVER) return;
        int remaining = q.amount() - progress(p);
        if (remaining <= 0) {
            claim(p);
            return;
        }
        PlayerInventory inv = p.getInventory();
        int taken = 0;
        for (int i = 0; i < 36 && taken < remaining; i++) {
            ItemStack it = inv.getItem(i);
            // uniquement des objets "normaux" (pas de nom, d'enchantement ou de données personnalisées)
            if (it == null || it.hasItemMeta() || !q.items().contains(it.getType())) continue;
            int n = Math.min(it.getAmount(), remaining - taken);
            if (it.getAmount() - n <= 0) {
                inv.setItem(i, null);
            } else {
                ItemStack rest = it.clone();
                rest.setAmount(it.getAmount() - n);
                inv.setItem(i, rest);
            }
            taken += n;
        }
        if (taken == 0) {
            Text.send(p, "<red>Vous n'avez aucun objet demandé sur vous.");
            return;
        }
        int prog = progress(p) + taken;
        p.getPersistentDataContainer().set(Keys.QUEST_PROGRESS, PersistentDataType.INTEGER, prog);
        if (prog >= q.amount()) {
            claim(p);
        } else {
            Text.send(p, "<green>Livré : <white>" + taken + "<gray>. Progression : <white>" + prog + "/" + q.amount());
        }
    }

    /** Paye la récompense d'une quête terminée. */
    public void claim(Player p) {
        Quest q = active(p);
        if (q == null || progress(p) < q.amount()) return;
        plugin.economy().deposit(p.getUniqueId(), q.reward());
        clear(p);
        p.getPersistentDataContainer().set(Keys.QUEST_NEXT, PersistentDataType.LONG,
                System.currentTimeMillis() + cooldownMs);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Text.send(p, "<green><bold>Quête terminée ! <reset><gray>Vous gagnez <gold>" + Text.money(q.reward())
                + "<gray>. Prochaine quête dans <white>" + Text.time(cooldownMs / 1000) + "<gray>.");
    }

    /** Appelé quand un joueur tue une créature (hors joueurs). */
    public void onKill(Player killer, EntityType type) {
        Quest q = active(killer);
        if (q == null || q.type() != Type.KILL || !q.targets().contains(type)) return;
        int prog = progress(killer);
        if (prog >= q.amount()) return;
        prog++;
        killer.getPersistentDataContainer().set(Keys.QUEST_PROGRESS, PersistentDataType.INTEGER, prog);
        if (prog >= q.amount()) {
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            Text.send(killer, "<green>Quête <white>" + q.name() + "<green> accomplie ! <gray>Retournez voir le "
                    + "<yellow>Quêtier<gray> pour votre récompense.");
        } else {
            killer.sendActionBar(Text.mm("<yellow>Quête : <white>" + prog + "/" + q.amount()));
        }
    }
}
