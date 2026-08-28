# Wurst Fork 项目记忆

## 项目版本

- `1.20.1_Fabric/source/` 是旧版 Minecraft 1.20.1 Fabric 开发目录。
- `26.2_Fabric/` 是从 `Wurst-Client-v7.55-MC26.2-sources.jar` 移植出的新版 Fabric 工程。
- 26.2 模组版本和归档名称已改为 `Wurst-Fork-BySJia`。
- 根目录的 26.2 输入 JAR、Gradle 缓存和构建输出不应纳入 Git。

## 已完成的魔改

### Search

- `Blocks` 使用和 X-Ray 相同的多选方块列表界面, 支持同时搜索多个方块。
- 增加 `Show block textures` 开关, 开启后只提交搜索目标的原始方块材质, 不改变其他方块透明度。
- 开启 `Show block textures` 时隐藏原本的彩色闪烁方框层, 只保留材质显示。
- Search 材质层使用 `FULL_BRIGHT` 单独提亮目标方块, 不修改全局 Gamma, 其他方块保持原版亮度。
- Search 材质模型通过 Fabric/Iris 标准提交入口发送到原版 `cutoutMovingBlock` 方块纹理层, 避免实体材质层产生白边。
- 彩色方框和材质渲染共用完整的不可变结果快照, 搜索缓存异步重建期间不会读取正在变化的匹配集合。
- 增加 `Only show exposed` 设置。
- X-Ray 和 Search 共用 `BlockUtils.isExposed(BlockPos)`。
- 区块加载、区块卸载、方块更新和区块数据更新会触发相关渲染缓存刷新。
- Search 的异步搜索结果会在区块实际可用后更新, 避免新区块加载后长时间显示旧缓存。
- 26.2 版本保留了这些修复和设置。

### FastBreak

- 提供 `Normal`、`Animated`、`Legit` 三种模式。
- `Animated` 按可配置倍率增加原版挖掘进度并保留裂纹动画。
- `Legit` 只清除方块间挖掘冷却, 不加速当前方块。
- 三种模式都会在更新时清除 `blockBreakingCooldown`。

### BoatFly

- 增加 `Normal` 和 `Safe` 模式。
- `Safe` 只处理 `BoatEntity`, 使用渐进加速和速度上限。
- Safe 档位重新分配为: `Conservative=(1.5, 1.5, 0.05, 0.05)`、`Balanced=(3.0, 3.0, 0.10, 0.10)`、`Aggressive=(5.0, 5.0, 0.20, 0.20)`, 顺序为水平上限、垂直上限、水平加速度、垂直加速度。
- `Safe` 现在可通过 `Ascent Ticks` 和 `Break Ticks` 设置上升周期与打断持续时间, 默认分别为 35 和 1 ticks。
- 打断阶段使用极轻微下降 (`-0.01`), 替代之前的 3 ticks 硬清零, 然后允许下一轮上升。
- Safe 的上升计时和中性阶段会在载具接触地面或水面后重置。
- 当前没有加入额外位置包或载具包逻辑。
- 服务器仍可能根据自身载具规则拒绝长时间空中移动, 需要实际服务器测试。

### Logo 和版本标识

- 26.2 保留原版 `ONLY_OUTDATED` Logo 显示选项。
- 同时保留隐藏 Logo 相关的 `HIDDEN` 选项。
- Logo 文本增加 `Fork BySJia` 标识。

### 稳定性

- 26.2 的 AltRenderer 后台线程使用 daemon platform thread, 避免线程池阻止游戏关闭。

## 关键事件链

```text
Minecraft 网络包或区块更新
  -> ClientPlayNetworkHandlerMixin
  -> ChunkUpdateListener
  -> Search/CaveFinder/MobSpawnESP/PortalESP
  -> 区块搜索或渲染缓存重建
```

```text
ClientPlayerInteractionManager.updateBlockBreakingProgress()
  -> ClientPlayerInteractionManagerMixin
  -> BlockBreakingProgressEvent
  -> FastBreakHack
```

## 构建验证

- 26.2 工程已通过 `.\\gradlew.bat test`。
- 26.2 工程已通过 `.\\gradlew.bat build`。
- 构建产物目录为 `26.2_Fabric/build/libs/`。
- 1.20.1 工程的常规验证命令为 `.\\gradlew.bat test` 和 `.\\gradlew.bat build -x spotlessLicenseHeaderCheck`。

## Git 状态记忆

- 当前主分支为 `main`, 远程为 `origin/main`。
- 历史起点是 `Initial Wurst Fork BySJia`。
- 本次提交包含 1.20.1 已有魔改、完整 26.2 工程、忽略规则以及本项目文档。
- 不主动执行 `git push`。
