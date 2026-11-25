# QualiaMinigames @VERSION@

接水管、数独、扫雷、数织与数桥五个小游戏的 Android 前端，规则由同一套 Rust 核心库提供。

The Android front end for pipes, sudoku, minesweeper, nonogram and hashi; the rules come from
one Rust core library.

## 下载 / Download

| 文件 / File | 说明 / Description |
|---|---|
| `QualiaMinigames-@VERSION@.apk` | 通用安装包，含 `arm64-v8a`、`armeabi-v7a`、`x86_64` / universal, three ABIs |
| `QualiaMinigames-@VERSION@.apk.sha256` | 上面这个包的 SHA-256 / the digest of the APK above |

要求 Android 8.0（API 26）及以上。 / Android 8.0 (API 26) or later.

## 本次内容 / What is in it

- 新增箭立方：宽、深、高各自 5~30 的实心长方体，由 1×1×1 与 1×2×1 两种方块铺满；每块带一个
  六向箭头，从箭头一端沿方向到边界之间全空时才能取出。生成器先随机铺满，再从满盘随机剥离，
  剥离时给每块选一个当时合法的箭头，因此盘面一定可解。点错次数、暂停保存与恢复、完成判定
  都在规则库中实现；本玩法没有撤销。
  Arrow Cube: a solid box 5 to 30 cells on each side, filled with 1×1×1 and 1×2×1 blocks. Every
  block carries one of six arrows and leaves along it when the cells from its front to the edge
  are empty. The generator fills the box at random, peels it apart, and gives each block an
  arrow that is legal at the moment it is peeled, so every board is solvable. Mistakes, pause
  and resume, and the solved check live in the rules library; the game has no undo.
- 对局页用正交投影的 Compose Canvas 自绘三维场景，单指旋转、双指以棋盘左上与右下角点为锚点
  缩放；只生成外露面，再依次做背面剔除、视口剔除与画家算法排序，放大到模型被视口裁掉时
  沿该方向显示阴影带。
  The play screen draws the scene on a Compose Canvas with an orthographic projection: one
  finger rotates, two fingers zoom about the board's top-left and bottom-right corners. Only
  exposed faces are generated, then back-face culled, viewport culled and painter-sorted; a
  zoomed model that leaves the view gains a shadow band on that side.
- 记录模型改为按玩法分型的 typed record：每个玩法一个记录组，保存自己的参数、最佳时长与历史
  对局；箭立方另存最佳点错次数。JSON 的 `type` 字段分派到各游戏的 codec。旧版本的 `records`
  数组在读取时迁移到新的 `groups` 数组，下一次写入保存为新格式，旧记录不丢失。
  Records are typed per game: each game has a record group with its own parameters, best time
  and history; arrow cube also keeps the mistakes of its best play. The JSON `type` field
  dispatches to the game's codec. An old `records` array is migrated into the new `groups`
  array when read, and the next write stores the new shape, so old records are not lost.
- 主页新增箭立方卡片与等轴测立方体图标；六张卡片、记录页、详情与导出图都显示参数、最佳成绩
  与点错次数。其余五款玩法的规则与操作保持不变。
  The home page gains an Arrow Cube card and an isometric cube mark; six cards, the records
  page, the detail dialog and the exported image show parameters, best scores and mistakes.
  The other five games keep their rules and controls.
## 校验 / Verification

签名证书 `CN=QualiaMinigames`，SHA-256 指纹：

```text
2E:D2:0F:21:82:71:E4:B2:C2:5E:AE:F4:E9:C5:95:BF:D7:7B:41:BC:C6:1B:E7:FC:CD:6B:C7:87:55:84:DD:70
```

安装包的 SHA-256 在同名的 `.sha256` 文件里。
The digest of the APK is in the `.sha256` file beside it.
