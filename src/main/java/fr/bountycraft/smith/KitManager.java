package fr.bountycraft.smith;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Items;
import fr.bountycraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Stockage des kits créés par les administrateurs et des shulkers placées dans le monde. */
public final class KitManager {
    /** {@code permission} = nœud requis pour acheter/ouvrir le kit (null = kit libre), {@code label} = nom du grade affiché aux joueurs. */
    public record Kit(String id, String name, long price, ItemStack[] contents, String permission, String label, String keyModel) {
        public boolean restricted() { return permission != null; }
    }
    /** Textures de clés présentes dans le resource pack (assets/bountycraft/items/key_<nom>.json). */
    public static final List<String> KEY_TEXTURES = List.of("copper", "fire", "gold", "diamond", "iron", "amethyst", "steel", "wood");
    private final BountyCraft plugin;
    private final File kitsFile, boxesFile;
    private final YamlConfiguration kits = new YamlConfiguration(), boxes = new YamlConfiguration();
    private final NamespacedKey keyId, boxId;
    private final KitLabels labels;

    public KitManager(BountyCraft plugin) {
        this.plugin = plugin;
        kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        boxesFile = new File(plugin.getDataFolder(), "kit-boxes.yml");
        keyId = new NamespacedKey(plugin, "kit_key"); boxId = new NamespacedKey(plugin, "kit_box");
        labels = new KitLabels(plugin, this);
        if (kitsFile.exists()) try { kits.load(kitsFile); } catch (Exception e) { plugin.getLogger().warning("kits.yml illisible: " + e.getMessage()); }
        if (boxesFile.exists()) try { boxes.load(boxesFile); } catch (Exception e) { plugin.getLogger().warning("kit-boxes.yml illisible: " + e.getMessage()); }
    }
    public KitLabels labels() { return labels; }
    public Collection<Kit> all() {
        List<Kit> out = new ArrayList<>(); ConfigurationSection s = kits.getConfigurationSection("kits");
        if (s == null) return out;
        for (String id : s.getKeys(false)) out.add(get(id));
        return out;
    }
    public Kit get(String id) {
        String path = "kits." + id;
        if (!kits.contains(path)) return null;
        List<?> raw = kits.getList(path + ".contents", List.of());
        List<ItemStack> items = new ArrayList<>();
        for (Object o : raw) if (o instanceof ItemStack it) items.add(it.clone());
        return new Kit(id, kits.getString(path + ".name", id), kits.getLong(path + ".price"), items.toArray(ItemStack[]::new),
                blankToNull(kits.getString(path + ".permission")), blankToNull(kits.getString(path + ".label")),
                keyModelOf(id));
    }
    /** "gold" / "key_gold" / "bountycraft:key_gold" -> "bountycraft:key_gold" (null si vide). */
    public static String normalizeModel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains(":")) return s;
        return "bountycraft:" + (s.startsWith("key_") ? s : "key_" + s);
    }
    private String keyModelOf(String id) {
        String own = normalizeModel(kits.getString("kits." + id + ".key-model"));
        return own != null ? own : normalizeModel(plugin.getConfig().getString("kit-keys." + id));
    }
    /** Définit la texture de clé d'un kit (null = retour à la valeur par défaut de config.yml). */
    public boolean setKeyModel(String id, String model) {
        if (!kits.contains("kits." + id)) return false;
        kits.set("kits." + id + ".key-model", normalizeModel(model));
        save(kits, kitsFile);
        return true;
    }
    /** Positions de toutes les boîtes enregistrées (mondes chargés uniquement) -> id du kit. */
    public Map<Location, String> allBoxes() {
        Map<Location, String> out = new LinkedHashMap<>();
        for (String k : boxes.getKeys(false)) {
            String[] p = k.split(",");
            World w = p.length == 4 ? Bukkit.getWorld(p[0]) : null;
            if (w == null) continue;
            try { out.put(new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])), boxes.getString(k)); }
            catch (NumberFormatException ignored) { }
        }
        return out;
    }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Retrouve l'identifiant d'un kit à partir de ce que tape l'admin (id ou nom), ou null s'il n'existe pas. */
    public String resolve(String input) {
        if (input == null) return null;
        String id = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return kits.contains("kits." + id) ? id : null;
    }
    public List<String> ids() {
        ConfigurationSection s = kits.getConfigurationSection("kits");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    /** Le joueur a-t-il le droit d'acheter la clé / d'ouvrir la boîte de ce kit ? (les admins passent toujours) */
    public boolean canUse(Player p, Kit kit) {
        return !kit.restricted() || p.hasPermission(kit.permission()) || p.hasPermission("bountycraft.admin");
    }
    public void deny(Player p, Kit kit) {
        Text.send(p, kit.label() != null
                ? "<red>Ce kit est réservé au grade <gold>" + kit.label() + "<red>."
                : "<red>Ce kit est réservé à certains joueurs (permission requise).");
    }
    /** Définit (ou retire avec permission=null) la permission d'un kit. Renvoie false si le kit n'existe pas. */
    public boolean setPermission(String id, String permission, String label) {
        if (!kits.contains("kits." + id)) return false;
        permission = blankToNull(permission);
        kits.set("kits." + id + ".permission", permission);
        kits.set("kits." + id + ".label", permission == null ? null : blankToNull(label));
        save(kits, kitsFile);
        labels.refreshKit(id);
        return true;
    }
    /** Contenu de la shulker tenue en main principale ; null (avec message) si ce n'est pas une shulker, ou si elle est vide. */
    private List<ItemStack> heldShulkerContents(Player admin) {
        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held == null || !(held.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox shulker)) {
            Text.send(admin, "<red>Vous devez tenir une shulker remplie dans votre main principale."); return null;
        }
        List<ItemStack> contents = Arrays.stream(shulker.getInventory().getContents())
                .filter(it -> it != null && !it.getType().isAir()).map(ItemStack::clone).toList();
        if (contents.isEmpty()) { Text.send(admin, "<red>Cette shulker est vide : un kit vide ne donnerait rien."); return null; }
        return contents;
    }
    /** Remplace le contenu d'un kit existant par celui de la shulker tenue en main (prix, nom, permission et boîtes inchangés). */
    public boolean update(Player admin, String id) {
        if (!kits.contains("kits." + id)) { Text.send(admin, "<red>Kit introuvable."); return false; }
        List<ItemStack> contents = heldShulkerContents(admin);
        if (contents == null) return false;
        kits.set("kits." + id + ".contents", contents);
        save(kits, kitsFile);
        Text.send(admin, "<green>Contenu du kit <white>" + kits.getString("kits." + id + ".name", id) + "<green> remplacé (<white>" + contents.size() + "<green> piles).");
        return true;
    }
    public boolean create(Player admin, String name, long price) {
        List<ItemStack> contents = heldShulkerContents(admin);
        if (contents == null) return false;
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (id.isBlank() || price < 0 || kits.contains("kits." + id)) { Text.send(admin, "<red>Nom déjà utilisé ou prix invalide."); return false; }
        Material[] colors = {Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX,
                Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
                Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
                Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX};
        kits.set("kits." + id + ".name", name); kits.set("kits." + id + ".price", price);
        kits.set("kits." + id + ".contents", contents);
        kits.set("kits." + id + ".box-material", colors[Math.max(0, all().size() - 1) % colors.length].name());
        save(kits, kitsFile);
        Items.give(admin, boxItem(id, name));
        Text.send(admin, "<green>Kit <white>" + name + "<green> créé. Sa clé est disponible chez le Forgeron.");
        return true;
    }
    public ItemStack boxItem(String id, String name) {
        Material material = Material.matchMaterial(kits.getString("kits." + id + ".box-material", "PURPLE_SHULKER_BOX"));
        if (material == null || !material.name().endsWith("_SHULKER_BOX")) material = Material.PURPLE_SHULKER_BOX;
        ItemStack box = new ItemStack(material); ItemMeta bm = box.getItemMeta();
        bm.displayName(Text.item("<light_purple><bold>Boîte du kit " + name));
        bm.lore(Text.item(List.of("<gray>À placer au spawn", "<dark_gray>Ouvrable avec sa clé dédiée")));
        bm.getPersistentDataContainer().set(boxId, PersistentDataType.STRING, id); box.setItemMeta(bm); return box;
    }
    public ItemStack key(Kit kit) {
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK); ItemMeta m = it.getItemMeta();
        m.displayName(Text.item("<gold><bold>Clé du kit " + kit.name()));
        NamespacedKey model = kit.keyModel() == null ? null : NamespacedKey.fromString(kit.keyModel());
        if (model != null) m.setItemModel(model);
        List<String> lore = new ArrayList<>(); lore.add("<gray>Prix : <gold>" + Text.money(kit.price())); lore.add(""); lore.add("<yellow>Contenu :");
        for (ItemStack content : kit.contents()) if (content != null && !content.getType().isAir()) {
            String label = content.hasItemMeta() && content.getItemMeta().displayName() != null
                    ? Text.plain(content.getItemMeta().displayName()) : pretty(content.getType().name());
            lore.add("<gray>• <white>" + content.getAmount() + "x " + label);
        }
        if (kit.contents().length == 0) lore.add("<red>(kit vide pour le moment)");
        lore.add("");
        if (kit.restricted()) lore.add("<red>Réservé au grade <gold>" + (kit.label() != null ? kit.label() : "autorisé"));
        lore.add("<green>Clic droit sur la boîte correspondante"); m.lore(Text.item(lore));
        m.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, kit.id()); it.setItemMeta(m); return it;
    }
    /** Clé telle qu'affichée dans la boutique du Forgeron : avec un avertissement si le joueur n'y a pas accès. */
    public ItemStack shopItem(Player viewer, Kit kit) {
        ItemStack it = key(kit);
        if (canUse(viewer, kit)) return it;
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        lore.add(Text.item("<red><bold>Vous n'avez pas accès à ce kit"));
        m.lore(lore); it.setItemMeta(m); return it;
    }
    public boolean buy(Player p, Kit kit) {
        if (!canUse(p, kit)) { deny(p, kit); return false; }
        if (kit.contents().length == 0) { Text.send(p, "<red>Ce kit est vide pour le moment : il n'est pas en vente."); return false; }
        if (!plugin.economy().withdraw(p.getUniqueId(), kit.price())) { Text.send(p, "<red>Fonds insuffisants."); return false; }
        Items.give(p, key(kit)); Text.send(p, "<green>Clé du kit " + kit.name() + " achetée."); return true;
    }
    public String boxId(ItemStack it) { return it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(boxId, PersistentDataType.STRING); }
    public String keyId(ItemStack it) { return it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING); }
    public void registerBox(Block block, String id) { boxes.set(locationKey(block.getLocation()), id); save(boxes, boxesFile); labels.refresh(block.getLocation()); }
    public String boxAt(Location loc) { return boxes.getString(locationKey(loc.getBlock().getLocation())); }
    public void removeBox(Location loc) { boxes.set(locationKey(loc.getBlock().getLocation()), null); save(boxes, boxesFile); labels.remove(loc.getBlock().getLocation()); }
    public void open(Player player, String id) {
        Kit kit = get(id); if (kit == null) { Text.send(player, "<red>Ce kit n'existe plus."); return; }
        for (ItemStack item : kit.contents()) if (item != null && !item.getType().isAir()) Items.give(player, item.clone());
        Text.send(player, "<green>Kit <white>" + kit.name() + "<green> récupéré !");
    }
    static String locationKey(Location l) { return l.getWorld().getName()+","+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ(); }
    /** Fichiers déjà copiés en .bak depuis le démarrage : on ne garde que la version d'avant la première écriture. */
    private final Set<File> backedUp = new HashSet<>();
    private void save(YamlConfiguration y, File f) {
        if (f.exists() && backedUp.add(f)) {
            try {
                java.nio.file.Files.copy(f.toPath(), new File(f.getParentFile(), f.getName() + ".bak").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { plugin.getLogger().warning("Copie de secours impossible pour " + f.getName() + ": " + e.getMessage()); }
        }
        try { y.save(f); } catch (IOException e) { plugin.getLogger().severe("Sauvegarde impossible: " + e.getMessage()); }
    }
    private String pretty(String s) { return s.toLowerCase(Locale.ROOT).replace('_',' '); }
}
