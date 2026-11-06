package fr.bountycraft.elixir;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Fx;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Keys;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Élixirs : un effet passif + une ou deux compétences (touche F, Shift+F pour la seconde).
 * Un seul élixir actif par joueur ; il est perdu à la mort ou à l'expiration.
 */
public final class ElixirManager {

    public record Active(ElixirType type, long expiresAt) {}   // expiresAt = -1 : permanent

    private record Passive(PotionEffectType type, int amp) {}

    private final BountyCraft plugin;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> skillUntil = new HashMap<>();
    private final Set<UUID> windPending = new HashSet<>();   // Vent : propulsion lancée, dash pas encore parti
    private Map<Material, ItemStack> smelt;
    private Set<Material> smeltBlacklist = EnumSet.noneOf(Material.class);
    private int ticks;
    private final File purchasesFile;
    private final YamlConfiguration purchases;

    public ElixirManager(BountyCraft plugin) {
        this.plugin = plugin;
        this.purchasesFile = new File(plugin.getDataFolder(), "elixir-purchases.yml");
        this.purchases = YamlConfiguration.loadConfiguration(purchasesFile);
        reload();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void reload() {
        smelt = null;
        smeltBlacklist = EnumSet.noneOf(Material.class);
        for (String s : plugin.getConfig().getStringList("elixirs.fire.smelt-blacklist")) {
            Material m = Material.matchMaterial(s);
            if (m != null) smeltBlacklist.add(m);
        }
    }

    // ================================================================== config / objets

    public double num(ElixirType t, String key, double def) {
        return plugin.getConfig().getDouble("elixirs." + t.id() + "." + key, def);
    }

    public long price(ElixirType t) {
        return (long) num(t, "price", 10000);
    }

    private String durationText(ElixirType t) {
        double min = num(t, "duration-minutes", 30);
        return min < 0 ? "permanent (jusqu'à la mort)" : (long) min + " min";
    }

    private List<String> lore(ElixirType t) {
        List<String> l = new ArrayList<>();
        l.add("<dark_gray>Élixir spécial");
        l.add("");
        l.add("<yellow>Effet passif");
        switch (t) {
            case INVISIBILITY -> {
                l.add("<gray> » Invisibilité");
                l.add("<gray> » Les monstres ne vous attaquent jamais");
            }
            case WARDEN -> l.add("<gray> » +10 coeurs (une rangée de plus)");
            case HEAL -> l.add("<gray> » Régénération");
            case EMERALD -> {
                l.add("<gray> » Héros du village");
                l.add("<gray> » Célérité");
            }
            case WIND -> l.add("<gray> » Aucun dégât de chute");
            case END -> l.add("<gray> » Aucun (la magie est dans la compétence)");
            case FIRE -> l.add("<gray> » Résistance au feu");
            case WATER -> {
                l.add("<gray> » Grâce du dauphin");
                l.add("<gray> » Puissance de l'aqueduc");
            }
        }
        if (t.hasSkill()) {
            l.add("");
            l.add("<yellow>Compétence <dark_gray>(touche F)");
            switch (t) {
                case INVISIBILITY -> {
                    l.add("<gray> » Aura des ténèbres " + (long) num(t, "skill-duration-seconds", 300) / 60 + " min");
                    l.add("<gray>   (Obscurité aux joueurs à moins de " + (long) num(t, "darkness-radius", 10) + " blocs)");
                }
                case WARDEN -> l.add("<gray> » Rayon sonique du Warden");
                case HEAL -> l.add("<gray> » Soigne l'être que vous regardez (" + (long) num(t, "skill-duration-seconds", 30) + " s)");
                case WIND -> {
                    l.add("<gray> » Propulsion vers le haut, puis dash");
                    l.add("<gray>   vers l’avant (au sol, sans recharge)");
                }
                case END -> {
                    l.add("<gray> » Téléportation (sans recharge)");
                    l.add("<gray> » <dark_gray>Shift+F<gray> : souffle du dragon");
                }
                case FIRE -> {
                    l.add("<gray> » Cuit blocs et animaux que vous tuez");
                    l.add("<gray> » Enflamme ce que vous frappez (" + (long) num(t, "skill-duration-seconds", 180) / 60 + " min)");
                }
                case WATER -> {
                    l.add("<gray> » Fait pleuvoir " + (long) num(t, "skill-duration-seconds", 900) / 60 + " min");
                    l.add("<gray> » Trident Impulsion temporaire");
                }
                default -> { }
            }
        }
        l.add("");
        l.add("<gray>Durée : <white>" + durationText(t));
        l.add("<dark_gray>Un seul élixir actif à la fois.");
        return l;
    }

    public ItemStack create(ElixirType t) {
        Material base = Material.matchMaterial(plugin.getConfig().getString("elixirs.base-material", "HONEY_BOTTLE"));
        if (base == null || base.isAir()) base = Material.HONEY_BOTTLE;
        ItemStack it = new ItemStack(base);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.item(t.color() + "<bold>" + t.displayName()));
        m.lore(Text.item(lore(t)));
        NamespacedKey model = NamespacedKey.fromString("bountycraft:potion_" + t.id());
        if (model != null) m.setItemModel(model);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(Keys.ELIXIR_ID, PersistentDataType.STRING, t.id());
        it.setItemMeta(m);
        return it;
    }

