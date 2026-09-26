package com.esn.smp.gameplay;

import com.esn.smp.data.ESNDataStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class ESNScoreboard {
    private static final String[] ENTRIES = {
            ChatColor.BLACK.toString(),
            ChatColor.DARK_BLUE.toString(),
            ChatColor.DARK_GREEN.toString(),
            ChatColor.DARK_AQUA.toString(),
            ChatColor.DARK_RED.toString()
    };

    private final JavaPlugin plugin;
    private final ESNDataStore data;

    public ESNScoreboard(JavaPlugin plugin, ESNDataStore data) {
        this.plugin = plugin;
        this.data = data;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, 40L, 100L);
    }

    private void update() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        int online = Bukkit.getOnlinePlayers().size();

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Scoreboard board = player.getScoreboard();
                if (board == null || board == manager.getMainScoreboard()) {
                    board = manager.getNewScoreboard();
                    player.setScoreboard(board);
                }

                Objective objective = board.getObjective("esn");
                if (objective == null) {
                    objective = board.registerNewObjective(
                            "esn", "dummy", ChatColor.GOLD + "" + ChatColor.BOLD + "ESN SMP");
                    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                }

                setLine(board, objective, 0, 5, ChatColor.GRAY + "play.esn");
                setLine(board, objective, 1, 4,
                        ChatColor.YELLOW + "Rank: " + ChatColor.WHITE +
                                (player.hasPermission("esnsmp.owner") ? "Owner" : "Member"));
                setLine(board, objective, 2, 3,
                        ChatColor.YELLOW + "Coins: " + ChatColor.WHITE + data.getBalance(player));
                setLine(board, objective, 3, 2,
                        ChatColor.YELLOW + "Kills: " + ChatColor.WHITE +
                                player.getStatistic(Statistic.PLAYER_KILLS));
                setLine(board, objective, 4, 1,
                        ChatColor.YELLOW + "Online: " + ChatColor.WHITE + online);
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "Scoreboard update failed for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void setLine(Scoreboard board, Objective objective, int index, int score, String text) {
        String teamName = "esn_line_" + index;
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);

        String entry = ENTRIES[index];
        if (!team.hasEntry(entry)) team.addEntry(entry);
        if (!text.equals(team.getPrefix())) team.setPrefix(text);

        objective.getScore(entry).setScore(score);
    }
}
