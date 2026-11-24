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

- 修复棋盘点击位置与响应格子错位半格的问题：接水管、数独与扫雷三块棋盘的命中判定把棋盘角
  当成了第一格的中心，点中一格的中心会判到右边或下边相邻的格子，点在最右或最下一格的后半
  边则没有响应。三块棋盘改为以格心判定，五块棋盘现在共用同一份对齐的判定。
  Fixed a half-cell offset between where a board is drawn and where a tap is answered: on the
  pipes, sudoku and minesweeper boards the hit test took the board's corner for the centre of
  its first cell, so a tap at a cell's centre was answered by the cell to its right or below,
  and a tap on the far half of the last column or row answered nothing. Those boards now
  measure from the centre of the cell, and all five boards share one aligned hit test.
- 其余功能与 0.3.0 相同：
  Everything else is what 0.3.0 had:
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
- 数织：宽与高各自 4~64，单击切换填充、长按切换标 X，点击带 X 的格子先取消 X。某行或某列
  已无法被线索满足时，该线的线索数字转为警示红。生成器先用随机图案算出线索，再用求解器
  确认唯一解；八次尝试内找不到唯一解时回退到首个候选，并在棋盘上方持续说明本题可能有多
  个答案。
  Nonogram: 4 to 64 cells a side, a tap fills a cell and a long press marks it; a click on a
  marked cell clears the mark first. A row or column whose clue can no longer be met turns its
  numbers red. The generator draws a pattern, derives the clues and checks that exactly one
  answer matches them; when eight attempts find none it plays the first one and says above the
  board that the puzzle may have more than one answer.
- 数桥：宽与高各自 4~35 个格点，岛数为格点数的 10%~35%。单击岛切换玩家自己的已解决斜线，
  长按后向同行或同列拖动连接两座岛，桥数在 0、1、2 之间循环。某座岛的桥数超过它的数字，
  或剩余方向已经装不下还缺的桥时，该岛的边框、数字与已解决斜线一并转为警示红。生成器先
  摆岛再用生成树构造隐藏解，线索取各岛度数，只保证有解。
  Hashi: 4 to 35 points a side, with islands covering 10% to 35% of the points. A tap toggles
  the player's own solved stroke; a long press dragged along a row or a column connects two
  islands, cycling their bridges through 0, 1 and 2. An island that has more bridges than its
  number, or that can no longer fit the bridges it still needs, turns its border, its number
  and its solved stroke red. The generator places the islands, joins them with a spanning tree
  and takes each island's degree as its clue, which guarantees an answer.
- 棋盘数字：扫雷、数独、数织与数桥四块棋盘统一用 Cascadia Code，并按字形自身的墨迹居中。
  Board digits: all four boards are set in Cascadia Code and centred on their own ink.
- 种子：五款玩法都可填写种子，留空则随机；记录保存最终种子，点开详情可一键复制。
  Seeds: all five games accept a seed and a blank field starts a random game. A record keeps
  its final seed, which the detail dialog copies in one tap.
- 记录页：按玩法分组的参数卡片，最佳成绩与历史；失败局标记为「失败」且不计入最佳成绩，
  手输种子的记录标记为「手动输入」；可多选导出为图片、删除选中或清空全部。
  Records: cards per game and parameters, best time and history. A loss is marked as failed
  and is not a best time; a hand-typed seed is marked as such. Multi-select export as an
  image, delete or clear.
- 主页：五个玩法各一张卡片，有存档时可直接继续；开始新对局会先确认是否丢弃未完成的对局。
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
