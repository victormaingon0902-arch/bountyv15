package fr.bountycraft.elixir;

import java.util.Locale;

/** Les 8 élixirs de l'Alchimiste. L'id sert aussi de nom de texture : bountycraft:potion_<id>. */
public enum ElixirType {
    INVISIBILITY("invisibility", "Potion d'Invisibilité", "<#b8c4d9>", "Invisibilité", true),
    WARDEN("warden", "Potion du Warden", "<#1fb5a6>", "Warden", true),
    HEAL("heal", "Potion de Soin", "<#e0405a>", "Soin", true),
    EMERALD("emerald", "Potion d'Émeraude", "<#17b860>", "Émeraude", false),
    WIND("wind", "Potion du Vent", "<#9fe3d9>", "Vent", true),
    END("end", "Potion de l'End", "<#a24be0>", "End", true),
    FIRE("fire", "Potion de Feu", "<#ff7a1a>", "Feu", true),
    WATER("water", "Potion d'Eau", "<#2f6fe0>", "Eau", true);

    private final String id;
    private final String displayName;
    private final String color;
    private final String shortName;
    private final boolean hasSkill;

    ElixirType(String id, String displayName, String color, String shortName, boolean hasSkill) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.shortName = shortName;
        this.hasSkill = hasSkill;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    /** Balise MiniMessage de couleur, ex. "<#ff7a1a>". */
    public String color() { return color; }
    public String shortName() { return shortName; }
    public boolean hasSkill() { return hasSkill; }

    public static ElixirType byId(String id) {
        if (id == null) return null;
        String low = id.toLowerCase(Locale.ROOT);
        for (ElixirType t : values()) {
            if (t.id.equals(low)) return t;
        }
        return null;
    }
}
