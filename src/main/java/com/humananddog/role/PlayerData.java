package com.humananddog.role;

import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/**
 * 单个玩家在本局游戏中的角色数据。
 */
public class PlayerData {
	public final UUID uuid;
	public Role role = Role.NONE;
	/** 狗选定的狼变种标识符，如 minecraft:woods。 */
	public String dogVariant = "";
	/** 狗是否已经完成变种选择。 */
	public boolean variantConfirmed = false;
	/** 项圈颜色（DyeColor 名称）。 */
	public String collarColor = "red";
	/** 主人命令狗坐下。 */
	public boolean sitting = false;
	/** 狗身上穿戴的狼铠（玩家没有 BODY 槽，单独保存）。 */
	public ItemStack wolfArmor = ItemStack.EMPTY;
	/** 坐下时锁定的坐标。 */
	public double sitX, sitY, sitZ;
	public boolean sitPosSet = false;

	public PlayerData(UUID uuid) {
		this.uuid = uuid;
	}

	public boolean isDog() {
		return role == Role.DOG;
	}

	public boolean isHuman() {
		return role == Role.HUMAN;
	}
}
