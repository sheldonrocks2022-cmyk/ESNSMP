package com.esn.smp;

import com.esn.smp.command.ESNSpawnCommand;
import com.esn.smp.command.SetSpawnCommand;
import com.esn.smp.command.SpawnCommand;
import com.esn.smp.listener.SpawnListener;
import com.esn.smp.listener.SpawnProtectionListener;
import com.esn.smp.spawn.SpawnManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ESNSMPPlugin extends JavaPlugin {
    private SpawnManager spawnManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.spawnManager = new SpawnManager(this);

        registerCommand("spawn", new SpawnCommand(spawnManager));
        registerCommand("setspawn", new SetSpawnCommand(spawnManager));
        registerCommand("esnspawn", new ESNSpawnCommand(spawnManager));

        getServer().getPluginManager().registerEvents(new SpawnListener(this, spawnManager), this);
        getServer().getPluginManager().registerEvents(new SpawnProtectionListener(spawnManager), this);

        getServer().getScheduler().runTask(this, () -> {
            try {
                spawnManager.ensureConfigured();
                if (getConfig().getBoolean("spawn.build-on-first-start", true)
                        && !getConfig().getBoolean("spawn.generated", false)) {
                    getLogger().info("First start detected. Building ESN SMP spawn...");
                    spawnManager.buildSpawn(getServer().getConsoleSender());
                }
            } catch (Exception ex) {
                getLogger().severe("ESN SMP spawn initialization failed safely: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        getLogger().info("ESNSMP v" + getDescription().getVersion() + " enabled.");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command in plugin.yml: " + name);
        command.setExecutor(executor);
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }
}
