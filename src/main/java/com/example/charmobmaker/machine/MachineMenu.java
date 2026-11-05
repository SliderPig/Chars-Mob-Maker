package com.example.charmobmaker.machine;

import com.example.charmobmaker.Designs;
import com.example.charmobmaker.ModContent;
import com.example.charmobmaker.block.CustomBlockEntity;
import com.example.charmobmaker.model.Design;
import com.example.charmobmaker.model.ScreenInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.List;

// A real server-side menu with its slots laid out by the design. Everything happens on the server like any chest or furnace, so items are as safe as those.
public final class MachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_START = 0, BUTTON_STOP = 1, BUTTON_STEP = 100;

    private final CustomBlockEntity be;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final int machineSlots;
    public final String screenId;
    public final BlockPos pos;

    // On the server.
    public MachineMenu(int id, Inventory inventory, CustomBlockEntity be, Design screen) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.be = be;
        this.data = be.data();
        this.pos = be.getBlockPos();
        this.screenId = screen.id;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.machineSlots = screen.screen.slots().size();
        addSlots(be.items(), inventory, screen.screen, machineSlots);
    }

    // On the PC: slots stand in for the real ones, the server fills them.
    public MachineMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.be = null;
        this.data = new SimpleContainerData(3);
        this.pos = buf.readBlockPos();
        this.screenId = buf.readUtf(64);
        this.machineSlots = Math.max(0, Math.min(64, buf.readVarInt()));
        this.access = ContainerLevelAccess.NULL;
        Design d = Designs.CLIENT.get(screenId);
        addSlots(new ItemStackHandler(machineSlots), inventory, d == null ? new ScreenInfo() : d.screen, machineSlots);
    }

    private void addSlots(IItemHandler items, Inventory inventory, ScreenInfo screen, int count) {
        List<ScreenInfo.Widget> slots = screen.slots();
        for (int i = 0; i < count; i++) {
            ScreenInfo.Widget w = i < slots.size() ? slots.get(i) : null;
            boolean output = w != null && w.output;
            int x = w == null ? -2000 : w.x + 1, y = w == null ? -2000 : w.y + 1;
            addSlot(new SlotItemHandler(items, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !output && super.mayPlace(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, screen.invX + 1 + col * 18, screen.invY + 1 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, screen.invX + 1 + col * 18, screen.invY + 59));
        addDataSlots(data);
    }

    public float progress() {
        int total = data.get(1);
        return total <= 0 ? 0f : Math.min(1f, data.get(0) / (float) total);
    }

    public int step() {
        int s = data.get(2);
        return data.get(1) <= 0 ? -1 : s;
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (be == null) return false;
        if (button == BUTTON_START) return be.start(-1);
        if (button == BUTTON_STOP) {
            be.stopRunning();
            return true;
        }
        if (button >= BUTTON_STEP) return be.start(button - BUTTON_STEP);
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, machineSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        if (be == null) return true;
        return !be.isRemoved() && be.screenDesign() != null && be.screenDesign().id.equals(screenId) && player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    public ContainerLevelAccess access() {
        return access;
    }
}
