package dev.oculifelis.careerbees.compat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.villager.Villager;

/** Names that moved between Minecraft versions. This copy is for 26.2. */
public final class Mc {
    public static final EntityType<Creeper> CREEPER = net.minecraft.world.entity.EntityTypes.CREEPER;
    public static final EntityType<Villager> VILLAGER = net.minecraft.world.entity.EntityTypes.VILLAGER;
    public static final EntityType<net.minecraft.world.entity.animal.pig.Pig> PIG = net.minecraft.world.entity.EntityTypes.PIG;
    public static final EntityType<net.minecraft.world.entity.animal.cow.Cow> COW = net.minecraft.world.entity.EntityTypes.COW;
    public static final EntityType<net.minecraft.world.entity.monster.zombie.Zombie> ZOMBIE = net.minecraft.world.entity.EntityTypes.ZOMBIE;

    private Mc() {}
}
