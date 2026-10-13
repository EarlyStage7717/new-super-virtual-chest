package afkx.nsvc;

import com.mojang.serialization.DataResult;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;

import java.util.UUID;

public class VirtualChest extends SimpleInventory {
    private final String name;
    private final Size chestSize;
    private UUID owner;         // 改为非 final
    private boolean isPublic;   // 改为非 final
    private boolean pendingDeletion;
    private long deletionStartTime;

    public enum Size {
        SMALL(27),
        LARGE(54);

        private final int slots;
        Size(int slots) { this.slots = slots; }
        public int getSlots() { return slots; }
        public String getDisplayName() { return this == SMALL ? "small" : "large"; }
    }

    public VirtualChest(String name, Size size) {
        this(name, size, null, true);
    }

    public VirtualChest(String name, Size size, UUID owner) {
        this(name, size, owner, false);
    }

    private VirtualChest(String name, Size size, UUID owner, boolean isPublic) {
        super(size.getSlots());
        this.name = name;
        this.chestSize = size;
        this.owner = owner;
        this.isPublic = isPublic;
        this.pendingDeletion = false;
        this.deletionStartTime = 0;
    }

    public String getName() { return name; }
    public Size getChestSize() { return chestSize; }
    public UUID getOwner() { return owner; }
    public boolean isPublic() { return isPublic; }
    public boolean isPendingDeletion() { return pendingDeletion; }
    public long getDeletionStartTime() { return deletionStartTime; }

    public void setPendingDeletion(boolean pending, long startTime) {
        this.pendingDeletion = pending;
        this.deletionStartTime = startTime;
    }

    /** 转移所有权：箱子变为私有，所有者改为新玩家 */
    public void transferTo(UUID newOwner) {
        this.owner = newOwner;
        this.isPublic = false;
    }

    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putString("name", name);
        nbt.putString("size", chestSize.name());
        nbt.putBoolean("public", isPublic);
        if (owner != null) {
            nbt.putString("owner", owner.toString());
        }
        nbt.putBoolean("pendingDeletion", pendingDeletion);
        if (pendingDeletion) {
            nbt.putLong("deletionStartTime", deletionStartTime);
        }

        NbtList items = new NbtList();
        for (int i = 0; i < chestSize.getSlots(); i++) {
            ItemStack stack = getStack(i);
            if (!stack.isEmpty()) {
                final int slot = i;
                DataResult<NbtElement> result = ItemStack.OPTIONAL_CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), stack);
                result.resultOrPartial(error -> {
                    VirtualChestMod.LOGGER.error("编码物品失败，槽位 {}: {}", slot, error);
                }).ifPresent(element -> {
                    NbtCompound itemEntry = new NbtCompound();
                    itemEntry.putInt("Slot", slot);
                    itemEntry.put("Item", element);
                    items.add(itemEntry);
                });
            }
        }
        nbt.put("Items", items);
        return nbt;
    }

    public static VirtualChest fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        String name = nbt.getString("name").orElse("unknown");
        String sizeStr = nbt.getString("size").orElse("LARGE");
        boolean isPublic = nbt.getBoolean("public").orElse(false);

        UUID owner = null;
        String ownerStr = nbt.getString("owner").orElse(null);
        if (ownerStr != null && !ownerStr.isEmpty()) {
            try {
                owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException e) {
                VirtualChestMod.LOGGER.error("无效的 UUID 字符串: {}", ownerStr);
            }
        }

        Size size;
        try {
            size = Size.valueOf(sizeStr);
        } catch (IllegalArgumentException e) {
            size = Size.LARGE;
        }
        VirtualChest chest = new VirtualChest(name, size, owner, isPublic);
        chest.pendingDeletion = nbt.getBoolean("pendingDeletion").orElse(false);
        if (chest.pendingDeletion) {
            chest.deletionStartTime = nbt.getLong("deletionStartTime").orElse(0L);
        }

        NbtList items = nbt.getList("Items").orElse(new NbtList());
        for (int i = 0; i < items.size(); i++) {
            NbtCompound itemEntry = items.getCompound(i).orElse(null);
            if (itemEntry != null) {
                int slot = itemEntry.getInt("Slot").orElse(-1);
                if (slot >= 0 && slot < chest.chestSize.getSlots()) {
                    NbtElement itemElement = itemEntry.get("Item");
                    if (itemElement != null) {
                        DataResult<ItemStack> result = ItemStack.OPTIONAL_CODEC.parse(registries.getOps(NbtOps.INSTANCE), itemElement);
                        var optionalStack = result.resultOrPartial(error -> {
                            VirtualChestMod.LOGGER.error("解码物品失败，槽位 {}: {}", slot, error);
                        });
                        if (optionalStack.isPresent()) {
                            chest.setStack(slot, optionalStack.get());
                        }
                    }
                }
            }
        }
        return chest;
    }
}