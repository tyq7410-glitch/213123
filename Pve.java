package dev.aurora.module;

import com.google.gson.JsonObject;
import dev.aurora.core.*;
import dev.aurora.util.Inv;
import dev.aurora.util.Util;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import dev.aurora.module.Pvp.*;
import dev.aurora.module.Movement.*;
import dev.aurora.module.Visuals.*;
import dev.aurora.module.Pve.*;
import dev.aurora.module.Cosmetics.*;
import dev.aurora.module.Misc.*;

/** All Pve modules in one file (keeps the repository small enough for GitHub web upload). */
public final class Pve {
    private Pve() {}

    public static class AutoTool extends Mod {
        private final Setting.Bool weapon = add(new Setting.Bool("Pick weapon", true));

        public AutoTool() { super("AutoTool", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || !mc.options.attackKey.isPressed()) return;
            HitResult hr = mc.crosshairTarget;
            if (hr == null) return;
            var inv = mc.player.getInventory();
            int best = -1;

            if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK) {
                BlockState st = mc.world.getBlockState(bhr.getBlockPos());
                float bestScore = inv.getStack(inv.getSelectedSlot()).getMiningSpeedMultiplier(st);
                for (int i = 0; i < 9; i++) {
                    float s = inv.getStack(i).getMiningSpeedMultiplier(st);
                    if (s > bestScore) { bestScore = s; best = i; }
                }
            } else if (weapon.get() && hr instanceof EntityHitResult) {
                int bestScore = score(inv.getStack(inv.getSelectedSlot()));
                for (int i = 0; i < 9; i++) {
                    int s = score(inv.getStack(i));
                    if (s > bestScore) { bestScore = s; best = i; }
                }
            }
            if (best != -1) inv.setSelectedSlot(best);
        }

