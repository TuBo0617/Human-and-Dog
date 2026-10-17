# 上架文案 · CurseForge / Modrinth

> 一份可直接复制粘贴的发布素材。中英双语，两个平台共用同一套文案，按平台字段裁剪即可。
> Ready-to-paste listing copy for both platforms (EN + ZH).

- Mod id：`human-and-dog`
- 版本：`1.0.0`
- 支持：Minecraft **26.3** / Fabric Loader **≥ 0.19.5** / Java **25**
- 依赖：**Fabric API**（同游戏版本，必装）
- 许可证：**CC0-1.0**
- 环境：客户端 + 服务端（单人 / 局域网 / 专用服务器均可）

---

## 1. 标题 / Title

| 字段 | 值 |
|---|---|
| CurseForge 名称 | `Human and Dog (人狗同行)` |
| Modrinth Title | `Human and Dog` |
| Modrinth Slug | `human-and-dog` |

> 平台搜索主要吃英文名，所以标题用英文，中文放括号里；描述里再强调中文名。

## 2. 一句话简介 / Summary

**EN（Modrinth ≤256 字符）**
> Two-player co-op: one plays the owner, one plays the tamed dog. A 15-block leash you cannot break, shared life and death, linked dimensions, spectators welcome.

**ZH**
> 双人合作：一人当主人，一人当被驯服的狗。15 格拴不住的拴绳、生死与共、跨维度联动，其他人可当观战者。

## 3. 详情描述 / Description

### English

```markdown
# Human and Dog

**Two players, one leash.** One of you is the human. The other is the dog — not a mob,
an actual player who walks on all fours, picks a wolf variant, wears wolf armor and answers
to their owner's right click.

## What makes it different

- **A leash that actually holds.** The pair can never be more than **15 blocks** apart in 3D.
  Fall off a cliff and the dog *hangs in the air* at the leash limit instead of dying.
  Beyond 12 blocks, the one running away slows down while the one coming home speeds up.
- **Shared fate.** Either death is cancelled: both freeze, a 3-second countdown runs, and you
  respawn together at a shared respawn point. One bed, one fate.
- **Linked dimensions.** Walk through a portal and your partner is dragged through with you.
- **Real dog rules.** 40 HP, 4 damage bare-pawed, wolf armor grants 11 armor, dye the collar,
  order the dog to sit, hunger and saturation are always full. No inventory, no item use, no
  block breaking, no pickup — but boats, beds and naps are allowed. Mobs ignore the dog until it
  attacks them; once it does, they fight back (and skeletons still flee from it).
- **Spectators.** Human and dog slots are one each. Everyone else joins in spectator mode and
  can watch the run; the role screen greys out roles that are already taken.

## How to play

1. Create a world (or join a server) with Fabric API installed.
2. The first player picks **human** or **dog**; the dog then picks one of the 9 wolf variants.
3. Share the world over LAN (or run a dedicated server) and have a friend join.
4. When both slots are filled the game starts with a "Welcome!" message in chat.

Default key: **F6** toggles the dog's first/third person view.

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.19.5+**
- Fabric API
- Java **25**

## Languages

English and Simplified Chinese are bundled. Other locales fall back to English.

## Notes

- The 15-block leash also limits the human, so the two of you are always within shouting distance.
- Spectators can fly around freely; they are not leashed, not killed by the linked-death rule and
  are teleported next to the pair when the run starts.
```

### 中文

```markdown
# 人狗同行（Human and Dog）

**两个人，一根拴绳。** 一个当主人，另一个当狗 —— 不是怪物，是真正用四肢走路的玩家：
可以选择狼变种、穿狼铠、被主人右键喂食染色、听主人的话坐下。

## 特色

- **真的拴得住。** 人狗三维距离永远不超过 **15 格**。狗掉下悬崖会在 15 格处**悬在半空**，
  不会摔死 —— 和原版拴绳一样的观感。距离超过 12 格后，正在远离的一方移动变慢，
  朝对方走的一方略微加速，回到 12 格内立即恢复。
- **生死与共。** 任意一方死亡都会被取消：双方定格、3 秒倒计时后一起在共享重生点复活。
  一张床，一条命。
- **跨维度联动。** 一方走进传送门，另一方会被一起拽过去。
- **完整的狗规则。** 40 血、空手 4 点伤害、狼铠 +11 护甲、可染项圈、可命令坐下、饥饿与饱和度恒满；
  没有背包、不能用物品、不能破坏方块、不能拾取 —— 但可以上船、可以睡觉。
  生物默认无视狗，狗一旦先动手就会被记仇围攻（骷髅依然会躲着狗走）。
- **观战者。** 人和狗各只有一个名额；人狗都就位后，后来进入的玩家自动进入旁观模式观战，
  角色界面上已被占用的选项会置灰。

## 怎么玩

1. 装好 Fabric API，新建世界（或进入已装模组的服务器）。
2. 第一位玩家选择「当人」或「当狗」；选狗的人再挑一种狼变种。
3. 通过「对局域网开放」或专用服务器拉第二位玩家进来。
4. 人和狗都就位后，聊天框会显示「欢迎游玩！！！」并正式开局。

默认按键：**F6** 切换狗的第一 / 第三人称视角。

## 前置

- Minecraft **26.3**
- Fabric Loader **0.19.5+**
- Fabric API
- Java **25**

## 语言

自带简体中文与英文；其他语言回退英文。

## 说明

- 15 格拴绳对人同样生效，所以两个人永远在彼此的视线范围附近。
- 观战者可以自由飞行，不受拴绳、不参与死亡联动，开局时会被传送到人狗身边。
```

