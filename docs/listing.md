# 上架文案 · CurseForge / Modrinth

> 一份可直接复制粘贴的发布素材。中英双语，两个平台共用同一套文案，按平台字段裁剪即可。
> Ready-to-paste listing copy for both platforms (EN + ZH).

- Mod id：`human-and-dog`
- 版本：`1.5.0`
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
  order the dog to sit. No inventory, no item use, no block breaking, no pickup — but boats,
  beds and naps are allowed. Mobs ignore the dog until it attacks them; once it does, they fight
  back (and skeletons still flee from it).
- **No hunger, only handouts.** The dog has no hunger or saturation at all. It never regenerates
  on its own and never starves — the only way to heal is for the owner to hand it food. Sprinting
  is unlimited. Golden apples, enchanted golden apples and suspicious stew all apply their real
  effects to the dog.
- **Puppies and family.** After picking the dog role you choose puppy or adult, then a wolf
  variant. Puppies grow up in 20 minutes, or feed a golden dandelion to keep them small forever.
  Feed a full-health dog and it falls in love — a tamed wolf nearby walks over and, after a
  moment, gives birth to a puppy that belongs to you.
- **A real body.** The dog uses the vanilla wolf hitbox (0.6 × 0.85, puppies 0.3 × 0.425), fits
  through one-block gaps, and can be pushed by players and mobs. You can hit it too — damage runs
  through the normal rules, so it triggers the shared-death countdown.
- **Bark.** Press **V** to bark with the vanilla wolf sound (dogs only; humans and spectators get
  nothing). Being hit plays the vanilla wolf hurt sound as well.
- **Spectators.** Human and dog slots are one each. Everyone else joins in spectator mode and
  can watch the run; the role screen greys out roles that are already taken.

## How to play

1. Create a world (or join a server) with Fabric API installed.
2. The first player picks **human** or **dog**; the dog then picks puppy or adult, and one of the 9 wolf variants.
3. Share the world over LAN (or run a dedicated server) and have a friend join.
4. When both slots are filled the game starts with a "Welcome!" message in chat.

Default keys: **F5** cycles first person / third person back / third person front; **V** barks.

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
- **完整的狗规则。** 40 血、空手 4 点伤害、狼铠 +11 护甲、可染项圈、可命令坐下；
  没有背包、不能用物品、不能破坏方块、不能拾取 —— 但可以上船、可以睡觉。
  生物默认无视狗，狗一旦先动手就会被记仇围攻（骷髅依然会躲着狗走）。
- **没有饱食度，只能靠投喂。** 狗彻底没有饥饿与饱和度：不会自然回血、也不会饿死，
  唯一的回血方式是主人喂食。疾跑不受饥饿限制。金苹果、附魔金苹果、谜之炖菜都对狗生效。
- **小狗与繁衍。** 选完「当狗」后先选小狗或成年狗，再选狼变种。小狗 20 分钟后长大，
  也可以喂金蒲公英让它永远是幼犬。给满血的狗喂食会让它进入寻爱状态，
  附近的驯服狼会走过来，过一会儿生下一只属于你的小狗。
- **真正的身体。** 狗使用原版狼的碰撞箱（成年 0.6×0.85，幼犬 0.3×0.425），能穿过一格高的缝隙，
  也会被玩家和生物挤压。人也可以打狗，伤害走原版流程，因此会触发死亡联动倒计时。
- **狗叫。** 按 **V** 发出原版狼叫（只有狗能用，人和观战者按了没反应）；被打时同样播放原版狼的受伤音效。
- **观战者。** 人和狗各只有一个名额；人狗都就位后，后来进入的玩家自动进入旁观模式观战，
  角色界面上已被占用的选项会置灰。

## 怎么玩

1. 装好 Fabric API，新建世界（或进入已装模组的服务器）。
2. 第一位玩家选择「当人」或「当狗」；选狗的人再选小狗或成年狗，然后挑一种狼变种。
3. 通过「对局域网开放」或专用服务器拉第二位玩家进来。
4. 人和狗都就位后，聊天框会显示「欢迎游玩！！！」并正式开局。

默认按键：**F5** 循环第一人称 / 第三人称背面 / 第三人称正面；**V** 狗叫。

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

## 5. 更新日志 / Changelog (1.4.1)

