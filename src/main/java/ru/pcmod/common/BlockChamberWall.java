package ru.pcmod.common;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

public class BlockChamberWall extends Block {
    public BlockChamberWall() { super(Properties.of().strength(3.0F, 10.0F).sound(SoundType.METAL)); }
}
