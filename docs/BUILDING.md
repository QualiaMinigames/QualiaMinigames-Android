# 构建与发布

本文件说明本仓库的 CI 需要哪些 Secrets、它们各自对应 `keystore.properties` 的哪个字段，
以及发布一条版本的顺序。

## Secrets 对照表

CI 在 `assembleRelease` 之前把 Secrets 写成本地 `keystore.properties`，所以在本地与 CI
之间切换时，改动只发生在文件生成这一步。

| Secret 名 | 对应 `keystore.properties` 字段 | 内容 |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | `storeFile` | 整个 `.jks` 文件的 Base64，CI 解码后写到 `$RUNNER_TEMP/release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` | 密钥库口令 |
| `ANDROID_KEY_ALIAS` | `keyAlias` | 密钥别名，例如 `qualia-minigames` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` | 密钥口令 |

`storeFile` 之所以是 Base64 而不是路径：CI 的 runner 上没有这个文件，而把它提交进仓库
等于公开了私钥。Base64 让一个 Secret 携带整个文件，解码后落在临时目录里，随 runner 一起
销毁。

## 本地创建签名密钥

密钥库放在仓库之外，例如 `E:\Keys\qualia-minigames-release.jks`：

```powershell
keytool -genkeypair -v -storetype JKS -keystore E:\Keys\qualia-minigames-release.jks -alias qualia-minigames -keyalg RSA -keysize 4096 -validity 10000 -storepass xxx -keypass yyy -dname "CN=QualiaMinigames, OU=QualiaMinigames, O=QualiaMinigames, C=CN"
```

必须用 `-storetype JKS` 并为 `-keypass` 指定与 `-storepass` 不同的口令时，两者才各自
生效；PKCS12 格式只有一套口令，`keyPassword` 会被忽略，构建时表现为
`Given final block not properly padded`。若坚持用 PKCS12，就把 `keyPassword` 写成与
`storePassword` 相同。

然后在本仓库根目录写 `keystore.properties`（该文件不入库）：

```properties
storeFile=E:/Keys/qualia-minigames-release.jks
storePassword=xxx
keyAlias=qualia-minigames
keyPassword=yyy
```

路径用正斜杠：Java 的 `.properties` 把反斜杠当作转义符，写成 `E:\Keys\...` 会被读成
`E:Keys...`，构建时的表现是签名回落到调试密钥库。

## 添加 Secrets

```powershell
$bytes = [System.IO.File]::ReadAllBytes('E:\Keys\qualia-minigames-release.jks')
[Convert]::ToBase64String($bytes) | Set-Content -Path E:\Keys\keystore.b64 -NoNewline
gh secret set ANDROID_KEYSTORE_BASE64 --repo QualiaMinigames/QualiaMinigames-Android --body-file E:\Keys\keystore.b64
gh secret set ANDROID_KEYSTORE_PASSWORD --repo QualiaMinigames/QualiaMinigames-Android
gh secret set ANDROID_KEY_ALIAS --repo QualiaMinigames/QualiaMinigames-Android
gh secret set ANDROID_KEY_PASSWORD --repo QualiaMinigames/QualiaMinigames-Android
```

`gh secret set` 不带 `--body` 时会交互式读取，避免口令进入命令历史。添加完成后删掉
`E:\Keys\keystore.b64`：它只是转存用的中间文件。

## 发布顺序

`Core` → `FFI` → `Android` 是单向依赖，所以版本必须按这个顺序发，否则下游构建会找一个
还不存在的版本。

```powershell
# 1. Core：推标签，CI 校验后发布到 crates.io
git -C ..\Core tag -a v0.2.0 -m "qualia-minigames-core 0.2.0"
git -C ..\Core push origin v0.2.0

# 2. FFI：等 crates.io 上出现 Core 的版本后，再推 FFI 的标签
git -C ..\FFI tag -a v0.2.0 -m "qualia-minigames-ffi 0.2.0"
git -C ..\FFI push origin v0.2.0

# 3. Android：等 crates.io 上出现 FFI 的版本后，推本仓库并推标签
git push origin main
git tag -a v0.2.0 -m "QualiaMinigames-Android 0.2.0"
git push origin v0.2.0
```

本仓库的 CI 默认从 crates.io 取 `qualia-minigames-ffi`，与发布构建看到的是同一份来源。
要验证尚未发布的 FFI，用 `-Pqualia.minigames.ffi.localDir=../FFI` 指向本地检出。

新的 FFI 版本号发布后，`app/build.gradle.kts` 里两处要一起改：`ffiCrateVersion` 的默认值，
以及 `ffiCrateSha256` 的默认值（crates.io 上该版本的 `checksum`）。只改前者会在下载后立刻
被摘要校验拦下。

## 三个仓库的版本号互相独立

`Core` 与 `FFI` 的版本号只对 Rust 依赖有意义，应用的 `versionName` 由
`app/build.gradle.kts` 决定。应用 0.1.0 可以用 Core/FFI 0.1.1：修复在核心库里发版，应用
的对外版本只在界面或行为对用户可见地改变时才动。

本仓库的 `v*` 标签触发 `.github/workflows/release.yml`：先核对标签与 `versionName` 是否
一致，再用 Secrets 里的密钥签出 release 包、校验签名不是调试密钥、把 APK 与它的
`.sha256` 一起传进 GitHub Release。`ci.yml` 只在推 main 与 PR 时运行，推标签不触发它。
