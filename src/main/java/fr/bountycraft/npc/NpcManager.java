package fr.bountycraft.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.bountycraft.BountyCraft;
import fr.bountycraft.gui.AlchemistMenu;
import fr.bountycraft.gui.ForgeMenu;
import fr.bountycraft.gui.GuideMenu;
import fr.bountycraft.gui.HunterMenu;
import fr.bountycraft.gui.MerchantMenu;
import fr.bountycraft.gui.QuestMenu;
import fr.bountycraft.util.Keys;
import fr.bountycraft.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * PNJ basés sur l'entité Mannequin (skin de joueur natif, aucune dépendance).
 * Chaque PNJ est repéré par une donnée persistante (npc_type) et listé dans npcs.yml
 * (ce qui permet de modifier le skin de tous les PNJ, même dans des chunks non chargés).
 */
public final class NpcManager {

    public record NpcRecord(NpcType type, String world, double x, double y, double z) {}

    private final BountyCraft plugin;
    private final File file;
    private final Map<UUID, NpcRecord> registry = new LinkedHashMap<>();

    public NpcManager(BountyCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        load();
    }

    // ------------------------------------------------------------------ détection

    public boolean isNpc(Entity e) {
        return e instanceof Mannequin
                && e.getPersistentDataContainer().has(Keys.NPC_TYPE, PersistentDataType.STRING);
    }

