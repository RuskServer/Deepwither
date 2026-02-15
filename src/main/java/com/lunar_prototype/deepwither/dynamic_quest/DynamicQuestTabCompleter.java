package com.lunar_prototype.deepwither.dynamic_quest;

import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DynamicQuestTabCompleter implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("accept", "decline", "spawn", "reload", "status", "addloc").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("addloc")) {
            return Arrays.stream(QuestType.values())
                    .map(Enum::name)
                    .filter(s -> s.startsWith(args[1].toUpperCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("addloc")) {
            return Arrays.asList("1", "2");
        }

        return new ArrayList<>();
    }
}
