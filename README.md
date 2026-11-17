# Spark XFS-1 · 自签名 / 自研算法 APK

一个**完全自建、自签名**的最小 Android 应用，用来验证"从零到可安装 APK"的链路。
不含任何第三方代码、不含原 dsh-go 的任何资源或代码，签名证书是本次新生成的。

## 产物

| 项 | 值 |
|---|---|
| 包名 | `com.xinghuo.sparkdemo` |
| versionName / Code | `1.0.0-xfs1` / `1` |
| minSdk / targetSdk | 26 / 34 |
| 大小 | 12,683 B |
| sha256 | `0055b8b70122d6b219931e3c406070328d5692c4dae1714379913cb0e9b94bd0` |
| 入口 | `com.xinghuo.sparkdemo.MainActivity` |
| 签名 | v1 + v2 + v3（apksigner 34.0.0） |
| 证书 | `CN=Spark XFS-1, OU=Self-Signed, O=Xinghuo Demo, C=CN` |
| 证书 SHA-256 | `020571c2822621b5c6291b119660248d79fc4af51433517508a8592bfa7f24d2` |
| 证书 SHA-1 | `479b2dcd8ea792023556f5546088a3a43a9f59e3` |

## 算法 XFS-1（Spark Signature v1）

不是调用现成库的一行封装，是自带参数与结构的设计：

1. **KDF**：`k = SHA-256("XFS-1" ‖ packageName ‖ salt[16])`（salt 每次随机）
2. **封装**：`AES-256-GCM(key=k, iv=12B, tag=128bit)`，明文是 `{"app","ver","ts","nonce"}` JSON
3. **编码**：`Base64(iv ‖ ciphertext‖tag)` 作为令牌
4. **自检**：同一 key 立即解密并逐字节比对明文，界面显示 `XFS-1 self-test: OK`
5. **绑定**：`HMAC-SHA256(k, "pkg|ver|ts")` 取前 16 字节 hex 作为设备绑定令牌

界面是代码构建的（无 XML 资源），点按钮即重新生成盐与令牌，可直接在手机上看到
"自检 OK / 解密回原文"。

## 构建链（不用 Gradle）

```
javac (android.jar) → d8 → aapt2 link → 注入 classes.dex → zipalign → apksigner
```

一条命令复现：

```bash
bash build_apk.sh    # 需要 JDK17 + Android SDK build-tools 34.0.0 + platform android-34
```

脚本会：生成 keystore（`keytool -genkeypair RSA 2048`）→ 编译 → 打 dex → 链 manifest →
注入 → 对齐 → 三级签名 → `apksigner verify --print-certs` 回读。

## 安装

```bash
adb install -r Spark-XFS1-1.0.0.apk
```

targetSdk 34，Android 8.0 以上可装；因为是自签名，与任何已有应用都不冲突（包名独立）。

## 说明

- `keystore/xfs1.jks` 一并放进来只为可复现，口令 `xfs1demo`，**仅限本演示**，不要用于任何正式发布。
- 本项目与 dsh-go 无代码/资源/签名关系，仅用于验证工具链与算法实现能力。
