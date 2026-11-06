package fr.bountycraft;

import fr.bountycraft.admin.ResetManager;
import fr.bountycraft.command.AdminCommand;
import fr.bountycraft.command.ContractCommand;
import fr.bountycraft.command.EconomyCommands;
import fr.bountycraft.command.HuntCommand;
import fr.bountycraft.command.NpcCommand;
import fr.bountycraft.economy.EconomyManager;
import fr.bountycraft.elixir.ElixirManager;
import fr.bountycraft.gui.MenuListener;
import fr.bountycraft.hunter.ContractManager;
import fr.bountycraft.hunter.HuntManager;
import fr.bountycraft.listener.CoreListener;
import fr.bountycraft.listener.ElixirListener;
import fr.bountycraft.listener.NpcListener;
import fr.bountycraft.listener.ToolListener;
import fr.bountycraft.market.MarketManager;
import fr.bountycraft.npc.GuideConfig;
import fr.bountycraft.npc.NpcManager;
import fr.bountycraft.quest.QuestManager;
import fr.bountycraft.smith.ForgeManager;
import fr.bountycraft.smith.KitManager;
import fr.bountycraft.command.KitCommand;
import fr.bountycraft.listener.KitListener;
import fr.bountycraft.util.Keys;
import fr.bountycraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * BountyCraft : économie, primes (PNJ Hunter), chasses à l'homme (/hunt),
 * forgeron (boutique pilotée par forge.yml), alchimiste (élixirs) et guide.
 */
public final class BountyCraft extends JavaPlugin {
    private EconomyManager economy;
    private ContractManager contracts;
    private HuntManager hunts;
    private ForgeManager forge;
    private KitManager kits;
    private ElixirManager elixirs;
    private NpcManager npcs;
    private GuideConfig guide;
    private QuestManager quests;
    private MarketManager market;
    private ResetManager resets;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveIfMissing("forge.yml");
        saveIfMissing("guide.yml");
        saveIfMissing("quests.yml");
        saveIfMissing("sell.yml");
        Keys.init(this);
        applyTextConfig();

        economy = new EconomyManager(this);
        contracts = new ContractManager(this);
        hunts = new HuntManager(this);
        forge = new ForgeManager(this);
        kits = new KitManager(this);
        elixirs = new ElixirManager(this);
        npcs = new NpcManager(this);
        guide = new GuideConfig(this);
        quests = new QuestManager(this);
        market = new MarketManager(this);
        resets = new ResetManager(this);

        Bukkit.getPluginManager().registerEvents(new CoreListener(this), this);
        Bukkit.getPluginManager().registerEvents(new NpcListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ToolListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ElixirListener(this), this);
        Bukkit.getPluginManager().registerEvents(new KitListener(this), this);
        Bukkit.getPluginManager().registerEvents(resets, this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);

        EconomyCommands eco = new EconomyCommands(this);
        register("bank", eco);
        register("pay", eco);
        register("baltop", eco);
        register("eco", eco);
        register("hunt", new HuntCommand(this));
        register("contract", new ContractCommand(this));
        register("npc", new NpcCommand(this));
        register("bountycraft", new AdminCommand(this));
        register("kit", new KitCommand(this));

        elixirs.start();
        Bukkit.getScheduler().runTaskTimer(this, this::tickSecond, 20L, 20L);
        Bukkit.getScheduler().runTask(this, kits.labels()::refreshAll); // textes au-dessus des boîtes de kit

        // joueurs déjà connectés (rechargement à chaud)
        for (Player p : Bukkit.getOnlinePlayers()) {
            economy.of(p);
            elixirs.load(p);
        }
        getLogger().info("BountyCraft activé.");
    }

    @Override
    public void onDisable() {
        if (kits != null) try { kits.labels().removeAll(); } catch (Exception ignored) { }
        if (hunts != null) hunts.shutdown();
        if (economy != null) economy.saveNow();
    }

    public void reloadAll() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        applyTextConfig();
        forge.load();
        guide.load();
        quests.load();
        market.load();
        elixirs.reload();
        kits.labels().refreshAll();
    }

    private void applyTextConfig() {
        Text.configure(getConfig().getString("economy.currency-symbol", "$"),
                getConfig().getString("prefix", "<dark_gray>[<gold>Bounty<dark_gray>] <gray>"));
    }

    private void saveIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("Commande absente de plugin.yml : " + name);
            return;
        }
        c.setExecutor(executor);
        if (executor instanceof TabCompleter tc) c.setTabCompleter(tc);
    }

    /** Chaque seconde : contrats, chasse, action bar. */
    private void tickSecond() {
        contracts.tick();
        hunts.tick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String a = contracts.hud(p);
            String b = elixirs.hud(p);
            if (a == null && b == null) continue;
            String line = a == null ? b : (b == null ? a : a + " <dark_gray>| " + b);
            p.sendActionBar(Text.mm(line));
        }
    }

    public EconomyManager economy() { return economy; }
    public ContractManager contracts() { return contracts; }
    public HuntManager hunts() { return hunts; }
    public ForgeManager forge() { return forge; }
    public KitManager kits() { return kits; }
    public ElixirManager elixirs() { return elixirs; }
    public NpcManager npcs() { return npcs; }
    public GuideConfig guide() { return guide; }
    public QuestManager quests() { return quests; }
    public MarketManager market() { return market; }
    public ResetManager resets() { return resets; }
}
