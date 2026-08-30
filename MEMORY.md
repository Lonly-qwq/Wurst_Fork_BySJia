# Wurst Fork 项目记忆

## 项目版本

- `1.20.1_Fabric/source/` 是旧版 Minecraft 1.20.1 Fabric 开发目录。
- `26.2_Fabric/` 是从 `Wurst-Client-v7.55-MC26.2-sources.jar` 移植出的新版 Fabric 工程。
- 26.2 模组版本和归档名称已改为 `Wurst-Fork-BySJia`。
- 根目录的 26.2 输入 JAR、Gradle 缓存和构建输出不应纳入 Git。

## 已完成的魔改

### 上游 7.55.1 融合

- 26.2 基线更新为 Wurst 7.55.1, 保留 `Fork-BySJia` 版本标识。
- `EasyVertexBuffer.createAndUpload()` 现在会关闭临时 `ByteBufferBuilder`, 修复 BaseFinder、CaveFinder、MobSpawnESP、NewChunks、Search 和 Tunneller 重建缓存时的内存泄漏。
- AltManager 导入 TXT 时只按第一个冒号分隔用户名和密码, 密码中的其余冒号会被完整保留。

### Search

- `Blocks` 使用和 X-Ray 相同的多选方块列表界面, 支持同时搜索多个方块。
- 增加 `Show block textures` 开关, 开启后只提交搜索目标的原始方块材质, 不改变其他方块透明度。
- 开启 `Show block textures` 时隐藏原本的彩色闪烁方框层, 只保留材质显示。
- Search 材质提供 `Balanced` 和 `Fullbright` 两种亮度模式。
- `Balanced` 在原版环境中对真实 lightmap 做局部 Gamma 提亮; 在 Iris 光影环境中使用普通 `BLOCK_ENTITY` 程序并将方块光最低补到 12, 保留环境明暗关系。
- `Fullbright` 在原版环境中使用仅作用于 Search 的 X-Ray gamma 16 曲线; 在 Iris 光影环境中优先绑定 `BLOCK_ENTITY_BRIGHT`, 并通过 `Fullbright exposure` 独立调整 Search 材质曝光, 不修改全局 Gamma。
- Search 材质 shader 完全关闭雾颜色混合, 避免地狱等维度的天空和群系雾色污染材质。
- Search 材质模式复用原版 0-9 阶段挖掘进度, 使用独立 no-depth 裂纹层在材质之后显示开裂动画。
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

### ESP 渲染性能

- 26.2 的实体 ESP 线框和 Tracer 可在同一个 RenderEvent 中共用一个批次, 相同 `RenderType` 只进行一次上传和一次 draw。
- 批处理使用可复用的 `StagedVertexBuffer`, 每帧调用 `endFrame()` 回收池, 游戏关闭时显式释放。
- PlayerESP 增加 `Max render distance` 和视锥剔除。性能验证完成后已移除临时 Debug 设置与计数器。
- Minecraft 26.2 原版 `ShapeOutlineFeatureRenderer` 同样为每条 AABB 边写入 2 个 `LINES` 输入顶点, 所以 1 个 Box 的 24 个输入顶点属于正常拓扑。
- Spark 中的 `glDrawElementsInstancedBaseVertex` 时间是采样窗口累计的 CPU 等待位置, 不能单独证明少量线段具有同等 GPU 执行时间。需要使用调试构建分别测试 Box、Tracer、两者同时以及 Iris 开关后再确定最终根因。
- 2026-08-29 的 iterationRP + Iris + Sodium + Voxy 实测 Profile `h6t8RpcbVQ`: 48.148 秒采样中 `PlayerEspHack.onRender()` 为 40 ms / 0.08%, `drawOutlinedBoxesBatched()` 为 8 ms / 0.02%, `drawTracersBatched()` 为 8 ms / 0.02%。整个 Wurst RenderEvent 为 416 ms / 0.86%, 已低于 5% 目标, `WurstBufferSource` 和对应 OpenGL Draw 不再出现在主要热点中。

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
