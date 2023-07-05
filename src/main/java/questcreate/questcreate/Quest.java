package questcreate.questcreate;

import org.bukkit.Material;

public class Quest {
    private String name;
    private Material item;
    private int amount;
    public Quest(String name, Material item, int amount) {
        this.name = name;
        this.item = item;
        this.amount = amount;
    }
    public String getName() {
        return name;
    }

    public Material getItem() {
        return item;
    }

    public int getAmount() {
        return amount;
    }
}

