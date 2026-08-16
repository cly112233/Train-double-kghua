# Train double-kghua（western_cowboy）

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
- **网站互通（WebBridge）**：与配套网站实时同步（战绩 / 邮箱 / 聊天 / 服务器状态）。

## 依赖（构建前置）

| 依赖 | 用途 | 获取方式 |
|---|---|---|
| [StarRailExpress](https://github.com/catmoon-train/StarRailExpress)（≥4.0.0） | 玩法引擎基座（角色/决斗/商店/身份卡） | 官方发布渠道自行获取，放 `libs/star_rail_express-4.3.0.jar` |
| habitrain_lottery（≥1.0.0） | 邮箱 / 皮肤数据模组 | 官方发布渠道自行获取，放 `libs/habitrain_lottery-1.0.1.jar` |
| wathe | SRE 前置装饰方块库 | 官方发布渠道自行获取 |

> `libs/` 目录与构建产物**不在仓库内**（均为第三方/官方产物，含 ARR 版权组件，不可重新分发）。自行下载后放置即可构建。

## 构建

```bash
# JDK 21
./gradlew.bat build        # Windows
# 或：./gradlew build      # Linux/macOS
```

产物：`build/libs/Train double-kghua-2.1.2.jar`

> 注意：替换 mods 目录中的 jar 前请先退出游戏（运行中替换 jar/配置会因类加载错乱而崩溃）。

## 皮肤系统说明（2026-08-16 起）

官方皮肤系统**不对外开放**。本模组已断开对官方物品皮肤/抽奖系统的所有连接（抽奖接口返回建设中占位、不再发放抽奖次数、皮肤图标导出停用），功能板块保留待自建系统上线。详见项目内《项目交接文档-v3》§2.17（该文档为运营文档，未包含在本仓库）。

## License

[GPL-3.0](LICENSE.txt)（与上游 StarRailExpress 相同；上游 LICENSE 原样保留于本仓库）
