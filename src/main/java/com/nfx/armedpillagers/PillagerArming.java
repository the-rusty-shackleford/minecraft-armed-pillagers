package com.nfx.armedpillagers;

import com.f708.anothergunmod.core.AmmoContainer;
import com.f708.anothergunmod.core.AmmoContainerRecord;
import com.f708.anothergunmod.registry.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Hands guns out, teaches every pillager how to use one, and decides what a
 * dead gunman leaves on the ground.
 *
 * Arming is two-stage on purpose. Pillager#finalizeSpawn hands itself a crossbow
 * and runs AFTER FinalizeSpawnEvent, so equipping there would just be
 * overwritten; instead the spawn event marks the mob and the join event - which
 * fires once the entity actually enters the level, after finalizeSpawn and after
 * a raid's applyRaidBuffs - does the swap. Marking at spawn rather than at join
 * is what keeps the roll to genuinely new pillagers: ones already saved in the
 * world keep the crossbows they were spawned with.
 */
@EventBusSubscriber(modid = ArmedPillagers.MOD_ID)
public final class PillagerArming {
    private PillagerArming() {}

    /** Set at spawn, consumed at join. Its presence means "roll this one". */
    private static final String TAG_PENDING = "armedpillagers_pending";
    /** Which gun it got, so drops don't depend on the main hand still holding it. */
    private static final String TAG_GUN = "armedpillagers_gun";

    /** Small magazines hold 32, and only ever small bullets. */
    private static final int SMALL_MAGAZINE_CAPACITY = 32;

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Pillager pillager)) {
            return;
        }
        if (event.getSpawnType() == MobSpawnType.EVENT && !ApConfig.ARM_RAID_PILLAGERS.get()) {
            return;
        }
        ArmedPillagers.LOGGER.debug("marking pillager {} for a gun roll (spawn type {})",
                pillager.getUUID(), event.getSpawnType());
        pillager.getPersistentData().putBoolean(TAG_PENDING, true);
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Pillager pillager)) {
            return;
        }

        CompoundTag data = pillager.getPersistentData();
        if (data.getBoolean(TAG_PENDING)) {
            data.remove(TAG_PENDING);
            arm(pillager, data);
        }

        // Given to every pillager, not just armed ones: the goal is inert without
        // a gun in hand, and this way a pillager handed one by a command or
        // another mod can still shoot it.
        addGoal(pillager);
    }

    private static void arm(Pillager pillager, CompoundTag data) {
        PillagerGun gun = PillagerGun.roll(pillager.getRandom());
        if (gun == null) {
            return;
        }
        ArmedPillagers.LOGGER.debug("arming pillager {} with a {}", pillager.getUUID(), gun.id());
        pillager.setItemSlot(EquipmentSlot.MAINHAND, gun.loadedStack());
        pillager.setDropChance(EquipmentSlot.MAINHAND, ApConfig.GUN_DROP_CHANCE.get().floatValue());
        data.putString(TAG_GUN, gun.id());
    }

    private static void addGoal(Pillager pillager) {
        for (WrappedGoal wrapped : pillager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GunAttackGoal) {
                return;
            }
        }
        // Priority 3 is the slot vanilla gives RangedCrossbowAttackGoal, which is
        // exactly what this stands in for.
        pillager.goalSelector.addGoal(3, new GunAttackGoal(pillager, 1.0));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Pillager pillager) || !event.isRecentlyHit()) {
            return;
        }
        PillagerGun gun = PillagerGun.byId(pillager.getPersistentData().getString(TAG_GUN));
        if (gun == null) {
            return;
        }

        RandomSource random = pillager.getRandom();
        Level level = pillager.level();

        unbatterDroppedGun(event);

        if (random.nextFloat() < ApConfig.AMMO_DROP_CHANCE.get()) {
            ItemStack ammo = new ItemStack(gun.ammo());
            int count = Math.min(1 + random.nextInt(ApConfig.AMMO_DROP_MAX.get()), ammo.getMaxStackSize());
            ammo.setCount(count);
            event.getDrops().add(drop(level, pillager, ammo));
        }

        if (random.nextFloat() < ApConfig.MAGAZINE_DROP_CHANCE.get()) {
            event.getDrops().add(drop(level, pillager, loadedMagazine(random)));
        }
    }

    /**
     * Vanilla batters dropped equipment down to a sliver of durability, which
     * would make a rare gun drop worthless. Clamp the wear instead.
     */
    private static void unbatterDroppedGun(LivingDropsEvent event) {
        double cap = ApConfig.MAX_DROPPED_GUN_WEAR.get();
        if (cap >= 1.0D) {
            return;
        }
        for (ItemEntity entity : event.getDrops()) {
            ItemStack stack = entity.getItem();
            if (PillagerGun.of(stack) == null || !stack.isDamageableItem()) {
                continue;
            }
            int maxWear = (int) (stack.getMaxDamage() * cap);
            if (stack.getDamageValue() > maxWear) {
                stack.setDamageValue(maxWear);
            }
        }
    }

    private static ItemStack loadedMagazine(RandomSource random) {
        ItemStack magazine = new ItemStack(ModItems.SMALL_MAGAZINE.get());
        int min = Math.min(ApConfig.MAGAZINE_MIN_ROUNDS.get(), ApConfig.MAGAZINE_MAX_ROUNDS.get());
        int max = Math.max(ApConfig.MAGAZINE_MIN_ROUNDS.get(), ApConfig.MAGAZINE_MAX_ROUNDS.get());
        int rounds = Math.min(min + random.nextInt(max - min + 1), SMALL_MAGAZINE_CAPACITY);

        AmmoContainerRecord record = new AmmoContainerRecord(new AmmoContainer(SMALL_MAGAZINE_CAPACITY));
        ItemStack round = new ItemStack(ModItems.SMALLBULLET.get());
        for (int i = 0; i < rounds; i++) {
            record = record.addNewBullet(round);
        }
        AmmoContainerRecord.setContainerToComponent(magazine, record);
        return magazine;
    }

    private static ItemEntity drop(Level level, Pillager pillager, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, pillager.getX(), pillager.getY() + 0.5D, pillager.getZ(), stack);
        entity.setDefaultPickUpDelay();
        return entity;
    }
}