    public ElixirType typeOf(ItemStack it) {
        return ElixirType.byId(Items.getString(it, Keys.ELIXIR_ID));
    }

    // ================================================================== état

    /** Nombre d'achats globaux encore disponibles pour ce type d'élixir. */
    public int purchasesLeft(ElixirType type) {
        int limit = Math.max(0, plugin.getConfig().getInt("elixirs." + type.id() + ".server-purchase-limit", 5));
        return Math.max(0, limit - purchases.getInt(type.id(), 0));
    }

    /** Enregistre un achat joueur ; les dons admin ne passent jamais ici. */
    public boolean registerPurchase(ElixirType type) {
        if (purchasesLeft(type) <= 0) return false;
        purchases.set(type.id(), purchases.getInt(type.id(), 0) + 1);
        try { purchases.save(purchasesFile); } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder les achats d'élixirs : " + ex.getMessage());
            return false;
        }
        return true;
    }

    /** Remet à 0 les achats de tous les élixirs : la limite par serveur redevient entièrement disponible. */
    public void resetPurchases() {
        for (String key : new ArrayList<>(purchases.getKeys(false))) purchases.set(key, null);
        try {
            purchases.save(purchasesFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder les achats d'élixirs : " + ex.getMessage());
        }
    }

    /** Reset général : retire l'élixir actif et tous les effets d'élixir, même si l'état n'avait pas encore été chargé. */
    public void resetPlayer(Player p) {
        for (ElixirType t : ElixirType.values()) removePassives(p, t);
        clear(p, false);
    }

    public ElixirType type(Player p) {
        Active a = active.get(p.getUniqueId());
        return a == null ? null : a.type();
    }

    public boolean has(Player p, ElixirType t) {
        return type(p) == t;
    }

    /** Vrai si la compétence chronométrée de cet élixir est en cours. */
    public boolean isSkillActive(Player p, ElixirType t) {
        if (!has(p, t)) return false;
        Long until = skillUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private List<Passive> passives(ElixirType t) {
        return switch (t) {
            case INVISIBILITY -> List.of(new Passive(PotionEffectType.INVISIBILITY, effectAmp(t, "invisibility-level", 1)));
            case WARDEN -> List.of(new Passive(PotionEffectType.HEALTH_BOOST, effectAmp(t, "health-boost-level", 5)));
            case HEAL -> List.of(new Passive(PotionEffectType.REGENERATION, effectAmp(t, "regeneration-level", 2)));
            case EMERALD -> List.of(new Passive(PotionEffectType.HERO_OF_THE_VILLAGE, effectAmp(t, "hero-level", 3)),
                    new Passive(PotionEffectType.HASTE, effectAmp(t, "haste-level", 2)));
            case FIRE -> List.of(new Passive(PotionEffectType.FIRE_RESISTANCE, effectAmp(t, "fire-resistance-level", 1)));
            case WATER -> List.of(new Passive(PotionEffectType.DOLPHINS_GRACE, effectAmp(t, "dolphins-grace-level", 2)),
                    new Passive(PotionEffectType.CONDUIT_POWER, effectAmp(t, "conduit-power-level", 1)));
            default -> List.of();
        };
    }

    private int effectAmp(ElixirType type, String key, int defaultLevel) {
        return Math.max(0, plugin.getConfig().getInt("elixirs." + type.id() + "." + key, defaultLevel) - 1);
    }

    private void applyPassives(Player p, Active a) {
        boolean infinite = a.expiresAt() < 0;
        int dur = infinite ? PotionEffect.INFINITE_DURATION
                : (int) Math.max(40, (a.expiresAt() - System.currentTimeMillis()) / 50);
        for (Passive ps : passives(a.type())) {
            PotionEffect cur = p.getPotionEffect(ps.type());
            boolean refresh = cur == null
                    || cur.getAmplifier() < ps.amp()
                    || (infinite && !cur.isInfinite())
                    || (!infinite && !cur.isInfinite() && cur.getDuration() < 100);
            if (refresh) {
                p.addPotionEffect(new PotionEffect(ps.type(), dur, ps.amp(), false, false, true));
            }
        }
    }

    private void removePassives(Player p, ElixirType t) {
        for (Passive ps : passives(t)) p.removePotionEffect(ps.type());
    }

    private void save(Player p, Active a) {
        p.getPersistentDataContainer().set(Keys.PLAYER_ELIXIR, PersistentDataType.STRING,
                a.type().id() + ":" + a.expiresAt());
    }

    /** Boire un élixir. */
    public void drink(Player p, ElixirType t) {
        UUID id = p.getUniqueId();
        Active old = active.get(id);
        if (old != null) {
            removePassives(p, old.type());
            Items.give(p, create(old.type()));
        }
        skillUntil.remove(id);
        purgeTridents(p, Long.MAX_VALUE);

        double minutes = num(t, "duration-minutes", 30);
        long exp = minutes < 0 ? -1 : System.currentTimeMillis() + (long) (minutes * 60_000);
        Active a = new Active(t, exp);
        active.put(id, a);
        save(p, a);
        applyPassives(p, a);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        Text.send(p, t.color() + "<bold>" + t.displayName() + "<reset><gray> activée ! Durée : <white>" + durationText(t));
        if (t.hasSkill()) Text.send(p, "<gray>Appuyez sur <yellow>F<gray> (échange de main) pour utiliser la compétence.");
    }

    /** Retire l'élixir actif. */
    public void clear(Player p, boolean notify) {
        UUID id = p.getUniqueId();
        Active a = active.remove(id);
        skillUntil.remove(id);
        p.getPersistentDataContainer().remove(Keys.PLAYER_ELIXIR);
        if (a == null) return;
        removePassives(p, a.type());
        purgeTridents(p, Long.MAX_VALUE);
        if (notify) Text.send(p, "<yellow>Votre " + a.type().displayName() + " s'est dissipée.");
    }

    public void load(Player p) {
        String raw = p.getPersistentDataContainer().get(Keys.PLAYER_ELIXIR, PersistentDataType.STRING);
        purgeTridents(p, System.currentTimeMillis());
        if (raw == null) return;
        String[] parts = raw.split(":");
        ElixirType t = ElixirType.byId(parts[0]);
        long exp = -1;
        try {
            if (parts.length > 1) exp = Long.parseLong(parts[1]);
        } catch (NumberFormatException ignored) {
            // valeur corrompue : traitée comme permanente
        }
        if (t == null || (exp > 0 && exp < System.currentTimeMillis())) {
            p.getPersistentDataContainer().remove(Keys.PLAYER_ELIXIR);
            return;
        }
        active.put(p.getUniqueId(), new Active(t, exp));
    }

    public void unload(Player p) {
        active.remove(p.getUniqueId());
        skillUntil.remove(p.getUniqueId());
    }

    public void onDeath(Player victim, PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isTemp);
        e.getItemsToKeep().removeIf(this::isTemp);
        clear(victim, false);
    }

    private boolean isTemp(ItemStack it) {
        return it != null && it.getType() != Material.AIR && Items.getLong(it, Keys.TEMP_UNTIL) != null;
    }

    // ================================================================== cooldowns

    private long cooldownLeft(UUID id, String slot) {
        Map<String, Long> m = cooldowns.get(id);
        if (m == null) return 0;
        return Math.max(0, m.getOrDefault(slot, 0L) - System.currentTimeMillis());
    }

    private void setCooldown(UUID id, String slot, long ms) {
        if (ms <= 0) return;
        cooldowns.computeIfAbsent(id, k -> new HashMap<>()).put(slot, System.currentTimeMillis() + ms);
    }

    // ================================================================== compétences

    /** Touche F (secondary = Shift+F). */
    public void cast(Player p, boolean secondary) {
        Active a = active.get(p.getUniqueId());
        if (a == null) return;
        ElixirType t = a.type();
        if (!t.hasSkill()) return;

        boolean second = secondary && t == ElixirType.END;
        String slot = t.id() + (second ? ":secondary" : ":primary");
        if (t == ElixirType.WIND) {
            if (windPending.contains(p.getUniqueId())) return;   // propulsion déjà en cours
            if (!p.isOnGround()) {
                p.sendActionBar(Text.mm("<red>Vous devez être au sol pour utiliser la propulsion."));
                return;
            }
        }
        long left = t == ElixirType.WIND ? 0 : cooldownLeft(p.getUniqueId(), slot);
        if (left > 0) {
            p.sendActionBar(Text.mm("<red>Compétence en recharge : <white>" + Text.time((left + 999) / 1000)));
            return;
        }
        long cd = switch (t) {
            case INVISIBILITY -> skillInvisibility(p);
            case WARDEN -> skillWarden(p);
            case HEAL -> skillHeal(p);
            case WIND -> skillWind(p);
            case END -> second ? skillBreath(p) : skillTeleport(p);
            case FIRE -> skillFire(p);
            case WATER -> skillWater(p);
            default -> -1L;
        };
        if (cd >= 0) setCooldown(p.getUniqueId(), slot, cd);
    }

    private long ms(ElixirType t, String key, double defSeconds) {
        return (long) (num(t, key, defSeconds) * 1000);
    }

    private boolean startTimed(Player p, ElixirType t, double defSeconds) {
        if (isSkillActive(p, t)) {
            Text.send(p, "<red>Cette compétence est déjà active.");
            return false;
        }
        skillUntil.put(p.getUniqueId(), System.currentTimeMillis() + ms(t, "skill-duration-seconds", defSeconds));
        return true;
    }

    private long skillInvisibility(Player p) {
        if (!startTimed(p, ElixirType.INVISIBILITY, 300)) return -1;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1f);
        Text.send(p, "<gray>Aura des ténèbres activée : <white>"
                + Text.time(ms(ElixirType.INVISIBILITY, "skill-duration-seconds", 300) / 1000));
        return ms(ElixirType.INVISIBILITY, "skill-cooldown-seconds", 600);
    }

    private long skillWarden(Player p) {
        ElixirType t = ElixirType.WARDEN;
        double range = num(t, "skill-range", 20);
        double damage = num(t, "skill-damage", 20);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();

        w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 1f);
        for (double d = 1; d <= range; d += 1.0) {
            Location l = eye.clone().add(dir.clone().multiply(d));
            Fx.particle(w, Particle.SONIC_BOOM, l, 1, 0, 0, 0, 0);
        }

        DamageSource src = DamageSource.builder(DamageType.SONIC_BOOM)
                .withCausingEntity(p).withDirectEntity(p).build();
        Location mid = eye.clone().add(dir.clone().multiply(range / 2));
        double box = range / 2 + 2;
        for (Entity e : w.getNearbyEntities(mid, box, box, box)) {
            if (!(e instanceof LivingEntity le) || e.equals(p) || e instanceof ArmorStand || plugin.npcs().isNpc(e)) continue;
            if (le instanceof Player op && op.getGameMode() == GameMode.SPECTATOR) continue;
            RayTraceResult hit = le.getBoundingBox().expand(0.4).rayTrace(eye.toVector(), dir, range);
            if (hit == null) continue;
            le.damage(damage, src);
            le.setVelocity(le.getVelocity().add(dir.clone().multiply(1.4).setY(0.4)));
        }
        return ms(t, "skill-cooldown-seconds", 20);
    }

    private long skillHeal(Player p) {
        if (!startTimed(p, ElixirType.HEAL, 30)) return -1;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.4f);
        Text.send(p, "<gray>Regardez un être vivant pour le soigner pendant <white>"
                + Text.time(ms(ElixirType.HEAL, "skill-duration-seconds", 30) / 1000) + "<gray>.");
        return ms(ElixirType.HEAL, "skill-cooldown-seconds", 120);
    }

    /**
     * Vent : 1) propulsion vers le haut, 2) quelques ticks plus tard, dash horizontal dans la direction
     * regardée à ce moment-là. Réglages (config.yml › elixirs.wind) : launch-power, dash-delay-ticks,
     * skill-power (vitesse du dash), dash-lift.
     */
    private long skillWind(Player p) {
        ElixirType t = ElixirType.WIND;
        double launch = num(t, "launch-power", 0.9);
        long delay = Math.max(1L, Math.round(num(t, "dash-delay-ticks", 8)));
        double dashPower = num(t, "skill-power", 1.3);
        double lift = num(t, "dash-lift", 0.2);
        UUID id = p.getUniqueId();

        // 1) propulsion vers le haut
        p.setVelocity(new Vector(0, launch, 0));
        p.setFallDistance(0f);
        windPending.add(id);
        Fx.particle(p.getWorld(), Particle.CLOUD, p.getLocation(), 30, 0.3, 0.1, 0.3, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.0f);

        // 2) puis dash vers l'avant
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            windPending.remove(id);
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline() || pl.isDead() || !has(pl, ElixirType.WIND)) return;
            Vector look = pl.getLocation().getDirection().setY(0);
            if (look.lengthSquared() < 0.0001) return;   // regard à la verticale : pas de dash
            look.normalize().multiply(dashPower);
            pl.setVelocity(new Vector(look.getX(), lift, look.getZ()));
            pl.setFallDistance(0f);
            Fx.particle(pl.getWorld(), Particle.CLOUD, pl.getLocation(), 30, 0.3, 0.1, 0.3, 0.05);
            pl.getWorld().playSound(pl.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.3f);
        }, delay);
        return 0;
    }

    private boolean isFree(Location feet) {
        Block b = feet.getBlock();
        Block head = b.getRelative(BlockFace.UP);
        return b.isPassable() && head.isPassable()
                && b.getType() != Material.LAVA && head.getType() != Material.LAVA
                && feet.getWorld().getWorldBorder().isInside(feet);
    }

    /** Téléportation vers le point regardé : aucun cooldown. */
    private long skillTeleport(Player p) {
        double max = num(ElixirType.END, "teleport-max-distance", 40);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult r = w.rayTraceBlocks(eye, dir, max, FluidCollisionMode.NEVER, true);
        double dist = (r != null && r.getHitPosition() != null)
                ? eye.toVector().distance(r.getHitPosition()) - 0.6
                : max;

        Location dest = null;
        for (double d = dist; d >= 0.5; d -= 0.5) {
            Location feet = eye.clone().add(dir.clone().multiply(d)).subtract(0, 1.62, 0);
            if (isFree(feet)) {
                dest = feet;
                break;
            }
        }
        if (dest == null) {
            p.sendActionBar(Text.mm("<red>Aucun emplacement libre dans cette direction."));
            return -1;
        }
        Location from = p.getLocation();
        dest.setYaw(from.getYaw());
        dest.setPitch(from.getPitch());
        Fx.particle(w, Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.5);
        p.teleport(dest);
        p.setFallDistance(0f);
        Fx.particle(w, Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.5);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        return 0;
    }

    /** Souffle du dragon (nuages persistants devant le joueur). */
    private long skillBreath(Player p) {
        ElixirType t = ElixirType.END;
        double radius = num(t, "breath-radius", 3.0);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        for (int i = 1; i <= 10; i++) {
            Fx.particle(w, Particle.DRAGON_BREATH, eye.clone().add(dir.clone().multiply(i)), 6, 0.3, 0.3, 0.3, 0.02);
        }
        for (int i = 1; i <= 3; i++) {
            Location at = Fx.ground(eye.clone().add(dir.clone().multiply(i * 3.0)), 8);
            w.spawn(at, AreaEffectCloud.class, c -> {
                c.setSource(p);
                c.setRadius((float) radius);
                c.setDuration(120);
                c.setReapplicationDelay(10);
                c.setRadiusPerTick(-0.01f);
                c.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1), true);
                if (Particle.DRAGON_BREATH.getDataType() == Float.class) {
                    c.setParticle(Particle.DRAGON_BREATH, 1.0f);
                } else {
                    c.setParticle(Particle.DRAGON_BREATH);
                }
                c.getPersistentDataContainer().set(Keys.BREATH, PersistentDataType.BYTE, (byte) 1);
            });
        }
        w.playSound(eye, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 1f);
        return ms(t, "breath-cooldown-seconds", 15);
    }

    private long skillFire(Player p) {
        if (!startTimed(p, ElixirType.FIRE, 180)) return -1;
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        Text.send(p, "<gray>Vos coups embrasent et tout ce que vous tuez ou minez est cuit pendant <white>"
                + Text.time(ms(ElixirType.FIRE, "skill-duration-seconds", 180) / 1000) + "<gray>.");
        return ms(ElixirType.FIRE, "skill-cooldown-seconds", 240);
    }

    private long skillWater(Player p) {
        ElixirType t = ElixirType.WATER;
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            Text.send(p, "<red>La pluie est impossible dans cette dimension.");
            return -1;
        }
        if (p.getInventory().firstEmpty() == -1) {
            Text.send(p, "<red>Libérez un emplacement d'inventaire pour recevoir le trident.");
            return -1;
        }
        if (!startTimed(p, t, 900)) return -1;

        long durMs = ms(t, "skill-duration-seconds", 900);
        w.setClearWeatherDuration(0);
        w.setStorm(true);
        w.setThundering(false);
        w.setWeatherDuration((int) Math.min(Integer.MAX_VALUE, durMs / 50));

        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta m = trident.getItemMeta();
        m.displayName(Text.item("<#2f6fe0><bold>Trident de la Marée <gray>(temporaire)"));
        m.lore(Text.item(List.of("<gray>Impulsion III - disparaît dans " + Text.time(durMs / 1000), "<dark_gray>Ne peut pas être jeté.")));
        m.setUnbreakable(true);
        m.addEnchant(Enchantment.RIPTIDE, 3, true);
        m.getPersistentDataContainer().set(Keys.TEMP_UNTIL, PersistentDataType.LONG, System.currentTimeMillis() + durMs);
        trident.setItemMeta(m);
        p.getInventory().addItem(trident);

        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.5f);
        Text.send(p, "<gray>La pluie tombe pendant <white>" + Text.time(durMs / 1000)
                + "<gray> et un <aqua>trident Impulsion<gray> vous est prêté.");
        return ms(t, "skill-cooldown-seconds", 1200);
    }

    // ================================================================== tâche périodique (0,5 s)

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        boolean slow = ticks % 4 == 0; // toutes les 2 s

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slow) purgeTridents(p, now);
            Active a = active.get(p.getUniqueId());
            if (a == null) continue;

            if (a.expiresAt() > 0 && now > a.expiresAt()) {
                clear(p, true);
                continue;
            }
            if (slow) applyPassives(p, a);

            Long until = skillUntil.get(p.getUniqueId());
            if (until == null) continue;
            if (until <= now) {
                skillUntil.remove(p.getUniqueId());
                skillEnded(p, a.type());
            } else if (a.type() == ElixirType.INVISIBILITY) {
                auraTick(p);
            } else if (a.type() == ElixirType.HEAL) {
                healTick(p);
            }
        }
    }

    private void skillEnded(Player p, ElixirType t) {
        switch (t) {
            case INVISIBILITY -> Text.send(p, "<gray>Votre aura des ténèbres s'est dissipée.");
            case HEAL -> Text.send(p, "<gray>Votre soin à distance est terminé.");
            case FIRE -> Text.send(p, "<gray>Le feu s'éteint : vos coups ne brûlent plus.");
            case WATER -> {
                Text.send(p, "<gray>Le trident s'évapore.");
                purgeTridents(p, Long.MAX_VALUE);
            }
            default -> { }
        }
    }

    private void auraTick(Player p) {
        double r = num(ElixirType.INVISIBILITY, "darkness-radius", 10);
        Location here = p.getLocation();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof Player o && o.getGameMode() != GameMode.SPECTATOR
                    && o.getWorld().equals(here.getWorld())
                    && o.getLocation().distanceSquared(here) <= r * r) {
                o.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false));
            }
        }
        if (ticks % 2 == 0) Fx.ring(here.clone().add(0, 0.3, 0), r, 48, Particle.SMOKE);
    }

    private void healTick(Player p) {
        double amount = num(ElixirType.HEAL, "heal-per-second", 4) * 0.5;
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceEntities(eye, eye.getDirection(), 25, 0.6,
                e -> e instanceof LivingEntity && !e.equals(p) && !(e instanceof ArmorStand) && !plugin.npcs().isNpc(e));
        if (r == null || !(r.getHitEntity() instanceof LivingEntity le) || le.isDead()) return;
        AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 20.0;
        le.setHealth(Math.min(maxHealth, le.getHealth() + amount));
        Fx.particle(le.getWorld(), Particle.HEART, le.getLocation().add(0, le.getHeight() + 0.2, 0), 2, 0.3, 0.2, 0.3, 0);
    }

    /** Supprime les tridents temporaires expirés (ou tous si now = Long.MAX_VALUE). */
    private void purgeTridents(Player p, long now) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != Material.TRIDENT) continue;
            Long until = Items.getLong(it, Keys.TEMP_UNTIL);
            if (until != null && until < now) inv.setItem(i, null);
        }
    }

    // ================================================================== cuisson (potion de feu)

    private void buildSmelt() {
        smelt = new EnumMap<>(Material.class);
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            if (it.next() instanceof FurnaceRecipe fr && fr.getInputChoice() instanceof RecipeChoice.MaterialChoice mc) {
                for (Material m : mc.getChoices()) smelt.putIfAbsent(m, fr.getResult());
            }
        }
    }

    /** Résultat cuit d'un objet, ou null s'il ne se cuit pas / est exclu. */
    public ItemStack smeltResult(ItemStack in) {
        if (smelt == null) buildSmelt();
        Material m = in.getType();
        if (smeltBlacklist.contains(m) || Tag.LOGS_THAT_BURN.isTagged(m)) return null;
        ItemStack r = smelt.get(m);
        if (r == null) return null;
        ItemStack out = r.clone();
        out.setAmount(Math.min(out.getMaxStackSize(), r.getAmount() * in.getAmount()));
        return out;
    }

    public int igniteTicks() {
        return (int) num(ElixirType.FIRE, "ignite-seconds", 5) * 20;
    }

    // ================================================================== HUD

    /** Ligne d'action bar (MiniMessage) ou null. */
    public String hud(Player p) {
        Active a = active.get(p.getUniqueId());
        if (a == null) return null;
        ElixirType t = a.type();
        StringBuilder sb = new StringBuilder(t.color()).append("<bold>").append(t.shortName()).append("<reset>");
        if (!t.hasSkill()) return sb.toString();

        long now = System.currentTimeMillis();
        Long until = skillUntil.get(p.getUniqueId());
        if (until != null && until > now) {
            sb.append(" <dark_gray>| <green>actif ").append(Text.time((until - now) / 1000));
        }
        if (t == ElixirType.END) {
            sb.append(" <dark_gray>| <green>F : téléportation");
            long left = cooldownLeft(p.getUniqueId(), t.id() + ":secondary");
            sb.append(left > 0 ? " <dark_gray>| <red>souffle " + Text.time((left + 999) / 1000)
                    : " <dark_gray>| <green>Shift+F : souffle");
        } else {
            long left = cooldownLeft(p.getUniqueId(), t.id() + ":primary");
            sb.append(left > 0 ? " <dark_gray>| <red>recharge " + Text.time((left + 999) / 1000)
                    : " <dark_gray>| <green>[F] prête");
        }
        return sb.toString();
    }
}
