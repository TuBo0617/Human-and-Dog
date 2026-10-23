# Human and Dog / 人狗同行

> Two-player co-op for Minecraft **26.3 (Fabric)**: one player is the owner, one is the tamed dog.
> 双人合作玩法：一人当主人，一人当被驯服的狗，15 格拴绳、生死与共、跨维度联动。

## What it does / 玩法

- **One human, one dog.** The first player picks a role; the dog then picks one of the 9 vanilla wolf variants. Everyone else joins as a **spectator**.
  人、狗各一名；狗可选 9 种原版狼变种；人狗都就位后，其他玩家自动进入**观战者**（旁观模式）。
- **15-block leash.** The pair can never be more than 15 blocks apart (3D). Fall off a cliff and the dog hangs in the air at the leash limit, exactly like a vanilla lead.
  15 格拴绳（三维）：超过 12 格后，远离对方的一方变慢、朝对方走的一方略微加速；到 15 格硬性停住，掉下悬崖会像原版拴绳一样挂在空中。
- **Shared fate.** Either one dying cancels the death, freezes both, plays a 3-second countdown and respawns the pair at a shared respawn point.
  生死与共：任意一方死亡都会取消真实死亡，定格双方并倒计时 3 秒后在共享重生点复活。
- **Linked dimensions.** Going through a portal drags the partner along.
  跨维度联动：一方穿过传送门，另一方一起被带过去。
- **Dog rules.** No inventory, no item use, no block breaking, no pickup; 40 HP, 4 damage, wolf armor support, collar dye, sit/stay commands, hunger and saturation locked at full. Mobs ignore the dog until it attacks them — then they fight back, and skeletons avoid it. Boats and beds are allowed.
  狗的限制：无背包、不能用物品、不能破坏方块、不能拾取；40 血、空手 4 点伤害、可穿狼铠、可染项圈、可命令坐下、饥饿与饱和度恒满；生物默认无视狗，但狗先动手就会被记仇还击，骷髅会躲狗；可以上船、可以睡觉。
- **F5** cycles first person / third person back / third person front (first person sits at the wolf's eye height).
  F5 循环第一人称 / 第三人称背面 / 第三人称正面（第一人称的相机位于狼眼高度）。

## Install / 安装

1. Minecraft **26.3** + **Fabric Loader ≥ 0.19.5**
2. **Fabric API** (same MC version)
3. **Java 25**
4. Drop the jar into `mods/`.

## Build / 构建

```bash
./gradlew build          # -> build/libs/human-and-dog-<version>.jar
```

## Localisation / 语言

`zh_cn` and `en_us` are bundled; other languages fall back to English.
自带 `zh_cn`、`en_us` 文案，其他语言回退英文。想加语言只需在
`src/client/resources/assets/human-and-dog/lang/` 里加一个 `<lang>.json`。

## License

CC0-1.0 — do whatever you want with it.
