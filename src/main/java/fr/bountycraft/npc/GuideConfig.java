package fr.bountycraft.npc;

import fr.bountycraft.BountyCraft;
import fr.bountycraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Contenu du PNJ Guide, lu depuis guide.yml. */
public final class GuideConfig {
    private final BountyCraft plugin;
    private String discord = "";
    private String website = "";
    private final List<Component> rules = new ArrayList<>();
    private final List<Component> howto = new ArrayList<>();

    public GuideConfig(BountyCraft plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "guide.yml"));
        discord = y.getString("discord", "");
        website = y.getString("website", "");
        rules.clear();
        howto.clear();
        for (String s : y.getStringList("rules-pages")) rules.add(Text.mm(s.stripTrailing()));
        for (String s : y.getStringList("howto-pages")) howto.add(Text.mm(s.stripTrailing()));
    }

    public String discord() { return discord; }
    public String website() { return website; }
    public List<Component> rulesPages() { return rules; }
    public List<Component> howtoPages() { return howto; }
}
