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

	// ---- 年龄 ----
	/** 是否小狗（未成年）。 */
	public boolean baby = false;
	/** 是否已经选过"小狗 / 成年狗"。 */
	public boolean ageConfirmed = false;
	/** 是否被金蒲公英锁住年龄（永远保持小狗）。 */
	public boolean ageLocked = false;
	/** 小狗成长计时（tick）。 */
	public int growthTicks = 0;

	// ---- 寻爱 / 繁殖 ----
	/** 寻爱剩余 tick（>0 表示正在冒爱心并寻找伴侣）。 */
	public int loveTicks = 0;
	/** 靠近伴侣后累计的 tick，用于"过一会儿再生小狗"。 */
	public int breedTicks = 0;
	/** 繁殖冷却（tick）。 */
	public int breedCooldown = 0;

	// ---- 狗身上背的方块（左 / 右侧各一个）----
	/** 左侧方块的 id（空字符串表示没有）。 */
	public String leftBlock = "";
	/** 右侧方块的 id（空字符串表示没有）。 */
	public String rightBlock = "";
	/** 左侧方块对应的容器内容（只有箱子一类才用得到，27 格）。 */
	public java.util.List<ItemStack> leftItems = new java.util.ArrayList<>();
	/** 右侧方块对应的容器内容。 */
	public java.util.List<ItemStack> rightItems = new java.util.ArrayList<>();

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
