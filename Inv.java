package dev.aurora.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

public final class Inv {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private Inv() {}

    public static int hotbar(Item item) { return hotbarWhere(s -> s.isOf(item)); }

    public static int hotbarWhere(Predicate<ItemStack> test) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) if (!inv.getStack(i).isEmpty() && test.test(inv.getStack(i))) return i;
        return -1;
    }

    /** Right-click `against` on the given face using the hotbar slot, then restores the old slot. */
    public static void useOnBlock(BlockPos against, Direction face, int slot) {
        var inv = mc.player.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        Vec3d hit = Vec3d.ofCenter(against).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, face, against, false));
        inv.setSelectedSlot(prev);
    }

    public static void useOnEntity(Entity e, int slot) {
        var inv = mc.player.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.interactionManager.interactEntity(mc.player, e, Hand.MAIN_HAND);
        inv.setSelectedSlot(prev);
    }
}
