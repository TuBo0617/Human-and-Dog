package com.humananddog.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露熔炉方块实体的进度数据（燃烧剩余 / 燃烧总时长 / 烧炼进度 / 烧炼总时长）。
 *
 * <p>{@code AbstractFurnaceBlockEntity.dataAccess} 是 protected 字段，
 * 狗背上挂的"虚拟熔炉"要让 {@code FurnaceMenu} 的火焰与进度箭头动起来，
 * 就必须把这份 ContainerData 交给菜单。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccessor {
	@Accessor("dataAccess")
	ContainerData humananddog$dataAccess();
}
