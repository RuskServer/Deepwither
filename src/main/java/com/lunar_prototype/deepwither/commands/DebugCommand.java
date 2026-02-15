package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.commands.DeepwitherCommand;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.*;

@NullMarked
public class DebugCommand implements DeepwitherCommand {
    @Override
    public String name() {
        return "dw.debug";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(name())
            .requires(source -> !(source.getSender() instanceof Player player) || player.hasPermission("deepwither.admin"))
            .then(argument("player", ArgumentTypes.player())
                .then(literal("heal")
                    .then(literal("hp").executes(DebugCommand::healHp))
                    .then(literal("mp").executes(DebugCommand::healMp))
                    .executes(context -> {
                        healHp(context);
                        healMp(context);
                        return 1;
                    })
                )
                .then(literal("level")
                    .then(literal("reset")
                        .executes(DebugCommand::resetLv)
                    )
                    .then(literal("xp")
                        .then(argument("xp", DoubleArgumentType.doubleArg(0))
                            .executes(DebugCommand::addXp)
                        )
                    )
                )
            )
            .build();
    }

    private static int healHp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = resolve(context).getFirst();
        var sm = Deepwither.getInstance().getStatManager();
        sm.heal(player, sm.getActualMaxHealth(player));
        return 1;
    }

    private static int healMp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = resolve(context).getFirst();
        var mm = Deepwither.getInstance().getManaManager();
        var id = player.getUniqueId();
        var md = mm.get(id);
        md.setCurrentMana(md.getMaxMana());
        mm.set(id, md);
        return 1;
    }

    private static int resetLv(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Deepwither.getInstance().getLevelManager().resetLevel(resolve(context).getFirst());
        return 1;
    }

    private static int addXp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Deepwither.getInstance().getLevelManager().addExp(resolve(context).getFirst(), DoubleArgumentType.getDouble(context, "xp"));
        return 1;
    }

    private static List<Player> resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
    }
}
