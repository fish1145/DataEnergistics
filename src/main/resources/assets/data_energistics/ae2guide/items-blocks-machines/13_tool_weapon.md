---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 工具/武器
  icon: data_energistics:data_sanctifier
  position: 13
item_ids:
- data_energistics:data_crystal_sword
- data_energistics:data_crystal_axe
- data_energistics:data_crystal_pickaxe
- data_energistics:data_crystal_hoe
- data_energistics:data_crystal_shovel
- data_energistics:data_crystal_cutting_knife

- data_energistics:data_light_saber
- data_energistics:data_sanctifier

- data_energistics:matter_converging_crossbow
---

# 工具/武器
## 数据水晶
一种以特殊工艺制作的基础工具。使其拥有下界合金般的硬度，以及金子的轻便程度。因其特殊工艺，使其自身能存储少量能量
<Row>
  <ItemImage id="data_energistics:data_crystal_sword" scale="4" />
  <ItemImage id="data_energistics:data_crystal_axe" scale="4" />
  <ItemImage id="data_energistics:data_crystal_pickaxe" scale="4" />
  <ItemImage id="data_energistics:data_crystal_hoe" scale="4" />
  <ItemImage id="data_energistics:data_crystal_shovel" scale="4" />
  <ItemImage id="data_energistics:data_crystal_cutting_knife" scale="4" />
</Row>

| 名称                                                                   | 存储能力  | 攻击速度 | 攻击伤害                  | 挖掘等级/速率                  | 可装载升级                                                                                                                    |
|----------------------------------------------------------------------|-------|------|-----------------------|--------------------------|--------------------------------------------------------------------------------------------------------------------------|
| <ItemImage id="data_energistics:data_crystal_sword" /> 数据水晶剑         | 20kae | 2    | 10                    | 无                        | <ItemLink id="ae2:speed_card"/> : 增加0.2攻击速度，增加100ae耗能 / <ItemLink id="ae2:energy_card"/> : 最大能源 = 基础容量 × (1 + 8 × 能源卡数量) |
| <ItemImage id="data_energistics:data_crystal_axe" /> 数据水晶斧           | 20kae | 1.2  | 12                    | 5\12                     | <ItemLink id="ae2:speed_card"/> : 增加0.2攻击\挖掘速度，增加耗能 / <ItemLink id="ae2:energy_card"/> : 最大能源 = 基础容量 × (1 + 8 × 能源卡数量)   |
| <ItemImage id="data_energistics:data_crystal_pickaxe" /> 数据水晶镐       | 20kae | 1.4  | 6                     | 5\12                     | <ItemLink id="ae2:speed_card"/> : 增加0.2攻击\挖掘速度，增加耗能 / <ItemLink id="ae2:energy_card"/> : 最大能源 = 基础容量 × (1 + 8 × 能源卡数量)   |
| <ItemImage id="data_energistics:data_crystal_hoe" /> 数据水晶锄           | 20kae | 1.4  | 6                     | 5\12                     | <ItemLink id="ae2:speed_card"/> : 增加0.2攻击\挖掘速度，增加耗能 / <ItemLink id="ae2:energy_card"/> : 最大能源 = 基础容量 × (1 + 8 × 能源卡数量)   |
| <ItemImage id="data_energistics:data_crystal_shovel" /> 数据水晶锹        | 20kae | 1.4  | 6                     | 5\12                     | <ItemLink id="ae2:speed_card"/> : 增加0.2攻击/挖掘速度，增加耗能 / <ItemLink id="ae2:energy_card"/> : 最大能源 = 基础容量 × (1 + 8 × 能源卡数量)   |
| <ItemImage id="data_energistics:data_crystal_cutting_knife" /> 数据切割刀 | 20kae | 无    | 当装载 eae 时潜行右键可以给 AE 设备重命名 | 替换为特殊功能，存储数据流可在传送锚之间互相传送 | <ItemLink id="ae2:energy_card"/> : 最大能源 = 基础容量 × (1 + 8 × 能源卡数量)                                                         |

这些使用 AE 能量的工具现在也能通过 Forge Energy 设备充电。输入的 FE 会按 AE2 能量单位转换为 AE 存入物品；该兼容逻辑仅负责充电，不会把工具内的 AE 反向输出为 FE。

---

## 数据光剑
一种快乐而又危险的"小玩具"，能让你cosplay一下绝地武士？
<Row>
    <ItemImage id="data_energistics:data_light_saber" scale="4" />
    <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" scale="4" />
</Row> 
耐久为20kae，攻击速度为2，攻击伤害为17，右键短暂蓄力后可以投掷，打开背包后拿染料或染色器右键可以对其染色。

# 数据圣裁者  
一种特殊的光剑，使用光剑红、黄、蓝合成
<Row>
    <ItemImage id="data_energistics:data_sanctifier" scale="4" />
    <ItemImage id="data_energistics:data_sanctifier" components="ae2:stored_energy=20000.0d" scale="4" />
</Row> 
耐久为20kae，攻击伤害为36，右键短暂蓄力后可以投掷。

---

# 物质聚合弩
一把将凝聚的物质发射出去的弩，力与美的协奏曲，在空中划出文明最暴力的弧线。
<Row>
    <ItemImage id="data_energistics:matter_converging_crossbow" scale="4" />
