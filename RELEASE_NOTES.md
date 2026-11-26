# QualiaMinigames @VERSION@

接水管、数独、扫雷、数织、数桥与箭立方六个小游戏的 Android 前端，规则由同一套 Rust 核心库提供。

The Android front end for pipes, sudoku, minesweeper, nonogram, hashi and arrow cube; the rules
come from one Rust core library.

## 下载 / Download

| 文件 / File | 说明 / Description |
|---|---|
| `QualiaMinigames-@VERSION@.apk` | 通用安装包，含 `arm64-v8a`、`armeabi-v7a`、`x86_64` / universal, three ABIs |
| `QualiaMinigames-@VERSION@.apk.sha256` | 上面这个包的 SHA-256 / the digest of the APK above |

要求 Android 8.0（API 26）及以上。 / Android 8.0 (API 26) or later.

## 本次内容 / What is in it

0.4.1 是箭立方的修复版本，其余五款玩法保持不变。
0.4.1 is a patch for Arrow Cube; the other five games are unchanged.

- 取出的方块改为恒速直线飞出，完全离开渲染区域后才消失；若超过原时长两倍仍未离开，则在
  500ms 内渐隐。极端视角下不会再看到方块停在屏幕里。
  Taken blocks now leave in a straight line at constant speed and disappear only after they
  are entirely outside the render area. If one is still visible after twice the old duration,
  it fades over 500 ms instead of stopping on screen.
- 失败撞击先按恒速接近，接触后按恒速返回，并在返回末段缓出；恒速与缓出的衔接处速度连续。
  A blocked block approaches at constant speed, holds briefly at the contact, then returns at
  constant speed and eases into its cell without a velocity jump at the seam.
- 点击不再被整段动画拦截：成功取出 75ms 后即可继续操作，失败动画只拦截落在该动画路径上的
  失败点击。多个飞出与撞击动画可以同时播放。
  Taps are no longer blocked for a whole animation: a successful take releases input after
  75 ms, and a failed take only ignores further failed taps on that animation's path. Several
  flights and bumps can run at once.
- 修复箭立方的面绘制：2 宽方块在洞口和视口左右边缘不再被错误遮盖或剔除，长面按面中心深度
  排序，外露判断改为逐格扫描。
  Fixes Arrow Cube face drawing: two-cell faces at holes and view edges are no longer painted
  over or culled, long faces sort by their centre depth, and exposure is scanned per cell.
- 修正旋转方向相反的问题。
  Fixes the reversed drag-rotation direction.

## 校验 / Verification

签名证书 `CN=QualiaMinigames`，SHA-256 指纹：

```text
2E:D2:0F:21:82:71:E4:B2:C2:5E:AE:F4:E9:C5:95:BF:D7:7B:41:BC:C6:1B:E7:FC:CD:6B:C7:87:55:84:DD:70
```

安装包的 SHA-256 在同名的 `.sha256` 文件里。
The digest of the APK is in the `.sha256` file beside it.
