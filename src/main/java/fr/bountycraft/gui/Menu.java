package fr.bountycraft.gui;

import fr.bountycraft.BountyCraft;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Base des menus (coffre GUI) du plugin. */
public abstract class Menu implements InventoryHolder {
    protected final BountyCraft plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(BountyCraft plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    protected void fill(ItemStack filler, int from, int toInclusive) {
        for (int i = from; i <= toInclusive; i++) inventory.setItem(i, filler);
    }

    /** Exécute une action au tick suivant (utile pour ouvrir un autre menu depuis un clic). */
    protected void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    void click(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
