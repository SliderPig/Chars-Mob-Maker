package com.example.charmobmaker;

import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.model.Design;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class MobMakerCommand {
    private MobMakerCommand() {}

    private static RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(DesignStore.ids(), b));
    }

    // Anyone can type /mobmaker and be told politely, so the check is here rather than hiding the command.
    private static boolean allowed(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return Access.canEdit(p);
        return s.hasPermission(2);
    }

    private static int refuse(CommandSourceStack s) {
        s.sendFailure(Component.literal(Access.refusal()));
        return 0;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mobmaker")
                .executes(c -> open(c, ""))
                .then(Commands.literal("open").then(nameArg().executes(c -> open(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("list").executes(MobMakerCommand::list))
                .then(Commands.literal("spawn").then(nameArg()
                        .executes(c -> spawn(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 256)).executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("give").then(nameArg()
                        .executes(c -> give(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(c -> give(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("delete").then(nameArg().executes(MobMakerCommand::delete)))
                .then(Commands.literal("play").then(Commands.argument("mobs", EntityArgument.entities()).then(Commands.argument("clip", StringArgumentType.word()).executes(MobMakerCommand::play))))
                .then(Commands.literal("reload").executes(MobMakerCommand::reload)));
    }

    private static int open(CommandContext<CommandSourceStack> c, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Access.canEdit(p)) return refuse(c.getSource());
        if (!id.isEmpty() && !Designs.SERVER.containsKey(id)) {
            c.getSource().sendFailure(Component.literal("There's no mob design called " + id));
            return 0;
        }
        Net.sendOpen(p, id);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<String> ids = DesignStore.ids();
        c.getSource().sendSuccess(() -> Component.literal(ids.size() + " mob designs: " + String.join(", ", ids)), false);
        return ids.size();
    }

    private static Design find(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "name");
        Design d = Designs.SERVER.get(id);
        if (d == null) c.getSource().sendFailure(Component.literal("There's no mob design called " + id));
        return d;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, int count) {
        if (!allowed(c.getSource())) return refuse(c.getSource());
        Design d = find(c);
        if (d == null) return 0;
        if (d.type != Design.Type.MOB) {
            c.getSource().sendFailure(Component.literal(d.name + " is a " + d.type.label.toLowerCase(java.util.Locale.ROOT) + ", not a mob. Try /mobmaker give"));
            return 0;
        }
        int n = Math.min(count, Config.MAX_SPAWN_COUNT.get());
        ServerLevel level = c.getSource().getLevel();
        Vec3 at = c.getSource().getPosition();
        for (int i = 0; i < n; i++) CustomMob.spawnNear(level, at, d.id, 1.5 + i * 0.3);
        c.getSource().sendSuccess(() -> Component.literal("Spawned " + n + " x " + d.name), true);
        return n;
    }

    private static int give(CommandContext<CommandSourceStack> c, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!allowed(c.getSource())) return refuse(c.getSource());
        Design d = find(c);
        if (d == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack egg = DesignStacks.of(d);
        if (egg.isEmpty()) {
            c.getSource().sendFailure(Component.literal("A screen isn't something you can carry: a block opens it."));
            return 0;
        }
        egg.setCount(Math.min(count, egg.getMaxStackSize()));
        if (!p.getInventory().add(egg)) p.drop(egg, false);
        c.getSource().sendSuccess(() -> Component.literal("Gave " + count + " x " + egg.getHoverName().getString()), true);
        return count;
    }

    private static int delete(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p) ? !c.getSource().hasPermission(2) : !Access.canChange(p, Designs.SERVER.get(StringArgumentType.getString(c, "name")))) return refuse(c.getSource());
        Design d = find(c);
        if (d == null) return 0;
        boolean done = DesignStore.delete(d.id);
        c.getSource().sendSuccess(() -> Component.literal(done ? "Deleted " + d.name : "Couldn't delete " + d.name), true);
        return done ? 1 : 0;
    }

    private static int play(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!allowed(c.getSource())) return refuse(c.getSource());
        String clip = StringArgumentType.getString(c, "clip");
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "mobs")) {
            if (e instanceof CustomMob) {
                Net.sendPlay(e, clip);
                n++;
            }
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal(count + " mobs play " + clip), false);
        return n;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        if (!c.getSource().hasPermission(2)) return refuse(c.getSource());
        DesignStore.reload();
        c.getSource().sendSuccess(() -> Component.literal("Read " + DesignStore.ids().size() + " mob designs again"), true);
        return 1;
    }
}
