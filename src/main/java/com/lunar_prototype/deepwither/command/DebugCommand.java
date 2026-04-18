package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.commands.DeepwitherCommand;
import com.lunar_prototype.deepwither.api.playerdata.IPlayerComponent;
import com.lunar_prototype.deepwither.core.PlayerCache;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
                .then(literal("dump")
                    .executes(DebugCommand::dumpPlayer)
                )
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

    private static int dumpPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player target = resolve(context).getFirst();
        PlayerCache cache = Deepwither.getInstance().getCacheManager().getCache(target.getUniqueId());
        
        CommandSourceStack source = context.getSource();
        source.getSender().sendMessage(Component.text("=== " + target.getName() + " のデータダンプ ===", NamedTextColor.AQUA));
        
        if (cache == null) {
            source.getSender().sendMessage(Component.text("キャッシュが見つかりません。", NamedTextColor.RED));
            return 1;
        }

        for (IPlayerComponent component : cache.getAllComponents()) {
            String className = component.getClass().getSimpleName();
            source.getSender().sendMessage(Component.text("[" + className + "] ", NamedTextColor.YELLOW)
                    .append(Component.text(component.toDebugSummary(), NamedTextColor.WHITE)));
        }
        
        source.getSender().sendMessage(Component.text("===============================", NamedTextColor.AQUA));
        return 1;
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