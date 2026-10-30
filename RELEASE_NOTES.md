# QualiaMinigames @VERSION@

接水管、数独与扫雷三个小游戏的 Android 前端，规则由同一套 Rust 核心库提供。

The Android front end for pipes, sudoku and minesweeper; the rules come from one Rust core
library.

## 下载 / Download

| 文件 / File | 说明 / Description |
|---|---|
| `QualiaMinigames-@VERSION@.apk` | 通用安装包，含 `arm64-v8a`、`armeabi-v7a`、`x86_64` / universal, three ABIs |
| `QualiaMinigames-@VERSION@.apk.sha256` | 上面这个包的 SHA-256 / the digest of the APK above |

要求 Android 8.0（API 26）及以上。 / Android 8.0 (API 26) or later.

## 本次内容 / What is in it

- 接水管：9～35 格见方，平边与跨边两种布局；点击顺时针、长按逆时针旋转，闭环处管壁转红，水流填充管腔。生成器保证每格最多三臂，不会出现转了也看不出变化的四通管。
  Pipes: 9 to 35 cells a side, flat or wrapped; tap to turn clockwise, long-press anticlockwise. A closed loop turns its walls red, and the water fills the lumen. No cell joins all four sides, so no turn is invisible.
- 数独：简单、中等、困难三档，笔记模式、行列宫高亮、冲突标红，双指缩放与拖动。
  Sudoku: three difficulties, notes, row/column/box highlighting, conflicts in red, pinch to zoom and drag to pan.
- 扫雷：宽与高各自 4~128，雷数可按颗数或占盘面比例填写，普通与无猜两种模式。首次点击的
  3×3（窄盘面为 2×2）内没有雷；无猜模式用计数与子集两条规则判定，找不到可推理的盘面时
  回退普通模式并在棋盘上方说明。踩雷立即结束并揭示全部雷。
  Minesweeper: 4 to 128 cells a side, mines as a count or a share of the board, normal and
  no-guess. The first click protects a 3 by 3 window (2 by 2 on a narrow board). No-guess
  boards are checked with a counting rule and a subset rule and fall back to normal, saying so
  above the board. Opening a mine ends the game and shows every mine.
- 种子：三款玩法都可填写种子，留空则随机；记录保存最终种子，点开详情可一键复制。
  Seeds: all three games accept a seed and a blank field starts a random game. A record keeps
  its final seed, which the detail dialog copies in one tap.
- 记录页：按玩法分组的参数卡片，最佳成绩与历史；失败局标记为「失败」且不计入最佳成绩，
  手输种子的记录标记为「手动输入」；可多选导出为图片、删除选中或清空全部。
  Records: cards per game and parameters, best time and history. A loss is marked as failed
  and is not a best time; a hand-typed seed is marked as such. Multi-select export as an
  image, delete or clear.
- 主页：三个玩法各一张卡片，有存档时可直接继续；开始新对局会先确认是否丢弃未完成的对局。
  Home: one card per game. A save can be continued directly; starting a new game asks before
  discarding it.
- 导出图片：单条为一张卡片，多条为「游玩记录」表格，用时使用等宽字体，导出时间在卡片外底部居中。
  Exports: a single record is one card, several are a "play records" table. Durations are set in a monospaced face, and the export time sits outside the card at the bottom, centred.
- 设置：玩家名、语言（跟随系统 / English / 中文）、主题、音效、震动，以及关于信息。
  Settings: player name, language, theme, sound, haptics, and the about section.
- 应用图标与关于页的标志取自同一份矢量原图。
  The launcher icon and the mark in the about section come from one drawing.

## 校验 / Verification

签名证书 `CN=QualiaMinigames`，SHA-256 指纹：

```text
2E:D2:0F:21:82:71:E4:B2:C2:5E:AE:F4:E9:C5:95:BF:D7:7B:41:BC:C6:1B:E7:FC:CD:6B:C7:87:55:84:DD:70
```

安装包的 SHA-256 在同名的 `.sha256` 文件里。
The digest of the APK is in the `.sha256` file beside it.