```markdown
### 1.4.1 — hotfix

**Fixed**
- Jade and other WAILA-style mods could not show the dog's health, effects or status at all:
  the dog player was filtered out as an invisible entity, so the crosshair never locked onto it.
- The dog's player body was drawn as a translucent ghost on top of the wolf when something
  (such as an info mod) forced it to be considered visible.

### 1.4.0 — commands, camera and a calmer ride

**Added**
- `/hd role <human|dog|spectator>` — change your role while the game is still waiting to start.
  Roles already taken stay greyed out; once the game starts the command is locked.
- `/hd age` — dogs only: reopen the puppy/adult + variant picker and apply the new look immediately.
- `/hd swap` — operators only: swap the human and the dog when both are online.
- Press **T** on the waiting screen to type commands.
- The dog shakes itself dry after leaving water, just like a vanilla wolf.

**Changed**
- The camera is cycled with **F5** again (first person / third person back / third person front).
- F3+F4 can open the game-mode switcher again.

**Fixed**
- A non-host player leaving now pauses the world and shows the waiting screen until they return.
- New players saw role buttons that looked clickable even when the slot was already taken.
- The dog's body jittered while riding a boat (much worse as a passenger).
- A dog told to sit could still walk around on its own screen while everyone else saw it sitting.
- Jade and other WAILA-style mods now show the dog's real health, effects and status — the crosshair
  targets the actual player instead of the visual wolf.
- Rejoining a saved world could leave both players stuck on the waiting screen even after the dog
  arrived; the dog list is now derived from the actual roles instead of a cached index.

### 1.3.0 — puppies, handouts and a real body

**Added**
- Choose the dog's age (puppy / adult) right after picking the dog role, then pick a wolf variant.
  Puppies grow up after 20 minutes; feed a golden dandelion to lock the age forever (and again to unlock).
- Love mode: feeding a dog at full health makes hearts float above its head. If a tamed wolf
  nearby is in love too, it walks over and after a moment gives birth to a puppy owned by the dog player.
- Golden apples, enchanted golden apples and suspicious stew now apply their real effects to the dog.
- Bark keybind (default **V**) — dogs only, vanilla wolf sound. Being hit plays the vanilla wolf hurt sound too.
- The dog now has a real hitbox (0.6 × 0.85, puppies 0.3 × 0.425) that players and mobs can push against.
- Humans can now hit the dog; damage runs through the normal rules, so it can trigger the shared-death countdown.

**Changed**
- Hunger and saturation are gone: the hunger bar is hidden, there is no natural regeneration and
  no starvation. Health only comes back from food the owner hands over (2 × nutrition, same as
  vanilla wolf feeding).
- Dogs can sprint forever without saturation.
- Adult and puppy dogs both fit through one-block-high gaps.
- The dog uses vanilla wolf ambient/hurt sounds instead of human ones.

**Fixed**
- Looking at an enderman's eyes as a dog did not anger it.
- The human silhouette flashed for a frame when the dog crouched or stood up.
- Wolf armor could not be equipped by right-clicking the dog, and shears could not take it off.
- Wolf armor covered the dog's first-person view.
- With hitboxes shown (F3+B) the dog still could not enter one-block gaps — the server kept a
  stale 1.8-high size cache. It is now re-checked every tick.
- Mobs no longer target the dog out of nowhere; they retaliate only after the dog hits them.

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
### 1.4.1 — 热修复

**修复**
- Jade（玉）等信息显示 mod 完全无法显示狗的血量、药水效果与状态：狗玩家被当成隐形实体过滤掉了，
  准星永远锁定不到它。
- 狗玩家的本体被以半透明幽灵的形式叠加画在替身狼上（凡是让它"被视为可见"的场景都会触发）。

### 1.4.0 — 指令、视角与更稳的骑乘

**新增**
- `/hd role <human|dog|spectator>`：开局前修改自己的身份；已被占用的身份置灰不可选，开局后该指令锁定。
- `/hd age`：只有狗能用，重新打开「小狗/成年狗 + 类型」选择并立即生效。
- `/hd swap`：需要管理员权限，人和狗同时在线时互换身份。
- 等待界面按 **T** 可以直接输入指令。
- 狗离开水面后会像原版狼一样甩水。

**改动**
- 视角改回用 **F5** 循环（第一人称 / 第三人称背面 / 第三人称正面）。
- F3+F4 重新可以打开游戏模式切换。

**修复**
- 非房主玩家退出后会暂停世界并显示等待界面，回来即继续。
- 新玩家在角色界面看到本已被占用的身份仍然可点。
- 狗在船上身体抖动（当乘客时更严重）。
- 被命令坐下的狗在自己视角里还能到处走。
- 装了 Jade（玉）等信息显示 mod 后，准星对着狗无法显示血量、药水效果与状态；现在准星命中真实的
  狗玩家而不是替身狼。
- 重新进入已保存的世界后，狗已经进服却仍然卡在「等待其他玩家加入」；狗名单改为按真实角色推导，
  不再依赖可能失同步的缓存索引。

### 1.3.0 — 小狗、投喂与真正的身体

**新增**
- 选完「当狗」后先选年龄（小狗 / 成年狗），再选狼变种；小狗 20 分钟后长大，
  喂金蒲公英可永久锁住幼犬形态（再喂一次解锁）。
- 寻爱：满血时被喂食会头顶冒爱心。附近若有同样在寻爱的驯服狼，它会走过来，
  过一会儿生下一只归属于狗玩家的小狗。
- 金苹果、附魔金苹果、谜之炖菜对狗生效，获得对应原版效果。
- 新增「狗叫」按键（默认 **V**），只有狗能用，播放原版狼叫；被打时也会播放原版狼的受伤音效。
- 狗拥有真正的碰撞箱（成年 0.6×0.85，幼犬 0.3×0.425），可以被玩家和生物挤压。
- 人可以攻击狗；伤害走原版流程，因此会触发死亡联动倒计时。

**改动**
- 删除饱食度与饱和度：饥饿条隐藏、不再自然回血、不会饿死；血量只能靠主人喂食恢复
  （每份食物回 2×营养值，与原版喂狼一致）。
- 狗无需饱和度即可无限疾跑。
- 成年狗与小狗都能穿过一格高的空间。
- 狗的叫声、受伤声改用原版狼音效，不再是人的声音。

**修复**
- 狗直视末影人眼睛时不再无法激怒末影人。
- 狗蹲下 / 起身的瞬间不再闪现人形。
- 狼铠可以右键给狗穿上，也可以用剪刀右键取下。
- 穿狼铠后不再遮挡狗的第一人称视野。
- 开启碰撞箱显示（F3+B）时狗仍进不去一格高的空间（服务端尺寸缓存停在 1.8 高），现已每 tick 自愈。
- 生物不再无缘无故把狗当目标，只有狗先动手才会记仇还击。

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
