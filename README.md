<div align="center" style="align-items: center">

# QualiaMinigames

简体中文 | [English](README.en.md)

小游戏集合的 Android 前端。

规则在 [QualiaMinigames-Core](https://github.com/QualiaMinigames/QualiaMinigames-Core)，
跨语言绑定在 [QualiaMinigames-FFI](https://github.com/QualiaMinigames/QualiaMinigames-FFI)，
这个仓库只负责界面。

[![License](https://img.shields.io/github/license/QualiaMinigames/QualiaMinigames-Android?label=License)][License]
[![Releases](https://img.shields.io/github/downloads/QualiaMinigames/QualiaMinigames-Android/total?logo=github&label=Downloads)][Releases]

[License]: LICENSE

[Releases]: https://github.com/QualiaMinigames/QualiaMinigames-Android/releases

</div>

---

## 状态

| 项 | 值 |
|---|---|
| applicationId | `com.qiushui1012.app.minigames` |
| 版本 | `0.1.0`（versionCode 1） |
| minSdk | 26 |
| targetSdk | 36 |
| compileSdk | 35 |
| 界面 | Kotlin + Jetpack Compose，棋盘用 Canvas 自绘 |
| 绑定 | UniFFI 0.32.2 生成的 Kotlin |
| 许可证 | `Apache-2.0` |

玩法为接水管（4 到 35 格，平边与跨边）与数独（标准 9×9，三档难度）。题目全部由种子在
本地生成，不联网。

## 目录与依赖

这个仓库与两个同级的仓库一起构成一个完整的构建，它们各自独立、互不包含：

```text
QualiaMinigames/               （工作区根，三个仓库平铺）
├── Core/                      QualiaMinigames-Core（crate `qualia-minigames-core`）
├── FFI/                       QualiaMinigames-FFI（crate `qualia-minigames-ffi`）
└── Android/                   QualiaMinigames-Android（本仓库）
```

`Core` → `FFI` → `Android` 是单向依赖，拆不开：FFI 导出的是 Core 的类型，本仓库调用的
是 FFI 的接口。

**本仓库不包含另外两个仓库的代码，也没有 submodule。** 发布过的版本依赖 crates.io 上
的 `qualia-minigames-ffi`；开发期改动了 FFI 或 Core 时，用下面两种方式之一让本仓库看到：

```powershell
# 方式一：指向本地 FFI 仓库（推荐，改动立即生效）
./gradlew :app:assembleDebug "-Pqualia.minigames.ffi.localDir=../FFI"

# 方式二：不传参数，构建会下载 crates.io 上 qualia-minigames-ffi 0.1.0 到 app/rust/
./gradlew :app:assembleDebug
```

`app/rust/` 是从 crates.io 解包出来的源码，已在 `.gitignore` 中，删掉即可重新解包。两种
方式都从同一份 Rust 源码生成 Kotlin 绑定，所以绑定的 Kotlin 一半与 Rust 一半永远同源。

## 构建

需要 JDK 21、Android SDK（含 NDK `28.2.13676358`）、Rust 工具链，以及
`cargo-ndk` 与 `uniffi-bindgen`：

```powershell
cargo install cargo-ndk --locked
cargo install uniffi --version 0.32.2 --features cli --locked
```

`local.properties` 写出 SDK 位置（该文件不入库）：

```properties
sdk.dir=D\:\\AndroidSdk
```

然后：

```powershell
./gradlew :app:assembleDebug      # 调试包，用来验证功能
./gradlew :app:assembleRelease    # 发布包，需要 keystore.properties
./gradlew :app:testDebugUnitTest  # 单元测试，含 JVM 上真实调用 Rust 规则的往返测试
```

构建过程会为 `arm64-v8a`、`armeabi-v7a`、`x86_64` 三个 ABI 交叉编译 Rust 库，再用
`uniffi-bindgen` 从刚构建出的库里生成 `com.qiushui1012.app.minigames.ffi` 包下的 Kotlin
绑定。生成目录与 JNI 库目录都在 `app/build/` 下，不提交。

## 签名

密钥库与密码都不进入仓库。本地发布构建从根目录的 `keystore.properties` 读取，该文件已
在 `.gitignore` 中：

```properties
storeFile=E:\\keys\\qualia-minigames-release.jks
storePassword=xxx
keyAlias=qualia-minigames
keyPassword=xxx
```

没有这个文件时，发布构建会退回调试签名并给出警告：能装能跑，但不是可以对外发布的身份。
CI 从仓库 Secrets 写出同一个文件，见 `.github/workflows/`。

## 参与贡献

```powershell
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

提交信息使用英文并遵守 [Conventional Commits](https://www.conventionalcommits.org/)。

## 许可

本项目按 **Apache License 2.0** 授权，全文见 `LICENSE`。

APK 内的 `app/src/main/res/font/cascadia_code.ttf` 是 Cascadia Code 的子集，只含
`0123456789-'":` 十四个字符，用于显示用时。Cascadia Code 以
[SIL Open Font License 1.1](https://scripts.sil.org/OFL) 授权，子集的版权归 Microsoft
Corporation 所有。