        private static int score(ItemStack s) {
            if (s.isIn(ItemTags.SWORDS)) return 2;
            if (s.isIn(ItemTags.AXES)) return 1;
            return 0;
        }
    }

    public static class Nuker extends Mod {
        private final Setting.Num range = add(new Setting.Num("Range", 4.0, 1.0, 6.0, 0.5));
        private final Setting.Mode filter = add(new Setting.Mode("Blocks", "All", "All", "Above feet", "Ores"));
        private final Setting.Num perTick = add(new Setting.Num("Per tick (creative)", 3, 1, 10, 1));

        public Nuker() { super("Nuker", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            var p = mc.player;
            var w = mc.world;
            BlockPos base = p.getBlockPos();
            Vec3d eye = p.getEyePos();
            int r = (int) Math.ceil(range.get());

            List<BlockPos> list = new ArrayList<>();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                BlockPos pos = base.add(x, y, z);
                if (Vec3d.ofCenter(pos).distanceTo(eye) > range.get()) continue;
                if (filter.is("Above feet") && pos.getY() < base.getY()) continue;
                BlockState st = w.getBlockState(pos);
                if (st.isAir() || !st.getFluidState().isEmpty() || st.getHardness(w, pos) < 0) continue;
                if (filter.is("Ores") && !Registries.BLOCK.getId(st.getBlock()).getPath().endsWith("_ore")) continue;
                list.add(pos);
            }
            if (list.isEmpty()) return;
            list.sort(Comparator.<BlockPos>comparingDouble(b -> Vec3d.ofCenter(b).squaredDistanceTo(eye)));

            if (mc.interactionManager.getCurrentGameMode().isCreative()) {
                for (int i = 0; i < Math.min(perTick.asInt(), list.size()); i++)
                    mc.interactionManager.attackBlock(list.get(i), Direction.UP);
            } else {
                mc.interactionManager.updateBlockBreakingProgress(list.get(0), Direction.UP);
            }
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    public static class AutoRespawn extends Mod {
        private final Setting.Num delay = add(new Setting.Num("Задержка (тики)", 5, 0, 40, 1));
        private int t;

        public AutoRespawn() { super("AutoRespawn", Category.PVE); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (p.getHealth() <= 0) {
                if (++t >= delay.asInt()) { p.requestRespawn(); t = 0; }
            } else t = 0;
        }
    }

    public static class AutoEat extends Mod {
        private final Setting.Num hunger = add(new Setting.Num("Есть при голоде ≤", 14, 1, 19, 1));
        private final Setting.Bool saveGold = add(new Setting.Bool("Беречь золотые яблоки", true));
        private boolean eating;
        private int prev, ticks;

        public AutoEat() { super("AutoEat", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null) return;
            var p = mc.player;
            var inv = p.getInventory();

            if (eating) {
                ticks++;
                ItemStack cur = inv.getStack(inv.getSelectedSlot());
                if (p.getHungerManager().getFoodLevel() >= 20 || ticks > 100 || !cur.contains(DataComponentTypes.FOOD)) stop();
                else mc.options.useKey.setPressed(true);
                return;
            }
            if (p.getHungerManager().getFoodLevel() > hunger.asInt() || p.isUsingItem()) return;

            int best = -1, bestN = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getStack(i);
                FoodComponent f = s.get(DataComponentTypes.FOOD);
                if (f == null || bad(s)) continue;
                if (f.nutrition() > bestN) { bestN = f.nutrition(); best = i; }
            }
            if (best >= 0) {
                prev = inv.getSelectedSlot();
                inv.setSelectedSlot(best);
                eating = true;
                ticks = 0;
            }
        }

        private boolean bad(ItemStack s) {
            return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.SPIDER_EYE) || s.isOf(Items.POISONOUS_POTATO)
                    || s.isOf(Items.PUFFERFISH) || s.isOf(Items.CHORUS_FRUIT)
                    || (saveGold.get() && (s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE)));
        }

        private void stop() {
            mc.options.useKey.setPressed(false);
            if (mc.player != null) mc.player.getInventory().setSelectedSlot(prev);
            eating = false;
        }

        @Override protected void onDisable() { if (eating) stop(); }
    }

    public static class AutoFish extends Mod {
        private final Setting.Bool recast = add(new Setting.Bool("Авто-заброс", true));
        private final Setting.Num recastDelay = add(new Setting.Num("Пауза перед забросом (тики)", 20, 5, 60, 1));
        private final Setting.Num reaction = add(new Setting.Num("Задержка подсечки (тики)", 2, 0, 10, 1));
        private int cd, react;

        public AutoFish() { super("AutoFish", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            var p = mc.player;
            if (!p.getMainHandStack().isOf(Items.FISHING_ROD)) return;

            var hook = p.fishHook;
            if (hook == null) {
                react = 0;
                if (cd > 0) { cd--; return; }
                if (recast.get()) {
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    cd = recastDelay.asInt();
                }
                return;
            }
            // a bite throws the bobber sharply downwards
            if (hook.isTouchingWater() && hook.getVelocity().y < -0.15) {
                if (++react >= reaction.asInt()) {
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    cd = recastDelay.asInt();
                    react = 0;
                }
            }
        }
    }

    /** Harvests mature crops around you and replants them (seeds must be in the hotbar). */
    public static class AutoFarm extends Mod {
        private final Setting.Num range = add(new Setting.Num("Радиус", 4, 1, 6, 1));
        private final Setting.Num perTick = add(new Setting.Num("Действий за тик", 2, 1, 5, 1));
        private final Setting.Bool replant = add(new Setting.Bool("Пересаживать", true));
        private final Map<BlockPos, Item> pending = new HashMap<>();
        private final Map<BlockPos, Integer> age = new HashMap<>();

        public AutoFarm() { super("AutoFarm", Category.PVE); }

        private static Item seedFor(Block b) {
            if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
            if (b == Blocks.CARROTS) return Items.CARROT;
            if (b == Blocks.POTATOES) return Items.POTATO;
            if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
            return null;
        }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            var p = mc.player;
            var w = mc.world;
            int actions = 0, limit = perTick.asInt();

            Iterator<Map.Entry<BlockPos, Item>> it = pending.entrySet().iterator();
            while (it.hasNext() && actions < limit) {
                var e = it.next();
                BlockPos pos = e.getKey();
                int a = age.merge(pos, 1, Integer::sum);
                if (w.getBlockState(pos).isAir()) {
                    if (plant(pos, e.getValue())) actions++;
                    it.remove();
                    age.remove(pos);
                } else if (a > 40) {
                    it.remove();
                    age.remove(pos);
                }
            }
            if (actions >= limit) return;

            BlockPos base = p.getBlockPos();
            Vec3d eye = p.getEyePos();
            int r = range.asInt();
            for (int x = -r; x <= r; x++) for (int y = -1; y <= 1; y++) for (int z = -r; z <= r; z++) {
                BlockPos pos = base.add(x, y, z);
                BlockState st = w.getBlockState(pos);
                if (!(st.getBlock() instanceof CropBlock crop) || !crop.isMature(st)) continue;
                if (Vec3d.ofCenter(pos).distanceTo(eye) > 4.5) continue;

                mc.interactionManager.attackBlock(pos, Direction.UP);
                p.swingHand(Hand.MAIN_HAND);
                Item seed = seedFor(st.getBlock());
                if (replant.get() && seed != null && w.getBlockState(pos.down()).isOf(Blocks.FARMLAND)) pending.put(pos, seed);
                if (++actions >= limit) return;
            }
        }

        private boolean plant(BlockPos pos, Item seed) {
            var inv = mc.player.getInventory();
            int slot = -1;
            for (int i = 0; i < 9; i++) if (inv.getStack(i).isOf(seed)) { slot = i; break; }
            if (slot < 0) return false;
            int prev = inv.getSelectedSlot();
            inv.setSelectedSlot(slot);
            BlockPos below = pos.down();
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, below, false));
            inv.setSelectedSlot(prev);
            return true;
        }

        @Override protected void onDisable() { pending.clear(); age.clear(); }
    }

    /** Puts on the best armor from the inventory (netherite > diamond > iron > ...). */
    public static class AutoArmor extends Mod {
        private final Setting.Num delay = add(new Setting.Num("Задержка (тики)", 8, 1, 40, 1));
        private static final String[] SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};
        private int t;

        public AutoArmor() { super("AutoArmor", Category.PVE); }

        private static String path(ItemStack s) { return Registries.ITEM.getId(s.getItem()).getPath(); }

        private static double score(ItemStack s, String suffix) {
            if (s.isEmpty()) return -1;
            String n = path(s);
            if (!n.endsWith(suffix)) return -1;
            double tier = n.startsWith("netherite") ? 60 : n.startsWith("diamond") ? 50 : n.startsWith("iron") ? 40
                    : n.startsWith("chainmail") ? 30 : n.startsWith("copper") ? 25 : n.startsWith("golden") ? 20
                    : n.startsWith("turtle") ? 20 : n.startsWith("leather") ? 10 : 5;
            double dur = s.isDamageable() ? 1.0 - (double) s.getDamage() / s.getMaxDamage() : 1.0;
            return tier + dur * 5;
        }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < delay.asInt()) return;
            t = 0;
            var p = mc.player;
            var h = p.playerScreenHandler;
            if (!h.getCursorStack().isEmpty()) return;

            for (int k = 0; k < 4; k++) {
                int armorSlot = 5 + k;
                ItemStack cur = h.getSlot(armorSlot).getStack();
                if (!cur.isEmpty() && !path(cur).endsWith(SUFFIX[k])) continue; // elytra, pumpkin etc.
                double bestScore = score(cur, SUFFIX[k]);
                int best = -1;
                for (int s = 9; s < 45; s++) {
                    double sc = score(h.getSlot(s).getStack(), SUFFIX[k]);
                    if (sc > bestScore) { bestScore = sc; best = s; }
                }
                if (best < 0) continue;
                mc.interactionManager.clickSlot(h.syncId, best, 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(h.syncId, armorSlot, 0, SlotActionType.PICKUP, p);
                if (!cur.isEmpty()) mc.interactionManager.clickSlot(h.syncId, best, 0, SlotActionType.PICKUP, p);
                return;
            }
        }
    }

    /** Moves a Totem of Undying into the off-hand when your health is low enough. */
    public static class AutoTotem extends Mod {
        private final Setting.Num health = add(new Setting.Num("Здоровье ≤ (20 = всегда)", 20, 1, 20, 1));
        private final Setting.Num delay = add(new Setting.Num("Задержка (тики)", 5, 1, 20, 1));
        private int t;

        public AutoTotem() { super("AutoTotem", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < delay.asInt()) return;
            t = 0;
            var p = mc.player;
            var h = p.playerScreenHandler;
            if (!h.getCursorStack().isEmpty()) return;
            if (h.getSlot(45).getStack().isOf(Items.TOTEM_OF_UNDYING)) return;
            if (p.getHealth() > health.get()) return;

            for (int s = 9; s < 45; s++) {
                if (!h.getSlot(s).getStack().isOf(Items.TOTEM_OF_UNDYING)) continue;
                boolean had = !h.getSlot(45).getStack().isEmpty();
                mc.interactionManager.clickSlot(h.syncId, s, 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(h.syncId, 45, 0, SlotActionType.PICKUP, p);
                if (had) mc.interactionManager.clickSlot(h.syncId, s, 0, SlotActionType.PICKUP, p);
                return;
            }
        }
    }

    public static class AutoTorch extends Mod {
        private final Setting.Num light = add(new Setting.Num("Ставить при свете ≤", 3, 0, 14, 1));
        private final Setting.Num delay = add(new Setting.Num("Пауза (тики)", 10, 2, 60, 1));
        private int t;

        public AutoTorch() { super("AutoTorch", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < delay.asInt()) return;
            t = 0;
            var w = mc.world;
            BlockPos feet = mc.player.getBlockPos();
            if (w.getLightLevel(LightType.BLOCK, feet) > light.asInt() || !w.getBlockState(feet).isAir()) return;
            BlockPos below = feet.down();
            if (!w.getBlockState(below).isSideSolidFullSquare(w, below, Direction.UP)) return;
            int slot = Inv.hotbar(Items.TORCH);
            if (slot >= 0) Inv.useOnBlock(below, Direction.UP, slot);
        }
    }

    public static class TreeChopper extends Mod {
        private final Setting.Num range = add(new Setting.Num("Радиус", 4.0, 1.0, 5.5, 0.5));
        private final Setting.Bool leaves = add(new Setting.Bool("Ломать листву", false));

        public TreeChopper() { super("TreeChopper", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            var p = mc.player;
            var w = mc.world;
            Vec3d eye = p.getEyePos();
            BlockPos base = p.getBlockPos();
            int r = (int) Math.ceil(range.get());
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                BlockPos pos = base.add(x, y, z);
                BlockState st = w.getBlockState(pos);
                if (!(st.isIn(BlockTags.LOGS) || (leaves.get() && st.isIn(BlockTags.LEAVES)))) continue;
                double d = Vec3d.ofCenter(pos).distanceTo(eye);
                if (d <= range.get() && d < bd) { bd = d; best = pos; }
            }
            if (best == null) return;
            if (mc.interactionManager.getCurrentGameMode().isCreative()) mc.interactionManager.attackBlock(best, Direction.UP);
            else mc.interactionManager.updateBlockBreakingProgress(best, Direction.UP);
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    public static class AutoBonemeal extends Mod {
        private final Setting.Num range = add(new Setting.Num("Радиус", 4, 1, 5, 1));
        private final Setting.Num delay = add(new Setting.Num("Пауза (тики)", 4, 1, 20, 1));
        private int t;

        public AutoBonemeal() { super("AutoBonemeal", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < delay.asInt()) return;
            t = 0;
            int slot = Inv.hotbar(Items.BONE_MEAL);
            if (slot < 0) return;
            var w = mc.world;
            BlockPos base = mc.player.getBlockPos();
            Vec3d eye = mc.player.getEyePos();
            int r = range.asInt();
            for (int x = -r; x <= r; x++) for (int y = -2; y <= 2; y++) for (int z = -r; z <= r; z++) {
                BlockPos pos = base.add(x, y, z);
                BlockState st = w.getBlockState(pos);
                boolean grow = (st.getBlock() instanceof CropBlock c && !c.isMature(st)) || st.getBlock() instanceof SaplingBlock;
                if (grow && Vec3d.ofCenter(pos).distanceTo(eye) <= 4.5) {
                    Inv.useOnBlock(pos, Direction.UP, slot);
                    return;
                }
            }
        }
    }

    public static class AutoShear extends Mod {
        private final Setting.Num range = add(new Setting.Num("Дальность", 3.5, 1.5, 5.0, 0.5));
        private final Setting.Num delay = add(new Setting.Num("Пауза (тики)", 10, 2, 40, 1));
        private int t;

        public AutoShear() { super("AutoShear", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < delay.asInt()) return;
            t = 0;
            int slot = Inv.hotbar(Items.SHEARS);
            if (slot < 0) return;
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof SheepEntity s && !s.isSheared() && !s.isBaby() && s.distanceTo(mc.player) <= range.get()) {
                    Inv.useOnEntity(s, slot);
                    return;
                }
            }
        }
    }

    public static class AutoBreed extends Mod {
        private final Setting.Num range = add(new Setting.Num("Дальность", 3.5, 1.5, 5.0, 0.5));
        private final Setting.Num delay = add(new Setting.Num("Пауза (тики)", 10, 2, 40, 1));
        private int t;

        public AutoBreed() { super("AutoBreed", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < delay.asInt()) return;
            t = 0;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof AnimalEntity a) || a.isBaby() || a.isInLove() || a.getBreedingAge() != 0) continue;
                if (a.distanceTo(mc.player) > range.get()) continue;
                int slot = Inv.hotbarWhere(a::isBreedingItem);
                if (slot >= 0) { Inv.useOnEntity(a, slot); return; }
            }
        }
    }

    /** Sneaks automatically at the edge of a block so you do not walk off it. */
    public static class EdgeSneak extends Mod {
        private boolean pressed;

        public EdgeSneak() { super("EdgeSneak", Category.PVE); }

        @Override
        public void onTick() {
            var p = mc.player;
            boolean edge = false;
            if (p.isOnGround() && Util.moving() && !p.getAbilities().flying) {
                double[] v = Util.horizontal(1.0);
                BlockPos under = BlockPos.ofFloored(p.getX() + v[0] * 0.35, p.getY() - 0.5, p.getZ() + v[1] * 0.35);
                edge = mc.world.getBlockState(under).isAir();
            }
            if (edge) { mc.options.sneakKey.setPressed(true); pressed = true; }
            else if (pressed) { mc.options.sneakKey.setPressed(false); pressed = false; }
        }

        @Override protected void onDisable() { if (pressed) mc.options.sneakKey.setPressed(false); pressed = false; }
    }

    /** Throws junk out of the inventory. */
    public static class AutoDrop extends Mod {
        private final Setting.Bool flesh = add(new Setting.Bool("Гнилая плоть", true));
        private final Setting.Bool eye = add(new Setting.Bool("Паучьи глаза", true));
        private final Setting.Bool bone = add(new Setting.Bool("Кости", false));
        private final Setting.Bool potato = add(new Setting.Bool("Ядовитый картофель", true));
        private final Setting.Bool cobble = add(new Setting.Bool("Булыжник", false));
        private int t;

        public AutoDrop() { super("AutoDrop", Category.PVE); }

        private boolean junk(ItemStack s) {
            return (flesh.get() && s.isOf(Items.ROTTEN_FLESH)) || (eye.get() && s.isOf(Items.SPIDER_EYE))
                    || (bone.get() && s.isOf(Items.BONE)) || (potato.get() && s.isOf(Items.POISONOUS_POTATO))
                    || (cobble.get() && (s.isOf(Items.COBBLESTONE) || s.isOf(Items.COBBLED_DEEPSLATE)));
        }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < 10) return;
            t = 0;
            var h = mc.player.playerScreenHandler;
            for (int s = 9; s < 45; s++) {
                if (junk(h.getSlot(s).getStack())) {
                    mc.interactionManager.clickSlot(h.syncId, s, 1, SlotActionType.THROW, mc.player);
                    return;
                }
            }
        }
    }

    public static class DurabilityWarning extends Mod {
        private final Setting.Num percent = add(new Setting.Num("Порог (%)", 10, 1, 50, 1));
        private final long[] last = new long[5];
        private int t;

        public DurabilityWarning() { super("DurabilityWarning", Category.PVE); }

        @Override
        public void onTick() {
            if (++t < 20) return;
            t = 0;
            var p = mc.player;
            EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            long now = System.currentTimeMillis();
            for (int i = 0; i < slots.length; i++) {
                ItemStack s = p.getEquippedStack(slots[i]);
                if (s.isEmpty() || !s.isDamageable()) continue;
                int pct = (s.getMaxDamage() - s.getDamage()) * 100 / s.getMaxDamage();
                if (pct <= percent.asInt() && now - last[i] > 30_000) {
                    Notifications.pushText(s.getName().getString() + " скоро сломается (" + pct + "%)", false);
                    last[i] = now;
                }
            }
        }
    }

    public static class AutoWalk extends Mod {
        public AutoWalk() { super("AutoWalk", Category.PVE); }

        @Override public void onTick() { if (mc.currentScreen == null) mc.options.forwardKey.setPressed(true); }
        @Override protected void onDisable() { if (mc.options != null) mc.options.forwardKey.setPressed(false); }
    }

    /** Holds the attack / mine button for you. */
    public static class AutoMine extends Mod {
        public AutoMine() { super("AutoMine", Category.PVE); }

        @Override public void onTick() { if (mc.currentScreen == null) mc.options.attackKey.setPressed(true); }
        @Override protected void onDisable() { if (mc.options != null) mc.options.attackKey.setPressed(false); }
    }

    /** Holds the use button for you (eating, bow, building). */
    public static class AutoUse extends Mod {
        public AutoUse() { super("AutoUse", Category.PVE); }

        @Override public void onTick() { if (mc.currentScreen == null) mc.options.useKey.setPressed(true); }
        @Override protected void onDisable() { if (mc.options != null) mc.options.useKey.setPressed(false); }
    }

    public static class DeathPoint extends HudBase {
        private double x, y, z;
        private boolean has, dead;

        public DeathPoint() { super("DeathPoint", 2, 20, Category.PVE); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (p.getHealth() <= 0) {
                if (!dead) { x = p.getX(); y = p.getY(); z = p.getZ(); has = true; dead = true; }
            } else dead = false;
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (!has) return;
            var p = mc.player;
            double d = Math.hypot(x - p.getX(), z - p.getZ());
            drawLines(ctx, List.of(String.format("Смерть: %d %d %d", (int) x, (int) y, (int) z),
                    String.format("%s  %d м", Util.arrow(x - p.getX(), z - p.getZ()), (int) d)));
        }
    }

    public static class HomePoint extends HudBase {
        private final Setting.Bool setHere = add(new Setting.Bool("Запомнить точку здесь", false));
        private final Setting.Bool clear = add(new Setting.Bool("Сбросить точку", false));
        private double x, y, z;
        private boolean has;

        public HomePoint() { super("HomePoint", 2, 25, Category.PVE); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (setHere.get()) { x = p.getX(); y = p.getY(); z = p.getZ(); has = true; setHere.toggle(); Notifications.pushText("Точка сохранена", true); }
            if (clear.get()) { has = false; clear.toggle(); }
        }

        @Override
        public void onHud(DrawContext ctx) {
            if (!has) return;
            var p = mc.player;
            double d = Math.hypot(x - p.getX(), z - p.getZ());
            drawLines(ctx, List.of(String.format("Дом: %d %d %d", (int) x, (int) y, (int) z),
                    String.format("%s  %d м", Util.arrow(x - p.getX(), z - p.getZ()), (int) d)));
        }

        @Override
        public void saveExtra(JsonObject o) {
            o.addProperty("has", has); o.addProperty("hx", x); o.addProperty("hy", y); o.addProperty("hz", z);
        }

        @Override
        public void loadExtra(JsonObject o) {
            if (!o.has("has")) return;
            has = o.get("has").getAsBoolean();
            x = o.get("hx").getAsDouble(); y = o.get("hy").getAsDouble(); z = o.get("hz").getAsDouble();
        }
    }

    public static class LightLevel extends HudBase {
        public LightLevel() { super("LightLevel", 2, 35, Category.PVE); }

        @Override
        public void onHud(DrawContext ctx) {
            var pos = mc.player.getBlockPos();
            int block = mc.world.getLightLevel(LightType.BLOCK, pos);
            int sky = mc.world.getLightLevel(LightType.SKY, pos);
            drawLines(ctx, List.of("Свет: блок " + block + " / небо " + sky,
                    block == 0 && sky < 8 ? "Тут могут появиться мобы" : "Безопасно"));
        }
    }

    /** Swaps a fresh stack into your hand when the held stack is almost used up. */
    public static class AutoRefill extends Mod {
        private final Setting.Num left = add(new Setting.Num("Пополнять при остатке ≤", 8, 1, 32, 1));
        private int t;

        public AutoRefill() { super("AutoRefill", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < 5) return;
            t = 0;
            var p = mc.player;
            ItemStack held = p.getMainHandStack();
            if (held.isEmpty() || held.getMaxCount() <= 1 || held.getCount() > left.asInt()) return;
            var h = p.playerScreenHandler;
            if (!h.getCursorStack().isEmpty()) return;
            for (int s = 9; s < 36; s++) {
                ItemStack st = h.getSlot(s).getStack();
                if (ItemStack.areItemsAndComponentsEqual(st, held) && st.getCount() > held.getCount()) {
                    mc.interactionManager.clickSlot(h.syncId, s, p.getInventory().getSelectedSlot(), SlotActionType.SWAP, p);
                    return;
                }
            }
        }
    }

    public static class AutoSapling extends Mod {
        private final Setting.Num range = add(new Setting.Num("Радиус", 3, 1, 4, 1));
        private final Setting.Num spacing = add(new Setting.Num("Расстояние между саженцами", 2, 1, 4, 1));
        private final Setting.Num delay = add(new Setting.Num("Пауза (тики)", 20, 5, 100, 1));
        private int t;

        public AutoSapling() { super("AutoSapling", Category.PVE); }

        @Override
        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null || ++t < delay.asInt()) return;
            t = 0;
            int slot = Inv.hotbarWhere(s -> s.isIn(ItemTags.SAPLINGS));
            if (slot < 0) return;
            var w = mc.world;
            BlockPos base = mc.player.getBlockPos();
            int r = range.asInt(), sp = spacing.asInt();
            for (int x = -r; x <= r; x++) for (int y = -1; y <= 1; y++) for (int z = -r; z <= r; z++) {
                BlockPos ground = base.add(x, y, z);
                if (!w.getBlockState(ground).isIn(BlockTags.DIRT) || !w.getBlockState(ground.up()).isAir()) continue;
                boolean crowded = false;
                for (int dx = -sp; dx <= sp && !crowded; dx++) for (int dz = -sp; dz <= sp; dz++)
                    if (w.getBlockState(ground.up().add(dx, 0, dz)).getBlock() instanceof SaplingBlock) { crowded = true; break; }
                if (crowded) continue;
                Inv.useOnBlock(ground, Direction.UP, slot);
                return;
            }
        }
    }

    public static class FoodHud extends HudBase {
        public FoodHud() { super("FoodHUD", 2, 40, Category.PVE); }

        @Override
        public void onHud(DrawContext ctx) {
            var hm = mc.player.getHungerManager();
            drawLines(ctx, List.of("Голод: " + hm.getFoodLevel() + "/20", String.format("Насыщение: %.1f", hm.getSaturationLevel())));
        }
    }

    public static class SessionStats extends HudBase {
        private final long start = System.currentTimeMillis();
        private double dist, lx, lz;
        private boolean init;

        public SessionStats() { super("SessionStats", 2, 45, Category.PVE); }

        @Override
        public void onTick() {
            var p = mc.player;
            if (init) {
                double d = Math.hypot(p.getX() - lx, p.getZ() - lz);
                if (d < 10) dist += d;
            }
            lx = p.getX(); lz = p.getZ(); init = true;
        }

        @Override
        public void onHud(DrawContext ctx) {
            long s = (System.currentTimeMillis() - start) / 1000;
            drawLines(ctx, List.of(String.format("Сессия: %d:%02d:%02d", s / 3600, s / 60 % 60, s % 60),
                    String.format("Пройдено: %.2f км", dist / 1000.0)));
        }
    }

    public static class HeldCounter extends HudBase {
        public HeldCounter() { super("HeldCounter", 50, 90, Category.PVE); }

        @Override
        public void onHud(DrawContext ctx) {
            var p = mc.player;
            ItemStack held = p.getMainHandStack();
            if (held.isEmpty()) return;
            int total = 0;
            var inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (ItemStack.areItemsAndComponentsEqual(s, held)) total += s.getCount();
            }
            int w = 56, h = 20, x = ox(ctx, w), y = oy(ctx, h);
            box(ctx, x, y, w, h);
            ctx.drawItem(held, x + 2, y + 2);
            ctx.drawText(mc.textRenderer, "x" + total, x + 22, y + 6, 0xFFFFFFFF, true);
        }
    }

    /** Pulsing red edges of the screen when your health is low. */
    public static class LowHealthAlert extends Mod {
        private final Setting.Num hearts = add(new Setting.Num("Порог (сердца)", 3, 1, 10, 1));
        private final Setting.Num strength = add(new Setting.Num("Сила (%)", 60, 10, 100, 5));

        public LowHealthAlert() { super("LowHealthAlert", Category.PVE); }

        @Override
        public void onHud(DrawContext ctx) {
            var p = mc.player;
            double thr = hearts.get() * 2;
            if (p.getHealth() > thr || p.getHealth() <= 0) return;
            double pulse = 0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 200.0);
            int a = (int) (Math.min(1.0, strength.get() / 100.0) * 200 * pulse * (1.0 - 0.5 * p.getHealth() / thr));
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight(), e = h / 4;
            ctx.fillGradient(0, 0, w, e, ((a & 255) << 24) | 0xFF0000, 0x00FF0000);
            ctx.fillGradient(0, h - e, w, h, 0x00FF0000, ((a & 255) << 24) | 0xFF0000);
            int side = w / 14;
            for (int i = 0; i < 10; i++) {
                int sa = a * (10 - i) / 10 / 2;
                int x1 = i * side / 10, x2 = (i + 1) * side / 10;
                ctx.fill(x1, 0, x2, h, ((sa & 255) << 24) | 0xFF0000);
                ctx.fill(w - x2, 0, w - x1, h, ((sa & 255) << 24) | 0xFF0000);
            }
        }
    }
}
