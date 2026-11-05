package com.example.charmobmaker;

import com.example.charmobmaker.client.ClientNet;
import com.example.charmobmaker.entity.CustomMob;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.Ids;
import com.example.charmobmaker.model.Limits;
import com.example.charmobmaker.model.Packer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// All the messages. A packet from a player carries at most about 30 KB, so a design goes up in pieces; everything from a player gets checked again here.
public final class Net {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(CharMobMaker.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    public static final int PIECE = 30000;
    private static final int MAX_PIECES = Limits.MAX_DESIGN_GZ_BYTES / PIECE + 2;

    private Net() {}

    // ---- to the PC ----
    public record DesignPiece(String id, int index, int total, byte[] data) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 64);
            b.writeVarInt(index);
            b.writeVarInt(total);
            b.writeByteArray(data);
        }

        static DesignPiece read(FriendlyByteBuf b) {
            return new DesignPiece(b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readByteArray(PIECE + 16));
        }
    }

    public record DesignGone(String id) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 64);
        }

        static DesignGone read(FriendlyByteBuf b) {
            return new DesignGone(b.readUtf(64));
        }
    }

    public record ResetAll() {
    }

    public record OpenEditor(String id) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 64);
        }

        static OpenEditor read(FriendlyByteBuf b) {
            return new OpenEditor(b.readUtf(64));
        }
    }

    public record SaveReply(boolean ok, boolean wasSave, String id, String message) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(ok);
            b.writeBoolean(wasSave);
            b.writeUtf(id, 64);
            b.writeUtf(message, 256);
        }

        static SaveReply read(FriendlyByteBuf b) {
            return new SaveReply(b.readBoolean(), b.readBoolean(), b.readUtf(64), b.readUtf(256));
        }
    }

    public record PlayClip(int entity, String clip) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(entity);
            b.writeUtf(clip, 32);
        }

        static PlayClip read(FriendlyByteBuf b) {
            return new PlayClip(b.readVarInt(), b.readUtf(32));
        }
    }

    // ---- to the server ----
    public record UploadPiece(int index, int total, byte[] data) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(index);
            b.writeVarInt(total);
            b.writeByteArray(data);
        }

        static UploadPiece read(FriendlyByteBuf b) {
            return new UploadPiece(b.readVarInt(), b.readVarInt(), b.readByteArray(PIECE));
        }
    }

    public enum Act {OPEN, DELETE, SPAWN, EGG}

    public record Action(Act act, String id, int count) {
        void write(FriendlyByteBuf b) {
            b.writeEnum(act);
            b.writeUtf(id, 64);
            b.writeVarInt(count);
        }

        static Action read(FriendlyByteBuf b) {
            return new Action(b.readEnum(Act.class), b.readUtf(64), b.readVarInt());
        }
    }

    public static void register() {
        int i = 0;
        CHANNEL.messageBuilder(DesignPiece.class, i++, NetworkDirection.PLAY_TO_CLIENT).encoder(DesignPiece::write).decoder(DesignPiece::read)
                .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.piece(m))).add();
        CHANNEL.messageBuilder(DesignGone.class, i++, NetworkDirection.PLAY_TO_CLIENT).encoder(DesignGone::write).decoder(DesignGone::read)
                .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.gone(m.id()))).add();
        CHANNEL.messageBuilder(ResetAll.class, i++, NetworkDirection.PLAY_TO_CLIENT).encoder((m, b) -> {}).decoder(b -> new ResetAll())
                .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientNet::reset)).add();
        CHANNEL.messageBuilder(OpenEditor.class, i++, NetworkDirection.PLAY_TO_CLIENT).encoder(OpenEditor::write).decoder(OpenEditor::read)
                .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.open(m.id()))).add();
        CHANNEL.messageBuilder(SaveReply.class, i++, NetworkDirection.PLAY_TO_CLIENT).encoder(SaveReply::write).decoder(SaveReply::read)
                .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.saved(m))).add();
        CHANNEL.messageBuilder(PlayClip.class, i++, NetworkDirection.PLAY_TO_CLIENT).encoder(PlayClip::write).decoder(PlayClip::read)
                .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.play(m))).add();
        CHANNEL.messageBuilder(UploadPiece.class, i++, NetworkDirection.PLAY_TO_SERVER).encoder(UploadPiece::write).decoder(UploadPiece::read)
                .consumerMainThread(Net::onUpload).add();
        CHANNEL.messageBuilder(Action.class, i++, NetworkDirection.PLAY_TO_SERVER).encoder(Action::write).decoder(Action::read)
                .consumerMainThread(Net::onAction).add();
    }

    // ---------- sending ----------

    public static void sendReset(ServerPlayer p) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ResetAll());
    }

    public static void sendDesign(ServerPlayer p, String id, byte[] packed) {
        List<byte[]> pieces = Packer.split(packed, PIECE);
        for (int i = 0; i < pieces.size(); i++) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new DesignPiece(id, i, pieces.size(), pieces.get(i)));
        }
    }

    public static void sendGone(String id) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new DesignGone(id));
    }

    public static void sendOpen(ServerPlayer p, String id) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new OpenEditor(id));
    }

    public static void sendPlay(Entity mob, String clip) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new PlayClip(mob.getId(), clip));
    }

    public static void uploadToServer(String json) {
        List<byte[]> pieces = Packer.split(Packer.pack(json), PIECE);
        for (int i = 0; i < pieces.size(); i++) CHANNEL.sendToServer(new UploadPiece(i, pieces.size(), pieces.get(i)));
    }

    public static void action(Act act, String id, int count) {
        CHANNEL.sendToServer(new Action(act, id, count));
    }

    // ---------- receiving from players ----------

    private static final class Upload {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;
    }

    private static final Map<UUID, Upload> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SAVE = new ConcurrentHashMap<>();

    public static void forget(UUID player) {
        UPLOADS.remove(player);
        LAST_SAVE.remove(player);
    }

    private static void reply(ServerPlayer p, boolean ok, String id, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SaveReply(ok, false, id, message));
    }

    private static void replySave(ServerPlayer p, boolean ok, String id, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SaveReply(ok, true, id, message));
    }

    private static void onUpload(UploadPiece m, Supplier<NetworkEvent.Context> c) {
        ServerPlayer p = c.get().getSender();
        if (p == null) return;
        UUID who = p.getUUID();
        if (!Access.canEdit(p)) {
            UPLOADS.remove(who);
            replySave(p, false, "", Access.refusal());
            return;
        }
        if (m.total() < 1 || m.total() > MAX_PIECES || m.index() < 0 || m.index() >= m.total()) {
            UPLOADS.remove(who);
            replySave(p, false, "", "That design is too big.");
            return;
        }
        Upload up = m.index() == 0 ? new Upload() : UPLOADS.get(who);
        if (up == null || up.next != m.index()) {
            UPLOADS.remove(who);
            return;
        }
        if (m.index() == 0) UPLOADS.put(who, up);
        if (up.bytes.size() + m.data().length > Limits.MAX_DESIGN_GZ_BYTES) {
            UPLOADS.remove(who);
            replySave(p, false, "", "That design is too big.");
            return;
        }
        up.bytes.writeBytes(m.data());
        up.next++;
        if (up.next < m.total()) return;
        UPLOADS.remove(who);
        long now = System.currentTimeMillis();
        Long last = LAST_SAVE.get(who);
        if (last != null && now - last < 300) {
            replySave(p, false, "", "Saving too quickly, try again.");
            return;
        }
        LAST_SAVE.put(who, now);
        String json = Packer.unpack(up.bytes.toByteArray());
        if (json == null) {
            replySave(p, false, "", "That design is too big.");
            return;
        }
        DesignStore.Saved saved = DesignStore.save(p, json);
        replySave(p, saved.ok(), saved.id(), saved.message());
    }

    private static void onAction(Action m, Supplier<NetworkEvent.Context> c) {
        ServerPlayer p = c.get().getSender();
        if (p == null) return;
        if (m.act() == Act.OPEN) {
            if (Access.canEdit(p)) sendOpen(p, Ids.valid(m.id()) ? m.id() : "");
            else p.displayClientMessage(Component.literal(Access.refusal()), true);
            return;
        }
        if (!Access.canEdit(p)) {
            p.displayClientMessage(Component.literal(Access.refusal()), true);
            return;
        }
        Design d = Designs.SERVER.get(m.id());
        if (d == null) {
            reply(p, false, m.id(), "Save the mob first.");
            return;
        }
        switch (m.act()) {
            case DELETE -> {
                if (!Access.canChange(p, d)) {
                    reply(p, false, m.id(), "That mob belongs to someone else.");
                } else {
                    reply(p, DesignStore.delete(m.id()), "", "Deleted " + d.name);
                }
            }
            case SPAWN -> {
                if (d.type != Design.Type.MOB) {
                    reply(p, false, m.id(), "Only mobs can be spawned.");
                    return;
                }
                int n = Math.max(1, Math.min(Config.MAX_SPAWN_COUNT.get(), m.count()));
                for (int i = 0; i < n; i++) CustomMob.spawnNear(p.serverLevel(), p.position(), m.id(), 2.0 + i * 0.3);
                reply(p, true, m.id(), n == 1 ? "Spawned " + d.name : "Spawned " + n + " x " + d.name);
            }
            case EGG -> {
                ItemStack egg = DesignStacks.of(d);
                if (egg.isEmpty()) {
                    reply(p, false, m.id(), "A screen isn't something you can carry: a block opens it.");
                    return;
                }
                egg.setCount(Math.max(1, Math.min(egg.getMaxStackSize(), m.count())));
                if (!p.getInventory().add(egg)) p.drop(egg, false);
                reply(p, true, m.id(), d.type == Design.Type.MOB ? "An egg for " + d.name + " is in your bag" : d.name + " is in your bag");
            }
            default -> {
            }
        }
    }
}