</Row>
耐久为20kae，可将各色物质球、奇点、满电无升级数据光剑作为弹药。

| 弹药                                                                                      | 伤害                 | 耗电                                  |
|-----------------------------------------------------------------------------------------|--------------------|-------------------------------------|
| <ItemImage id="ae2:matter_ball" /> 各色物质球                                                | 10                 | 200ae                               |
| <ItemImage id="ae2:singularity" /> 奇点                                                   | 25                 | 200ae                               |
| <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" /> 满电数据光剑 | 20(1%~5%最大百分比真实伤害) | 200000 ae + 额外耗电 (上限 300000 AE / 发) |

---

# 升级
<Row>
    <ItemLink id="ae2:speed_card"/> 
    <ItemImage id="ae2:speed_card" />
</Row>
加速卡：
<Row>
    <ItemImage id="data_energistics:data_crystal_sword" />
    <ItemImage id="data_energistics:data_light_saber" />
    <ItemImage id="data_energistics:data_sanctifier" />
</Row>
：增加 0.2 攻击速度，增加 100 AE 耗能（当光剑装载时，减少蓄力时间）
<Row>
    <ItemImage id="data_energistics:data_crystal_axe" />
    <ItemImage id="data_energistics:data_crystal_pickaxe" />
    <ItemImage id="data_energistics:data_crystal_hoe" />
    <ItemImage id="data_energistics:data_crystal_shovel" />
</Row>  
：增加 0.2 攻击速度，增加 100 AE 耗能，提升 8 点挖掘效率

<Row>
    <ItemImage id="data_energistics:matter_converging_crossbow" />
</Row>
弹射物速度：  
普通弹药：3.15 + 加速卡数量 * 1.0  
特殊弹药：3.15 * 1.5 + 加速卡数量 * 1.0 (特殊弹药不吃弹射物速度加成)  
当加速卡数量 >= 4，拉满弦后会直接装弹并立刻发射

---

<Row>
 <ItemLink id="ae2:energy_card"/>  
 <ItemImage id="ae2:energy_card" />
</Row>
能源卡:
<Row>
    <ItemImage id="data_energistics:data_crystal_sword" />
    <ItemImage id="data_energistics:data_crystal_axe" />
    <ItemImage id="data_energistics:data_crystal_pickaxe" />
    <ItemImage id="data_energistics:data_crystal_hoe" />
    <ItemImage id="data_energistics:data_crystal_shovel" />
    <ItemImage id="data_energistics:data_crystal_cutting_knife" /> 
    <ItemImage id="data_energistics:data_light_saber" />
    <ItemImage id="data_energistics:data_sanctifier" />
</Row>  
：最大能源 = 基础容量 × (1 + 8 × 能源卡数量)  

<ItemImage id="data_energistics:matter_converging_crossbow" />  
：最大能源 = 基础容量 × (1 + 8 × 能源卡数量)  
百分比真实伤害 = 1% + 每25000 AE额外耗电增加1%。

---

<Row>
    <ItemLink id="data_energistics:redstone_tuning_card" /> 
    <ItemImage id="data_energistics:redstone_tuning_card" />
</Row>
红石调整卡：  
<ItemImage id="data_energistics:matter_converging_crossbow" /> :  
普通弹药单发耗电 = 基础耗电 × 5。装上后弹射物开启追踪，追踪目标是最近的非玩家活体。  

---

<Row>
    <ItemLink id="data_energistics:card_saber_energy"/>  
    <ItemImage id="data_energistics:card_saber_energy" />
</Row>
聚能卡:  
<ItemImage id="data_energistics:matter_converging_crossbow" /> : 最终伤害 = 基础伤害 × (聚能卡数量 × 2) × 当前速度(3.15) [暴击再 × 1.5]  当<ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" />为弹药时增加5%最大百分比真实伤害  
<ItemImage id="data_energistics:data_light_saber" /> : 最大伤害 = 基础伤害 × (聚能卡数量 × 2) 增加500ae耗能, <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" />左键攻击可以发射出光刃,<ItemImage id="data_energistics:data_sanctifier" components="ae2:stored_energy=20000.0d" />的体型会比原本大一倍  
<ItemImage id="data_energistics:data_crystal_cutting_knife" /> : 扩大传送范围  
以下工具还会增加一个数据流存储槽  
<ItemImage id="data_energistics:data_crystal_sword" /> : 最大伤害 = 基础伤害 × (聚能卡数量 × 2) 增加500ae耗能，攻击时消耗20数据流剥夺实体20Tick AI  
<ItemImage id="data_energistics:data_crystal_axe" /> : 消耗20数据能连锁一整棵树，增加500ae耗能   
<ItemImage id="data_energistics:data_crystal_pickaxe" /> : 消耗20数据连锁周边矿石，并将其复制一份，增加500ae耗能  
<ItemImage id="data_energistics:data_crystal_hoe" /> : 消耗20数据耕地永不失水，增加500ae耗能  
<ItemImage id="data_energistics:data_crystal_shovel" /> : 可以潜行右键调整3×3或5×5的破坏范围，增加500ae耗能，破坏时消耗20数据  