## 4. 分类与标签 / Categories & tags

| 平台 | 建议值 |
|---|---|
| CurseForge · Category | `Gameplay`（主）、`Adventure`（次） |
| CurseForge · Loaders | `Fabric` |
| CurseForge · Game version | `26.3` |
| Modrinth · Categories | `gameplay`, `adventure`, `social` |
| Modrinth · Loaders | `Fabric` |
| Modrinth · Environment | `Client and server` |
| Modrinth · Game versions | `26.3` |
| 关键词 / Keywords | `co-op`, `multiplayer`, `leash`, `wolf`, `dog`, `roleplay`, `双人`, `合作`, `拴绳`, `狼` |

> 两个平台都支持 `Modrinth/CurseForge 项目关系`：把 **Fabric API** 标成 Required dependency，
> 加载器标 Fabric，环境标 Client+Server。

## 5. 更新日志 / Changelog (1.0.0)

```markdown
### 1.2.0 — dog combat & comfort
- Dog hunger and saturation are locked at full
- Mobs now retaliate against the dog after it attacks them (they still ignore it otherwise)

### 1.0.0 — first release
- Two roles: human + dog (9 wolf variants), spectators for everyone else
- 15-block 3D leash: hard cap, cliff hang, distance-based slow/speed at 12+ blocks
- Linked death with 3s freeze + countdown and shared respawn point
- Linked dimensions through portals
- Dog rules: 40 HP, 4 damage, wolf armor, collar dye, sit, no inventory/items/blocks/pickup
- Wolf eye height for aiming, walking animation, team-based invisibility fix
- zh_cn + en_us localisation
```
```markdown
### 1.2.0 — 狗的战斗力与舒适度
- 狗的饥饿值与饱和度锁定为满值
- 狗攻击敌对/中立生物后会被记仇反击（不主动惹事则依旧被无视）

### 1.0.0 — 首次发布
- 双身份：人 + 狗（9 种狼变种），其余玩家观战
- 15 格三维拴绳：硬上限、悬崖悬停、12 格外按移动方向减速/加速
- 死亡联动：定格 + 3 秒倒计时 + 共享重生点
- 传送门维度联动
- 狗规则：40 血、4 伤害、狼铠、项圈染色、坐下、无背包/物品/破坏/拾取
- 瞄准使用狼眼高度、走路动画、同队隐形导致的"半透明人影"修复
- 简体中文 + 英文文案
```

## 6. 发布前检查清单 / Pre-publish checklist

- [ ] `gradle.properties` 的 `version` 已更新，`./gradlew build` 通过
- [ ] `fabric.mod.json` 的 name/description 已本地化（英文 + 中文）
- [ ] `assets/human-and-dog/icon.png`（建议 128×128 以上）
- [ ] 至少 3 张截图：角色选择界面、狗挂在悬崖、人狗同框
- [ ] 一段 20–40 秒演示视频（挂牌照/悬崖悬停/死亡联动各 5 秒）
- [ ] 依赖关系：Fabric API = Required
- [ ] 许可证字段与仓库 LICENSE 一致（CC0-1.0）
- [ ] 中英文描述分别贴到 CF 的「Description」与 MR 的「Description」
- [ ] 版本号/channel：`Release`
- [ ] 发布后在 CF 的「Issues」/ MR 的「Issues」贴上反馈地址

## 7. 一句话卖点（用于封面/视频标题）

- EN: *One human. One dog. Fifteen blocks. No more.*
- ZH: *一人一狗，十五格，生死与共。*
