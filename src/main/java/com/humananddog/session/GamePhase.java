package com.humananddog.session;

/**
 * 一局游戏的阶段。
 */
public enum GamePhase {
	/** 等待房主选择当人。 */
	WAITING_HUMAN,
	/** 等待狗选择类型。 */
	WAITING_DOGS,
	/** 游戏进行中参与者掉线，暂停世界等他回来。 */
	WAITING_PARTNER,
	/** 重生倒计时（世界暂停）。 */
	COUNTDOWN,
	/** 正常进行中。 */
	PLAYING;

	public boolean isPaused() {
		return this != PLAYING;
	}
}
