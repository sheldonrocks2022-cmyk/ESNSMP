package com.esn.smp.spawn;

import org.bukkit.Material;

public enum HubService {
    AUCTION("Auction House", 40, 34, Material.GOLD_BLOCK),
    SHOP("Server Shop", -40, 34, Material.EMERALD_BLOCK),
    CRATES("Crates", 20, 54, Material.AMETHYST_BLOCK),
    QUESTS("Quests", -20, 54, Material.LAPIS_BLOCK),
    WARPS("Warps", 0, 58, Material.CRYING_OBSIDIAN),
    LEADERBOARDS("Leaderboards", 55, 0, Material.DIAMOND_BLOCK),
    INFO("Info & Rules", -55, 0, Material.QUARTZ_BLOCK);

    private final String displayName;
    private final int offsetX;
    private final int offsetZ;
    private final Material coreMaterial;

    HubService(String displayName, int offsetX, int offsetZ, Material coreMaterial) {
        this.displayName = displayName;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.coreMaterial = coreMaterial;
    }

    public String displayName() {
        return displayName;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetZ() {
        return offsetZ;
    }

    public Material coreMaterial() {
        return coreMaterial;
    }
}
