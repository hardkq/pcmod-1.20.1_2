package ru.pcmod.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Команды операторов (Brigadier; регистрируются в RegisterCommandsEvent, см. PCMod):
 * /pcbank balance|give|take|set &lt;игрок&gt; [сумма]   - счета игроков (онлайн)
 * /pcshop reload|refresh|status                     - каталог магазина
 */
public final class CommandBank {
    private CommandBank() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pcbank").requires(s -> s.hasPermission(2))
                .then(Commands.literal("balance")
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> bank(c, "balance", 0L))))
                .then(amountOp("give")).then(amountOp("take")).then(amountOp("set")));
        d.register(Commands.literal("pcshop").requires(s -> s.hasPermission(2))
                .executes(c -> shop(c, "status"))
                .then(Commands.literal("reload").executes(c -> shop(c, "reload")))
                .then(Commands.literal("refresh").executes(c -> shop(c, "refresh")))
                .then(Commands.literal("status").executes(c -> shop(c, "status"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> amountOp(String op) {
        return Commands.literal(op).then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                        .executes(c -> bank(c, op, LongArgumentType.getLong(c, "amount")))));
    }

    private static void say(CommandContext<CommandSourceStack> c, String t) {
        c.getSource().sendSuccess(() -> Component.literal(t), false);
    }

    private static int bank(CommandContext<CommandSourceStack> c, String op, long v) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        Bank b = Bank.get();
        if (b == null) {
            say(c, "Player not found (must be online): " + p.getGameProfile().getName());
            return 0;
        }
        String id = p.getUUID().toString();
        String name = p.getGameProfile().getName();
        b.rename(id, name);
        if (op.equals("give")) {
            b.setBalance(id, b.balance(id) + v);
            b.log(id, "+" + Bank.fmt(v) + " admin");
        } else if (op.equals("take")) {
            b.setBalance(id, b.balance(id) - v);
            b.log(id, "-" + Bank.fmt(v) + " admin");
        } else if (op.equals("set")) {
            b.setBalance(id, v);
            b.log(id, "= " + Bank.fmt(v) + " admin");
        }
        say(c, name + ": " + Bank.fmt(b.balance(id)));
        return 1;
    }

    private static int shop(CommandContext<CommandSourceStack> c, String op) {
        if (op.equals("reload")) {
            Shop.load();
            say(c, "Shop config reloaded: " + Shop.poolSize() + " offers in the pool");
        } else if (op.equals("refresh")) {
            Shop.reroll();
            say(c, "Catalog refreshed, purchase limits reset");
        } else {
            say(c, "Pool: " + Shop.poolSize() + ", catalog: " + Shop.catalog().size()
                    + " offers, next change in " + Shop.secondsLeft() + " s");
        }
        return 1;
    }
}
