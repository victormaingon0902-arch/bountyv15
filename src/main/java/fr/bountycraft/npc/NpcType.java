package fr.bountycraft.npc;

import java.util.Locale;

public enum NpcType {
    HUNTER("hunter", "<dark_red><bold>Hunter", "<gray>Chasseur de primes"),
    BLACKSMITH("blacksmith", "<gold><bold>Forgeron", "<gray>Armes & outils légendaires"),
    ALCHEMIST("alchemist", "<light_purple><bold>Alchimiste", "<gray>Élixirs spéciaux"),
    GUIDE("guide", "<aqua><bold>Guide", "<gray>Infos, règles & aide"),
    QUEST("quest", "<yellow><bold>Quêtier", "<gray>Une quête par heure"),
    MERCHANT("merchant", "<green><bold>Marchand", "<gray>Rachète vos récoltes");

    private final String configKey;
    private final String defaultName;
    private final String defaultDescription;

    NpcType(String configKey, String defaultName, String defaultDescription) {
        this.configKey = configKey;
        this.defaultName = defaultName;
        this.defaultDescription = defaultDescription;
    }

    public String configKey() { return configKey; }
    public String defaultName() { return defaultName; }
    public String defaultDescription() { return defaultDescription; }

    public static NpcType parse(String s) {
        if (s == null) return null;
        String low = s.toLowerCase(Locale.ROOT);
        for (NpcType t : values()) {
            if (t.name().equalsIgnoreCase(low) || t.configKey.equals(low)) return t;
        }
        // alias français
        return switch (low) {
            case "forgeron" -> BLACKSMITH;
            case "alchimiste" -> ALCHEMIST;
            case "quete", "quête", "quetier", "quêtier", "quests" -> QUEST;
            case "marchand", "vendeur", "market", "shop" -> MERCHANT;
            default -> null;
        };
    }
}
