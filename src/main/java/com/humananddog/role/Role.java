package com.humananddog.role;

/**
 * 玩家的身份。
 */
public enum Role {
	/** 还没选。 */
	NONE,
	/** 主人。 */
	HUMAN,
	/** 狗。 */
	DOG,
	/** 观战者（旁观模式）。 */
	SPECTATOR;

	public static Role byName(String name) {
		for (Role role : values()) {
			if (role.name().equalsIgnoreCase(name)) {
				return role;
			}
		}
		return NONE;
	}

	public boolean isParticipant() {
		return this == HUMAN || this == DOG;
	}
}
