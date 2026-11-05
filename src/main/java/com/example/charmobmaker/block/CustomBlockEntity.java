package com.example.charmobmaker.block;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.DesignStacks;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.machine.MachineMenu;
import com.example.charmobmaker.model.Clip;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.MachineRules;
import com.example.charmobmaker.model.Rig;
import com.example.charmobmaker.model.ScreenInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

// A placed made block: which design it is and, if that design opens a screen, its slots and how far along the current step is.
public final class CustomBlockEntity extends BlockEntity implements MenuProvider {
    public static final ModelProperty<String> DESIGN = new ModelProperty<>();
    private static final Set<CustomBlockEntity> LOADED_SERVER = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<CustomBlockEntity> LOADED_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    private String design = "";
    private final ItemStackHandler items = new ItemStackHandler(0) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int progress, total, step = -1;
    private boolean running;
    private float reach = 16f;
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(this::hopperView);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> total;
                case 2 -> step;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> total = value;
                case 2 -> step = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CustomBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CUSTOM_BE.get(), pos, state);
    }

    public String designId() {
        return design;
    }

    public boolean running() {
        return running;
    }

    public ItemStackHandler items() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    public void setDesign(String id) {
        design = id == null ? "" : id;
        refreshFromDesign();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public Design designNow() {
        return level == null ? null : Designs.of(level, design);
    }

    // The screen design this block opens, or null.
    public Design screenDesign() {
        Design d = designNow();
        if (d == null || d.block.screen.isEmpty()) return null;
        Design s = Designs.of(level, d.block.screen);
        return s != null && s.type == Design.Type.SCREEN ? s : null;
    }

    private void refreshFromDesign() {
        if (level == null) return;
        Design d = designNow();
        reach = d == null ? 16f : Rig.of(d).reach();
        Design s = screenDesign();
        if (s != null) growTo(s.screen.slots().size());
        if (level.isClientSide) requestModelDataUpdate();
    }

    // Never gets smaller: if a design loses a slot its things stay safe, and drop when the block is broken.
    private void growTo(int size) {
        if (items.getSlots() >= size) return;
        ItemStack[] old = new ItemStack[items.getSlots()];
        for (int i = 0; i < old.length; i++) old[i] = items.getStackInSlot(i);
        items.setSize(size);
        for (int i = 0; i < old.length; i++) items.setStackInSlot(i, old[i]);
    }

    // Called when the design changes: its light and whether it's a machine are part of the block state.
    public void designChanged() {
        if (level == null) return;
        refreshFromDesign();
        if (level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
            return;
        }
        Design d = designNow();
        if (d != null) {
            BlockState want = CustomBlock.stateFor(getBlockState(), d);
            if (want != getBlockState()) level.setBlock(worldPosition, want, 3);
        }
    }

    public static void refreshAll(boolean client, String id) {
        for (CustomBlockEntity be : new java.util.ArrayList<>(client ? LOADED_CLIENT : LOADED_SERVER)) {
            if (id.equals(be.design)) be.designChanged();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) return;
        (level.isClientSide ? LOADED_CLIENT : LOADED_SERVER).add(this);
        refreshFromDesign();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED_CLIENT.remove(this);
        LOADED_SERVER.remove(this);
        itemCap.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED_CLIENT.remove(this);
        LOADED_SERVER.remove(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        float r = reach / 16f;
        return new AABB(worldPosition.getX() + 0.5 - r, worldPosition.getY() - r, worldPosition.getZ() + 0.5 - r, worldPosition.getX() + 0.5 + r, worldPosition.getY() + r, worldPosition.getZ() + 0.5 + r);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(DESIGN, design).build();
    }

    // ---------- saving and sending ----------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Design", design);
        if (items.getSlots() > 0) tag.put("Items", items.serializeNBT());
        if (step >= 0) {
            tag.putInt("Step", step);
            tag.putInt("Progress", progress);
            tag.putInt("Total", total);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        design = tag.getString("Design");
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        step = tag.contains("Step") ? tag.getInt("Step") : -1;
        progress = tag.getInt("Progress");
        total = tag.getInt("Total");
        running = tag.getBoolean("Running") || step >= 0;
        if (level != null && level.isClientSide) requestModelDataUpdate();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("Design", design);
        tag.putBoolean("Running", running);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        applyClientTag(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) applyClientTag(pkt.getTag());
    }

    private void applyClientTag(CompoundTag tag) {
        String before = design;
        design = tag.getString("Design");
        running = tag.getBoolean("Running");
        refreshFromDesign();
        if (!before.equals(design) && level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
    }

    // Hoppers put things in the input slots and take from the output slots.
    private IItemHandler hopperView() {
        return new IItemHandler() {
            private boolean output(int slot) {
                Design s = screenDesign();
                if (s == null) return false;
                List<ScreenInfo.Widget> slots = s.screen.slots();
                return slot < slots.size() && slots.get(slot).output;
            }

            private int count() {
                Design s = screenDesign();
                return s == null ? 0 : Math.min(items.getSlots(), s.screen.slots().size());
            }

            @Override
            public int getSlots() {
                return count();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot < count() ? items.getStackInSlot(slot) : ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return slot >= count() || output(slot) ? stack : items.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot < count() && output(slot) ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return items.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !output(slot);
            }
        };
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && screenDesign() != null) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    // ---------- the machine ----------

    @Override
    public Component getDisplayName() {
        Design d = designNow();
        return Component.literal(d == null ? "Machine" : d.name);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        Design s = screenDesign();
        if (s == null) return null;
        growTo(s.screen.slots().size());
        return new MachineMenu(id, inventory, this, s);
    }

    public void writeOpen(FriendlyByteBuf buf) {
        Design s = screenDesign();
        buf.writeBlockPos(worldPosition);
        buf.writeUtf(s == null ? "" : s.id, 64);
        buf.writeVarInt(s == null ? 0 : s.screen.slots().size());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CustomBlockEntity be) {
        be.tickMachine();
    }

    private MachineRules.Held[] heldNow(int slotCount) {
        MachineRules.Held[] held = new MachineRules.Held[slotCount];
        for (int i = 0; i < slotCount; i++) {
            ItemStack s = items.getStackInSlot(i);
            held[i] = new MachineRules.Held(DesignStacks.refOf(s), s.getCount(), s.isEmpty() ? 64 : s.getMaxStackSize());
        }
        return held;
    }

    private boolean[] outputsOf(ScreenInfo screen, int slotCount) {
        List<ScreenInfo.Widget> slots = screen.slots();
        boolean[] out = new boolean[slotCount];
        for (int i = 0; i < slotCount; i++) out[i] = slots.get(i).output;
        return out;
    }

    private int maxOf(String ref) {
        ItemStack s = DesignStacks.ofRef(ref, 1, Designs.SERVER);
        return s.isEmpty() ? 64 : s.getMaxStackSize();
    }

    private MachineRules.Plan planFor(ScreenInfo screen, ScreenInfo.Recipe r) {
        int count = Math.min(items.getSlots(), screen.slots().size());
        if (r.inputs.isEmpty() || DesignStacks.ofRef(r.output, 1, Designs.SERVER).isEmpty()) return null;
        return MachineRules.plan(r, heldNow(count), outputsOf(screen, count), maxOf(r.output));
    }

    private void tickMachine() {
        Design s = screenDesign();
        if (s == null) {
            if (step >= 0) stopRunning();
            return;
        }
        ScreenInfo screen = s.screen;
        if (step >= 0) {
            if (step >= screen.recipes.size() || planFor(screen, screen.recipes.get(step)) == null) {
                stopRunning();
                return;
            }
            progress++;
            setChanged();
            if (progress >= total) finish(screen, screen.recipes.get(step));
        } else if (screen.auto) {
            start(-1);
        }
    }

    // Starts a step, or the first one that can go when given -1. False when it can't.
    public boolean start(int which) {
        if (step >= 0) return false;
        Design s = screenDesign();
        if (s == null) return false;
        ScreenInfo screen = s.screen;
        if (which < 0) {
            for (int i = 0; i < screen.recipes.size(); i++) {
                if (planFor(screen, screen.recipes.get(i)) != null) {
                    which = i;
                    break;
                }
            }
            if (which < 0) return false;
        }
        if (which >= screen.recipes.size() || planFor(screen, screen.recipes.get(which)) == null) return false;
        step = which;
        progress = 0;
        total = Math.max(1, Math.round(screen.recipes.get(which).seconds * 20f));
        setRunning(true);
        return true;
    }

    public void stopRunning() {
        step = -1;
        progress = 0;
        total = 0;
        setRunning(false);
    }

    private void setRunning(boolean now) {
        setChanged();
        if (running != now) {
            running = now;
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void finish(ScreenInfo screen, ScreenInfo.Recipe r) {
        MachineRules.Plan plan = planFor(screen, r);
        ItemStack result = DesignStacks.ofRef(r.output, r.outputCount, Designs.SERVER);
        if (plan == null || result.isEmpty()) {
            stopRunning();
            return;
        }
        for (int i = 0; i < plan.take().length; i++) {
            if (plan.take()[i] > 0) {
                ItemStack s = items.getStackInSlot(i).copy();
                s.shrink(plan.take()[i]);
                items.setStackInSlot(i, s);
            }
        }
        ItemStack there = items.getStackInSlot(plan.resultSlot());
        if (there.isEmpty()) {
            items.setStackInSlot(plan.resultSlot(), result);
        } else {
            ItemStack grown = there.copy();
            grown.grow(result.getCount());
            items.setStackInSlot(plan.resultSlot(), grown);
        }
        if (level != null) level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.3f);
        stopRunning();
    }

    // Used by the block's renderer when choosing a clip.
    public static boolean hasClip(Design d, String name) {
        Clip c = d.clip(name);
        return c != null && !c.tracks.isEmpty();
    }
}
