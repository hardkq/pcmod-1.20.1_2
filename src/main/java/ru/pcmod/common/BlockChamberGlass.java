package ru.pcmod.common;

import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.SoundType;

/** BlockBreakable + CUTOUT: render layer теперь задаётся в model json ("render_type": "cutout"). */
public class BlockChamberGlass extends AbstractGlassBlock {
    public BlockChamberGlass() { super(Properties.of().strength(1.0F, 6.0F).sound(SoundType.GLASS).noOcclusion()); }
}
