# Wurst Fork 项目地图

## 项目目标

- 基于 Wurst Client `v7.50.3-MC1.20.1` 开发 Minecraft 1.20.1 Fabric 客户端模组。
- Java 17 为目标运行时, Yarn mappings 为 `1.20.1+build.10`。
- `FastBreak` 已扩展为 Normal、Animated、Legit 三种模式。

## 根目录结构

```text
Wurst_Fork_BySJia/
├─ .gitignore                         # Git 排除列表
├─ AGENTS.md
└─ 1.20.1_Fabric/
   ├─ Wurst-Client-v7.50.3-MC1.20.1.jar  # 原始输入, 不修改
   ├─ unpacked/                           # JAR 原样解包, 只读参考
   ├─ decompiled/                         # CFR 0.152 反编译结果, 只读参考
   └─ source/                             # 对应版本的可构建源码, 实际开发目录
```

## 可编辑源码地图

所有功能修改均在 `1.20.1_Fabric/source/` 中完成。

```text
source/
├─ build.gradle                 # Loom, 依赖, 测试与打包配置
├─ gradle.properties            # MC, Yarn, Fabric Loader/API 与模组版本
├─ settings.gradle              # Gradle 仓库与插件管理
├─ gradlew / gradlew.bat        # 固定版本 Gradle Wrapper
└─ src/
   ├─ main/
   │  ├─ java/net/wurstclient/
   │  │  ├─ WurstClient.java    # 客户端核心与全局组件
   │  │  ├─ WurstInitializer.java
   │  │  ├─ hack/               # Hack 基类、注册表与列表
   │  │  ├─ hacks/              # 各功能实现, FastBreakHack.java 在此
   │  │  ├─ event/              # 通用事件系统
   │  │  ├─ events/             # 事件接口与事件数据
   │  │  ├─ mixin/              # Minecraft 注入点
   │  │  ├─ mixinterface/       # Mixin 暴露给功能层的接口
   │  │  ├─ settings/           # Checkbox、Slider、Enum 等设置类型
   │  │  ├─ clickgui/           # ClickGUI 与设置组件
   │  │  ├─ commands/           # 命令实现
   │  │  └─ util/               # 方块、渲染、网络等工具
   │  └─ resources/
   │     ├─ fabric.mod.json      # Fabric 模组元数据
   │     ├─ wurst.mixins.json    # Mixin 注册表
   │     ├─ wurst.accesswidener  # Minecraft 成员访问扩展
   │     └─ assets/wurst/        # 图片与翻译
   └─ test/java/                # JUnit 测试
```

## FastBreak 关键路径

- 功能入口: `source/src/main/java/net/wurstclient/hacks/FastBreakHack.java`
- 功能注册: `source/src/main/java/net/wurstclient/hack/HackList.java`
- 挖掘事件: `source/src/main/java/net/wurstclient/events/BlockBreakingProgressListener.java`
- 事件注入: `source/src/main/java/net/wurstclient/mixin/ClientPlayerInteractionManagerMixin.java`
- 设置组件: `source/src/main/java/net/wurstclient/settings/`
- Mixin 清单: `source/src/main/resources/wurst.mixins.json`

当前事件链:

```text
Minecraft ClientPlayerInteractionManager.updateBlockBreakingProgress()
  -> ClientPlayerInteractionManagerMixin
  -> BlockBreakingProgressEvent
  -> FastBreakHack.onBlockBreakingProgress()
  -> sendPlayerActionC2SPacket(STOP_DESTROY_BLOCK)
```

FastBreak 模式:

- `Normal`: 保持原版 Wurst 行为, 使用 `Activation chance` 并立即发送快速破坏包, 不保证裂纹动画。
- `Animated`: 独立于 Normal 和 `Activation chance`, 按 1.0-2.0x 可配置倍率增加每 tick 的原版破坏进度, 默认 1.3x, 保留裂纹动画。1.0x 完全使用原版进度, 高倍率以 `Math.nextDown(1.0F)` 为上限以避免慢速挖掘永久卡在最终裂纹。超过 1.4x 时服务器可能延迟接受或回滚方块。
- `Legit`: 仅清除方块间挖掘冷却, 不加速当前方块。
- 三种模式均在每次 Update 时将 `blockBreakingCooldown` 设为 `0`。

## Render 功能扩展

- X-Ray 与 Search 共用 `BlockUtils.isExposed(BlockPos)` 判断方块是否至少有一面邻接非不透明完整方块。
- Search 的 `Only show exposed` 会在异步结果筛选阶段排除未暴露方块, 设置变化后自动重建渲染缓存。

## 开发约定

- 不编辑或覆盖原始 JAR。
- `unpacked/` 用于核对资源、清单和字节码文件, 不作为源码。
- `decompiled/` 仅用于验证原始 JAR 行为, 不作为构建输入。
- 原始 JAR、`unpacked/` 和 `decompiled/` 已加入根目录 `.gitignore`。
- 业务代码、资源与测试只修改 `source/src/`。
- 保持项目现有 Java 代码风格: Tab 缩进, 大括号换行, 英文半角标点。
- 修改 Mixin 时同步检查 `wurst.mixins.json` 和目标方法签名。
- FastBreak 模式应使用 `EnumSetting`, 避免多个互斥 Checkbox 形成无效组合。
- 不主动执行 `git commit` 或 `git push`; 完成修改并验证后先询问用户。

## 构建与验证

在 `1.20.1_Fabric/source/` 下执行:

```powershell
.\gradlew.bat test
.\gradlew.bat build -x spotlessLicenseHeaderCheck
```

- 构建产物: `source/build/libs/`
- 单元测试报告: `source/build/reports/tests/test/`
- 原版标签的 Spotless 规则会在 2026 年要求一次性更新约 604 个历史文件的版权年份。常规开发验证跳过 `spotlessLicenseHeaderCheck`, 不要为此制造无关的全仓改动。
- 修改 FastBreak 后至少验证 `test`、`build`, 并在 Minecraft 1.20.1 Fabric 环境中手动检查三种模式。
