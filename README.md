# Train double-kghua

> ## ⚠️ 非官方附属模组声明
> 本模组是 **StarRailExpress（残月列车 / The Harpy Express）的非官方附属模组**。
> - 非残月团队官方作品，未经官方认可，与残月团队无任何从属或合作关系；
> - 原模组（StarRailExpress）开源仓库：**[https://github.com/catmoon-train/StarRailExpress](https://github.com/catmoon-train/StarRailExpress)**；
> - 本模组按上游相同的 **GPL-3.0** 协议开源（见 [LICENSE.txt](LICENSE.txt)），并保留上游 LICENSE；
> - 按 GPL-3.0 要求：本模组基于 StarRailExpress 修改，含其派生代码，任何使用/修改/分发必须遵守 GPL-3.0。

## 简介

残月哈比快车服务器的整合模组，为 StarRailExpress 提供：

- **Western Cowboy（西部牛仔）决斗者角色**：决斗技能、金币经济、称号与商店互动；
- **NPC AI 客服系统**：游戏内 NPC 对话、邮箱、投稿审核、传送点、小脑惩罚等管理功能；
- **网站互通（WebBridge）**：加载该模组的服务器与配套网站实时同步（战绩 / 邮箱 / 聊天 / 服务器状态）。

## 构建

```bash
# JDK 21
./gradlew.bat build        # Windows
# 或：./gradlew build      # Linux/macOS
```

产物：`build/libs/Train double-kghua-2.1.2.jar`

> 注意：替换 mods 目录中的 jar 前请先退出游戏（运行中替换 jar/配置会因类加载错乱而崩溃）。

## 皮肤系统说明

官方皮肤系统不对外开放。本模组已断开对官方物品皮肤/抽奖系统的所有连接。

## License

[GPL-3.0](LICENSE.txt)（与上游 StarRailExpress 相同；上游 LICENSE 原样保留于本仓库）
