# coloros-airpods-tools

**AirPodsOff**：一个 LSPosed 模块，给 ColorOS 上的 AirPods Pro 2 / Pro 3 开启降噪「关闭」模式。ColorOS 原生只给这两款提供降噪、自适应、通透三档。

> 仅在 OnePlus PLG110 / ColorOS 17（Android 17）/ KernelSU / LSPosed 2.2.1 / AirPods Pro 2 上实测（2026-10）。
> 其他机型和 ColorOS 版本未验证。模块作用于系统蓝牙进程，使用风险自负。

## 安装

从 [Releases](../../releases) 下载 `AirPodsOff.apk`：

1. 安装 APK，在 LSPosed 中启用模块，作用域只勾「蓝牙」（`com.android.bluetooth`）；
2. 重启手机；
3. 连上 AirPods，「我的设备」中的耳机面板会多出「关闭」选项。

万一蓝牙异常，在 LSPosed 里停用这个模块再重启即可恢复。

## 原理

```
「我的设备」App (com.heytap.mydevices)
   │  功能位含 512 才显示「关闭」按钮，点击后下发模式值 1
   ▼
系统蓝牙 (com.android.bluetooth)
   oplus_bluetooth_common_ext.jar → AirPodsManager
   ├─ parseDeviceModel：Pro 2/3 的功能位写死为 1149 (0x47d)，不含 512
   └─ setNoiseMode：发送 AAP 包 04 00 04 00 09 00 0D <mode> 00 00 00
   ▼
AirPods：没有收到「允许关闭」(控制码 0x34) 时，会把「关闭」退回成通透
```

AirPodsOff 做两件事：

1. 给 `AirPodsManager.getDeviceExtendFeatureMask` 的返回值补上 512，界面就会显示「关闭」；
2. 在连接握手之后、以及每次切换到「关闭」之前，发送 `04 00 04 00 09 00 34 01 00 00 00`（允许关闭）。

`AirPodsManager` 所在的 jar 由 `OplusBTFactory` 在运行时通过 `BaseDexClassLoader.addDexPath` 加载，所以模块先 Hook `addDexPath`，等 jar 加载后再挂上其余钩子。

查看日志：`su -c 'grep AirPodsOff /data/adb/lspd/log/modules_*.log'`

### 系统更新后失效时怎么排查

模块依赖以下方法名：`getDeviceExtendFeatureMask`、`setNoiseMode`、`getDeviceOutputStream`，以及 `lambda$handleAirPodsSocket$3` / `$10`（握手）和 `lambda$handleAirPodsSocket$9`（写包）。

1. 从手机拉取 `/system_ext/framework/oplus_bluetooth_common_ext.jar`，用 jadx 查看 `com.oplus.bluetooth.feature.airpods.AirPodsManager` 里这些方法是否改了名；
2. 用 `probe/Anc.java` 读取功能位、切换模式来验证，用法见该文件开头的注释。

## 自行编译

不需要 Android SDK，只要 JDK 11+、curl 和 zip，并用 adb 连上手机：

```bash
./build.sh            # 产物在 release/AirPodsOff.apk
./build.sh install    # 编译后直接 adb 安装
```

首次运行时，脚本会：
- 从 Maven 下载 aapt2、d8（r8）、apksig 和 Xposed API；
- 从手机拉取 `framework-res.apk`；
- 生成签名密钥 `lsposed-module/key.p12`。

密钥已加入 `.gitignore`，不会提交。以后覆盖升级必须用同一个密钥签名。另外，你自己编译出的 APK 和 Release 里的签名不同，两者不能互相覆盖安装。

## 目录

| 路径 | 内容 |
|---|---|
| `lsposed-module/` | AirPodsOff 模块源码 |
| `probe/Anc.java` | 调试工具：root 下通过 `app_process` 调用 `OplusBluetoothDevice` 读写降噪模式 |
| `tools/Sign.java` | 基于 apksig 的 v1+v2 签名工具 |
| `build.sh` | 一键编译和签名 |

## License

MIT
