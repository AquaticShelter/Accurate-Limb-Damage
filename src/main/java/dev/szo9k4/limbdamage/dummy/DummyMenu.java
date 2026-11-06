package dev.szo9k4.limbdamage.dummy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The dummy's inventory screen: six equipment slots (helmet, chestplate, leggings, boots, main hand, off hand)
 * that accept ANY item, like a player standing there, plus your own inventory underneath. Equipment is read
 * from and written straight to the dummy, so the world copy, the screen and the renderer can never disagree.
 */
public final class DummyMenu extends AbstractContainerMenu {

    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    /** What the screen needs to know, sent when the menu opens. */
    public record Stats(float maxHealth, float reduction, float autoHeal, int mode, boolean canEdit, boolean canEquip) {

        public static Stats of(DummyEntity dummy, Player viewer) {
            return new Stats(dummy.getMaxHealth(), dummy.getDamageReduction(), dummy.effectiveAutoHealSeconds(),
                    dummy.getDeathMode().ordinal(), dummy.canEditSettings(viewer), dummy.canEquip(viewer));
        }

        public void write(FriendlyByteBuf buf, int entityId) {
            buf.writeVarInt(entityId);
            buf.writeFloat(maxHealth);
            buf.writeFloat(reduction);
            buf.writeFloat(autoHeal);
            buf.writeVarInt(mode);
            buf.writeBoolean(canEdit);
            buf.writeBoolean(canEquip);
        }
    }

    public final DummyEntity dummy;
    public final Stats stats;

    public DummyMenu(int id, Inventory inventory, DummyEntity dummy, Stats stats) {
        super(ModMenus.DUMMY.get(), id);
        this.dummy = dummy;
        this.stats = stats;
        Container equipment = new EquipmentContainer(dummy);

        // armor down the left, hands down the right (the screen draws the dummy between them)
        addSlot(new EquipSlot(equipment, 0, 8, 18, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET));
        addSlot(new EquipSlot(equipment, 1, 8, 36, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE));
        addSlot(new EquipSlot(equipment, 2, 8, 54, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS));
        addSlot(new EquipSlot(equipment, 3, 8, 72, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS));
        addSlot(new EquipSlot(equipment, 4, 152, 36, null));
        addSlot(new EquipSlot(equipment, 5, 152, 54, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 198));
        }
    }

    /** Client side: rebuilt from the data the server sent when it opened the menu. */
    public static DummyMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        Stats stats = new Stats(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean());
        Entity entity = inventory.player.level().getEntity(entityId);
        DummyEntity dummy = entity instanceof DummyEntity d ? d : ModEntities.DUMMY.get().create(inventory.player.level());
        return new DummyMenu(id, inventory, dummy, stats);
    }

    @Override
    public boolean stillValid(Player player) {
        return dummy.isAlive() && player.distanceToSqr(dummy) < 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOTS.length) {
            if (!moveItemStackTo(stack, SLOTS.length, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = 4;
            EquipmentSlot wanted = Mob.getEquipmentSlotForItem(stack);
            for (int i = 0; i < SLOTS.length; i++) {
                if (SLOTS[i] == wanted) {
                    target = i;
                }
            }
            if (!moveItemStackTo(stack, target, target + 1, false) && !moveItemStackTo(stack, 4, 6, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** A view of the dummy's six equipment slots as a Container. */
    private static final class EquipmentContainer implements Container {
        private final DummyEntity dummy;

        EquipmentContainer(DummyEntity dummy) {
            this.dummy = dummy;
        }

        @Override
        public int getContainerSize() {
            return SLOTS.length;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot slot : SLOTS) {
                if (!dummy.getItemBySlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return dummy.getItemBySlot(SLOTS[index]);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = getItem(index);
            if (current.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = current.split(count);
            dummy.setItemSlot(SLOTS[index], current.isEmpty() ? ItemStack.EMPTY : current);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack current = getItem(index);
            dummy.setItemSlot(SLOTS[index], ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            dummy.setItemSlot(SLOTS[index], stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return dummy.isAlive() && player.distanceToSqr(dummy) < 64.0;
        }

        @Override
        public void clearContent() {
            for (EquipmentSlot slot : SLOTS) {
                dummy.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /** An equipment slot: takes anything (armor slots too), one item, only if the viewer may equip this dummy. */
    private final class EquipSlot extends Slot {
        EquipSlot(Container container, int index, int x, int y, net.minecraft.resources.ResourceLocation emptyIcon) {
            super(container, index, x, y);
            if (emptyIcon != null) {
                setBackground(InventoryMenu.BLOCK_ATLAS, emptyIcon);
            }
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stats.canEquip();
        }

        @Override
        public boolean mayPickup(Player player) {
            return stats.canEquip();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}
