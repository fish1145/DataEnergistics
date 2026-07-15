---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: 升级
  icon: data_energistics:card_saber_energy
  position: 4
item_ids:
- data_energistics:card_saber_energy
- data_energistics:redstone_tuning_card
---

# 升级

## 聚能卡
<Row>
    <ItemLink id="data_energistics:card_saber_energy"/>  
    <ItemImage id="data_energistics:card_saber_energy" />
    <RecipeFor id="data_energistics:card_saber_energy" />
</Row>
聚能卡:  
<ItemImage id="data_energistics:matter_converging_crossbow" /> : 最终伤害 = 基础伤害 × (聚能卡数量 × 2) × 当前速度(3.15) [暴击再 × 1.5]  当<ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" />为弹药时增加5%最大百分比真实伤害  
<ItemImage id="data_energistics:data_light_saber" /> : 最大伤害 = 基础伤害 × (聚能卡数量 × 2) 增加40ae额外耗能（总耗电 50ae）, <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" />左键攻击可以发射出光刃,<ItemImage id="data_energistics:data_sanctifier" components="ae2:stored_energy=20000.0d" />的体型会比原本大一倍  
<ItemImage id="data_energistics:data_crystal_cutting_knife" /> : 扩大传送范围  
以下工具还会增加一个数据流存储槽  
<ItemImage id="data_energistics:data_crystal_sword" /> : 最大伤害 = 基础伤害 × (聚能卡数量 × 2) 增加40ae额外耗能（总耗电 50ae），攻击时消耗20数据流剥夺实体20Tick AI  
<ItemImage id="data_energistics:data_crystal_axe" /> : 消耗20数据能连锁一整棵树，增加40ae额外耗能（总耗电 50ae）   
<ItemImage id="data_energistics:data_crystal_pickaxe" /> : 消耗20数据连锁周边矿石，并将其复制一份，增加40ae额外耗能（总耗电 50ae）   
<ItemImage id="data_energistics:data_crystal_hoe" /> : 消耗20数据耕地永不失水，增加40ae额外耗能（总耗电 50ae）   
<ItemImage id="data_energistics:data_crystal_shovel" /> : 可以潜行右键调整3×3或5×5的破坏范围，增加40ae额外耗能（总耗电 50ae），破坏时消耗20数据  


## 红石调整卡

<Row>
    <ItemLink id="data_energistics:redstone_tuning_card" />
    <ItemImage id="data_energistics:redstone_tuning_card"  />
    <RecipeFor id="data_energistics:redstone_tuning_card" />
</Row>

红石调整卡用于给样板供应器和物质聚合弩增加红石联动能力，每台设备最多安装 1 张。

### 样板供应器

<Row>
    <ItemImage id="ae2:pattern_provider" />
    <ItemImage id="data_energistics:adaptive_pattern_provider" />
    <ItemImage id="data_energistics:adaptive_pattern_provider_part" />
</Row>

支持 AE2 样板供应器、自适应样板供应器及已兼容的其他样板供应器。安装后可在界面中切换两种模式：

- 发出模式：供应器分发合成内容时，对外发出一次短暂红石脉冲。
- 接收模式：供应器接收到红石脉冲后，对内部每个当前可合成的样板各请求一次主产物。

### 物质聚合弩

<Row>
    <ItemImage id="data_energistics:matter_converging_crossbow" />
</Row>

安装后，普通弹药的单发耗能变为基础耗能的 5 倍，同时弹射物会追踪距离最近的非玩家活体。
