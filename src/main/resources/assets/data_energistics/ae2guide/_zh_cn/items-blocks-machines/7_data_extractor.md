---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 数据提取仪
  icon: data_energistics:data_extractor
  position: 7
item_ids:
- data_energistics:data_extractor
- data_energistics:data_carrier
---

# 数据提取仪
数据提取仪会从海量信息流中抽出隐藏规则，把原始素材转成结构化数据。
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:data_extractor" x="0" y="0" z="0" />
   <IsometricCamera yaw="205" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:data_extractor" />
</Row>

---

# 升级

<Row>
    <ItemLink id="ae2:capacity_card"/>
    <ItemImage id="ae2:capacity_card" />
</Row>
容量卡：  
同时增加目标上限与工作范围。

目标上限公式：  
目标上限 = 基础目标上限 + 容量卡数量 × 每张卡额外目标数

默认配置下：  
目标上限 = 20 + 容量卡数量 × 5

范围公式：
- 水平方向每张容量卡向两侧各增加 1 格
- 垂直高度 = 3 + 容量卡数量 × 2

默认情况下：
- 0 张容量卡：水平基础半径 1，垂直高度 3
- 每多 1 张卡：水平每侧 +1 格，垂直 +2 格

---

<Row>
    <ItemLink id="ae2:speed_card"/>
    <ItemImage id="ae2:speed_card" />
</Row>
加速卡会缩短工作周期。

间隔公式：  
最终工作时间（秒） = 基础工作时间 - 加速卡数量 × 1

默认基础工作时间为 5 秒：
- 0 张：每 5 秒工作一次
- 1 张：每 4 秒工作一次
- 2 张：每 3 秒工作一次
- 3 张：每 2 秒工作一次
- 4 张及以上：每 1 秒工作一次

---

<Row>
    <ItemLink id="ae2:energy_card"/>
    <ItemImage id="ae2:energy_card" />
</Row>
能源卡会提高每轮生成的基础数据流，并提升内部 AE 储能缓冲。

基础数据流公式：  
每轮基础数据流 = 基础值 + 能源卡数量 × 200 + 本轮造成伤害 × 每点伤害额外数据流

默认配置下：  
每轮基础数据流 = 100 + 能源卡数量 × 200 + 造成伤害 × 20

内部 AE 缓冲公式：  
内部缓冲 = 1600 + 能源卡数量 × 100

---

# 多目标倍率
当一次工作影响多个目标时，基础数据流会乘上一个目标倍率。

公式：  
最终数据流 = 每轮基础数据流 × (1 + max(0, 目标数量 - 1) × 额外目标倍率)

默认额外目标倍率为 0.25：
- 1 个目标：× 1.00
- 2 个目标：× 1.25
- 3 个目标：× 1.50
- 4 个目标：× 1.75

这些基础数值都可以继续在配置文件中调整。

---

# 数据载体
<ItemImage id="data_energistics:data_carrier"  scale="6" />

数据提取仪会使用空白  
<Row>
    <ItemLink id="data_energistics:data_carrier"/>
    <ItemImage id="data_energistics:data_carrier" />
</Row>
载体作为基础媒介。  
记录完成后，会自动转化为对应的成品数据载体。

基础规则：
- 将空白数据载体放入左上主槽
- 一张空白载体一次只能记录一个类别
- 一旦开始记录生物数据，就不能混入矿石或作物数据
- 一旦开始记录矿石数据，就不能混入生物或作物数据
- 一旦开始记录作物数据，就不能混入生物或矿石数据
- 当进度达到要求后，空白载体会变成对应的成品载体

---

# 生物数据载体
<Row>
    <ItemLink id="data_energistics:mob_data_carrier"/>
    <ItemImage id="data_energistics:mob_data_carrier" />
</Row>

记录流程：
- 将空白数据载体放入主槽
- 剑槽可放入武器；若为空则机器使用基础魔法伤害
- 提取仪会周期性伤害范围内的活体目标
- 首次成功记录时，载体会锁定当前被伤害的生物类型
- 之后只有对同一种生物造成的有效伤害才会继续累积进度

进度来源：
- 进度直接跟随对目标生物实际造成的伤害
- 达到要求后，空白载体会变成 <ItemImage id="data_energistics:mob_data_carrier" />

特性：
- 适合锁定某一种特定生物
- 后续可放入数据拟生场中模拟该生物的掉落

---

# 矿石数据载体
<Row>
    <ItemLink id="data_energistics:ore_data_carrier"/>
    <ItemImage id="data_energistics:ore_data_carrier" />
</Row>

记录流程：
- 将空白数据载体放入主槽
- 在矿物槽放入矿石，或配置允许的其他有效矿物输入
- 首次记录时，载体会锁定当前输入代表的矿物目标
- 之后只有同一目标的输入才会继续推进进度

进度来源：
- 默认普通矿石按 `1:1` 计数
- 默认粗矿按 `0.5:1` 计数
- 如果 `input_rules` 中存在矿石规则，则优先生效：
  - `recorded_item`
  - `progress_per_item`
  - `required_amount`

完成条件：
- 达到要求后，空白载体会变成 <ItemImage id="data_energistics:ore_data_carrier" />

---

# 作物数据载体
<Row>
    <ItemLink id="data_energistics:crop_data_carrier"/>
    <ItemImage id="data_energistics:crop_data_carrier" />
</Row>

记录流程：
- 将空白数据载体放入主槽
- 在作物槽放入作物、树苗、菌类、花、竹子、甘蔗、仙人掌，或其他配置允许的输入
- 首次记录时，载体会锁定当前输入代表的作物目标
- 之后只有匹配目标的输入才会继续推进进度

进度来源：
- 默认标准作物输入按 `1:1` 计数
- 部分映射输入可通过配置转换，例如：
  - 小麦种子 -> 小麦，`0.5`
  - 甜菜种子 -> 甜菜根，`0.5`
  - 西瓜种子 / 西瓜片 -> 西瓜，`0.5`
  - 南瓜种子 -> 南瓜，`0.5`
- 如果 `input_rules` 中存在作物规则，则优先生效：
  - `recorded_item`
  - `progress_per_item`
  - `required_amount`

有效目标：
- 常规农作物
- 下界疣
- 树苗与红树胎生苗
- 蘑菇与菌类
- 甜浆果
- 西瓜与南瓜
- 竹子、甘蔗、仙人掌
- 各类花
- 你额外配置的任何输入

完成条件：
- 达到要求后，空白载体会变成 <ItemImage id="data_energistics:crop_data_carrier" />

---

# 配置规则
数据提取仪与数据拟生场共用一份独立规则表：

`config/data_energistics-data_extractor_rules.json`

---

其中：
- `input_rules` 控制提取仪各槽位允许放入什么、记录成什么数据、每个物品提供多少进度、以及总需求量
- `output_rules` 控制成品数据载体在数据拟生场中会产出什么

如果没有匹配的 `input_rules` 条目，数据提取仪会退回到内置默认规则。  
如果没有对应的 `output_rule`，数据拟生场会退回到默认战利品表或硬编码掉落逻辑。
