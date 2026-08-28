# Wurst Fork 项目地图

## 根目录

```text
Wurst_Fork_BySJia/
├─ .gitignore
├─ AGENTS.md                         # 项目目录地图
├─ MEMORY.md                         # 项目关键记忆
├─ 1.20.1_Fabric/                    # Minecraft 1.20.1 版本
│  ├─ Wurst-Client-v7.50.3-MC1.20.1.jar
│  ├─ unpacked/                       # 原始 JAR 解包参考
│  ├─ decompiled/                    # CFR 反编译参考
│  └─ source/                         # 1.20.1 可构建源码
├─ 26.2_Fabric/                      # Minecraft 26.2 版本
│  └─ src/                            # 26.2 可构建源码
├─ Wurst-Client-v7.55-MC26.2-sources.jar
└─ Wurst-Client-v7.55-MC26.2.jar
```

## 1.20.1_Fabric/source

```text
source/
├─ build.gradle                       # Gradle/Loom 构建配置
├─ gradle.properties                  # Minecraft、Mappings、Fabric 版本
├─ settings.gradle
├─ gradlew / gradlew.bat
└─ src/
   ├─ main/
   │  ├─ java/net/wurstclient/
   │  │  ├─ WurstClient.java          # 客户端核心
   │  │  ├─ WurstInitializer.java     # Fabric 初始化入口
   │  │  ├─ hack/                     # Hack 基类和注册表
   │  │  ├─ hacks/                    # Hack 功能实现
   │  │  ├─ event/                    # 通用事件系统
   │  │  ├─ events/                   # 事件接口和事件数据
   │  │  ├─ mixin/                    # Minecraft 注入点
   │  │  ├─ mixinterface/             # Mixin 暴露接口
   │  │  ├─ settings/                 # 设置类型
   │  │  ├─ clickgui/                 # ClickGUI
   │  │  ├─ commands/                 # 命令实现
   │  │  └─ util/                     # 方块、渲染、网络和区块工具
   │  └─ resources/
   │     ├─ fabric.mod.json
   │     ├─ wurst.mixins.json
   │     ├─ wurst.accesswidener
   │     └─ assets/wurst/
   └─ test/java/                      # 测试源码
```

## 26.2_Fabric

```text
26.2_Fabric/
├─ build.gradle                       # Gradle/Loom 构建配置
├─ gradle.properties                  # Minecraft、Mappings、模组版本
├─ settings.gradle
├─ gradlew / gradlew.bat
├─ codestyle/                         # Eclipse 格式和版权模板
└─ src/
   └─ main/
      ├─ java/net/wurstclient/
      │  ├─ WurstClient.java          # 客户端核心
      │  ├─ WurstInitializer.java     # Fabric 初始化入口
      │  ├─ hack/                     # Hack 基类和注册表
      │  ├─ hacks/                    # Hack 功能实现
      │  ├─ event/                    # 通用事件系统
      │  ├─ events/                   # 事件接口和事件数据
      │  ├─ mixin/                    # Minecraft 注入点
      │  ├─ mixinterface/             # Mixin 暴露接口
      │  ├─ settings/                 # 设置类型
      │  ├─ clickgui/                 # ClickGUI
      │  ├─ commands/                 # 命令实现
      │  ├─ altmanager/               # 账户管理
      │  ├─ navigator/                # Navigator 界面
      │  ├─ other_features/            # 其他功能
      │  └─ util/                     # 方块、渲染、网络和区块工具
      └─ resources/
         ├─ fabric.mod.json
         ├─ wurst.mixins.json
         ├─ wurst.accesswidener
         └─ assets/wurst/
```

## 关键功能定位

| 功能 | 1.20.1 路径 | 26.2 路径 |
|---|---|---|
| Search | `source/src/main/java/net/wurstclient/hacks/SearchHack.java` | `26.2_Fabric/src/main/java/net/wurstclient/hacks/SearchHack.java` |
| BoatFly | `source/src/main/java/net/wurstclient/hacks/BoatFlyHack.java` | `26.2_Fabric/src/main/java/net/wurstclient/hacks/BoatFlyHack.java` |
| FastBreak | `source/src/main/java/net/wurstclient/hacks/FastBreakHack.java` | `26.2_Fabric/src/main/java/net/wurstclient/hacks/FastBreakHack.java` |
| Hack 注册 | `source/src/main/java/net/wurstclient/hack/HackList.java` | `26.2_Fabric/src/main/java/net/wurstclient/hack/HackList.java` |
| 区块事件 | `source/src/main/java/net/wurstclient/events/ChunkUpdateListener.java` | `26.2_Fabric/src/main/java/net/wurstclient/events/ChunkUpdateListener.java` |
| 区块网络注入 | `source/src/main/java/net/wurstclient/mixin/ClientPlayNetworkHandlerMixin.java` | `26.2_Fabric/src/main/java/net/wurstclient/mixin/ClientPlayNetworkHandlerMixin.java` |
| 区块搜索 | `source/src/main/java/net/wurstclient/util/chunk/` | `26.2_Fabric/src/main/java/net/wurstclient/util/chunk/` |
| Logo | `source/src/main/java/net/wurstclient/other_features/WurstLogoOtf.java` | `26.2_Fabric/src/main/java/net/wurstclient/other_features/WurstLogoOtf.java` |