    public NpcType typeOf(Entity e) {
        if (!(e instanceof Mannequin)) return null;
        return NpcType.parse(e.getPersistentDataContainer().get(Keys.NPC_TYPE, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------------ création / suppression

    /** Résultat d'une création : le PNJ, ou {@code null} + la raison de l'échec. */
    public record CreateResult(Mannequin npc, String error) {
        public boolean ok() { return npc != null; }
    }

    public CreateResult create(Player admin, NpcType type) {
        Location loc = admin.getLocation();
        World world = loc.getWorld();
        loc.setYaw(loc.getYaw() + 180f);
        loc.setPitch(0f);

        Mannequin npc;
        try {
            npc = world.spawn(loc, Mannequin.class, m -> setup(m, type));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de créer le PNJ " + type.name(), ex);
            return new CreateResult(null, "erreur pendant le spawn (détails dans la console : "
                    + ex.getClass().getSimpleName() + ")");
        }

        // Si le spawn est annulé (WorldGuard, plugin anti-lag, règle de monde...), Paper renvoie quand
        // même un objet Mannequin, mais il n'est JAMAIS ajouté au monde : on ne le voit donc pas.
        if (!npc.isValid()) {
            plugin.getLogger().warning("Spawn du PNJ " + type.name() + " refusé en " + world.getName()
                    + " (" + (int) loc.getX() + " " + (int) loc.getY() + " " + (int) loc.getZ()
                    + ") : l'entité n'a pas été ajoutée au monde (annulé par un autre plugin ?).");
            return new CreateResult(null, "le spawn a été annulé par le serveur ou un autre plugin "
                    + "(protection de zone, anti-lag...). Essayez ailleurs ou regardez la console");
        }

        registry.put(npc.getUniqueId(), new NpcRecord(type, world.getName(), loc.getX(), loc.getY(), loc.getZ()));
        save();

        UUID id = npc.getUniqueId();
        UUID adminId = admin.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> verify(id, adminId), 20L);
        return new CreateResult(npc, null);
    }

    /** 1 s après la création : le PNJ existe-t-il toujours, et le client de l'admin le reçoit-il ? */
    private void verify(UUID id, UUID adminId) {
        NpcRecord r = registry.get(id);
        if (r == null) return;
        Player admin = Bukkit.getPlayer(adminId);
        World w = Bukkit.getWorld(r.world());
        if (w == null) return;
        Entity e = Bukkit.getEntity(id);

        if (e == null || !e.isValid()) {
            // chunk déchargé entre-temps : on ne conclut rien
            if (!w.isChunkLoaded(((int) Math.floor(r.x())) >> 4, ((int) Math.floor(r.z())) >> 4)) return;
            registry.remove(id);
            save();
            plugin.getLogger().warning("Le PNJ " + r.type().name() + " (" + id + ") a disparu juste après sa création : "
                    + "un autre plugin le supprime (nettoyage d'entités, protection...).");
            if (admin != null) {
                Text.send(admin, "<red>Le PNJ a disparu 1 seconde après sa création : un autre plugin "
                        + "(nettoyage d'entités, protection...) le supprime. Voir la console.");
            }
            return;
        }

        if (admin != null && admin.isOnline() && admin.getWorld().equals(e.getWorld())
                && admin.getLocation().distanceSquared(e.getLocation()) < 24 * 24
                && !e.isTrackedBy(admin)) {
            plugin.getLogger().warning("Le PNJ " + id + " existe mais n'est pas envoyé au client de " + admin.getName() + ".");
            Text.send(admin, "<red>Le PNJ existe côté serveur mais n'est pas envoyé à votre client "
                    + "(plugin de visibilité d'entités ou entity-tracking-range trop bas ?).");
        }
    }

    private void setup(Mannequin m, NpcType type) {
        m.setInvisible(false);
        m.setVisibleByDefault(true);
        m.setImmovable(true);
        m.setInvulnerable(true);
        m.setPersistent(true);
        m.setSilent(true);
        m.setRemoveWhenFarAway(false);
        m.customName(Text.mm(plugin.getConfig().getString("npcs." + type.configKey() + ".name", type.defaultName())));
        m.setCustomNameVisible(true);
        m.setDescription(Text.mm(plugin.getConfig().getString("npcs." + type.configKey() + ".description", type.defaultDescription())));
        m.getPersistentDataContainer().set(Keys.NPC_TYPE, PersistentDataType.STRING, type.name());

        String skin = plugin.getConfig().getString("npcs.default-skin", "");
        if (skin != null && !skin.isBlank()) {
            try {
                m.setProfile(profileOf(null, skin.trim()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("npcs.default-skin invalide : " + skin);
            }
        }
    }

    public void remove(Mannequin m) {
        registry.remove(m.getUniqueId());
        m.remove();
        save();
    }

    // ------------------------------------------------------------------ skins

    /**
     * Profil de skin : le joueur {@code self} si name == null, sinon le joueur nommé
     * (en ligne : son profil complet ; hors-ligne : résolu côté client par son pseudo).
     */
    public ResolvableProfile profileOf(Player self, String name) {
        if (name == null) {
            PlayerProfile pp = self.getPlayerProfile();
            return ResolvableProfile.resolvableProfile(pp);
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return ResolvableProfile.resolvableProfile(online.getPlayerProfile());
        return ResolvableProfile.resolvableProfile().name(name).build();
    }

    public void applySkin(Mannequin m, ResolvableProfile profile) {
        m.setProfile(profile);
    }

    /** Applique un skin à TOUS les PNJ (charge les chunks nécessaires). */
    public void applySkinAll(CommandSender sender, ResolvableProfile profile) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (Map.Entry<UUID, NpcRecord> en : registry.entrySet()) {
            NpcRecord r = en.getValue();
            World w = Bukkit.getWorld(r.world());
            if (w == null) continue;
            UUID id = en.getKey();
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            futures.add(f);
            w.getChunkAtAsync(((int) Math.floor(r.x())) >> 4, ((int) Math.floor(r.z())) >> 4).thenAccept(chunk -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Entity e = Bukkit.getEntity(id);
                    boolean ok = e instanceof Mannequin;
                    if (ok) ((Mannequin) e).setProfile(profile);
                    f.complete(ok);
                });
            });
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    long done = futures.stream().filter(x -> x.getNow(false)).count();
                    Text.send(sender, "<green>Skin appliqué à <white>" + done + "<green> PNJ sur "
                            + registry.size() + " enregistré(s).");
                }));
    }

    // ------------------------------------------------------------------ interaction

    public void open(Player p, Mannequin m) {
        NpcType type = typeOf(m);
        if (type == null) return;
        switch (type) {
            case HUNTER -> new HunterMenu(plugin, p, 0).open();
            case BLACKSMITH -> new ForgeMenu(plugin, p, 0).open();
            case ALCHEMIST -> new AlchemistMenu(plugin, p).open();
            case GUIDE -> new GuideMenu(plugin, p).open();
            case QUEST -> new QuestMenu(plugin, p).open();
            case MERCHANT -> new MerchantMenu(plugin, p, 0).open();
        }
    }

    // ------------------------------------------------------------------ registre

    public Map<UUID, NpcRecord> registry() {
        return registry;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("npcs");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            try {
                ConfigurationSection n = s.getConfigurationSection(key);
                NpcType type = NpcType.parse(n.getString("type"));
                if (type == null) continue;
                registry.put(UUID.fromString(key), new NpcRecord(type, n.getString("world", "world"),
                        n.getDouble("x"), n.getDouble("y"), n.getDouble("z")));
            } catch (Exception ignored) {
                // entrée invalide
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, NpcRecord> en : registry.entrySet()) {
            String base = "npcs." + en.getKey();
            NpcRecord r = en.getValue();
            y.set(base + ".type", r.type().name());
            y.set(base + ".world", r.world());
            y.set(base + ".x", r.x());
            y.set(base + ".y", r.y());
            y.set(base + ".z", r.z());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder npcs.yml : " + e.getMessage());
        }
    }
}
