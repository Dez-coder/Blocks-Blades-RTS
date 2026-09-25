package ru.dez.client.camera;

import net.minecraft.world.item.Item;

public class RTSBuilding {

    public final String id;
    public final String name;
    public final Item icon;
    public final String requires;
    public final RTSSchematic schematic;

    public boolean unlocked;

    /** True for mine/lumber upgrades: hidden from the build panel, shown as a
     *  dedicated upgrade button above the profession button. */
    public final boolean upgrade;

    public final int costWood;
    public final int costFood;
    public final int costOre;
    public final int work;

    public RTSBuilding(String id, String name, Item icon, String requires, RTSSchematic schematic, int costWood, int costFood, int costOre) {
        this(id, name, icon, requires, schematic, costWood, costFood, costOre, false);
    }

    public RTSBuilding(String id, String name, Item icon, String requires, RTSSchematic schematic, int costWood, int costFood, int costOre, boolean upgrade) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.requires = requires;
        this.schematic = schematic;
        this.costWood = costWood;
        this.costFood = costFood;
        this.costOre = costOre;
        this.upgrade = upgrade;
        this.work = schematic == null ? 100 : schematic.entries.size() * 5;
        this.unlocked = requires == null;
    }
}