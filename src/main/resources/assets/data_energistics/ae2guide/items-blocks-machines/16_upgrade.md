---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 升级
  icon: ae2:advanced_card
  position: 16
item_ids:
- data_energistics:card_saber_energy
- data_energistics:redstone_tuning_card
---

# 升级

## AE2 容量卡

<Row>
  <ItemLink id="ae2:capacity_card" />
  <ItemImage id="ae2:capacity_card" />
</Row>

数位化存储仓可以安装最多 4 张容量卡。

<Row>
  <ItemImage id="data_energistics:digital_storage_depot" />
</Row>

容量卡会同时提升物品槽、流体槽和 Key 槽的容量：  
最终容量 = 基础容量 × (1 + 4 × 容量卡数量)

如果移除容量卡会导致现有内容超过新的容量上限，该容量卡会暂时无法取出。

---

## 聚能卡
<Row>
  <ItemImage id="data_energistics:card_saber_energy" scale="6" />
  <RecipeFor id="data_energistics:card_saber_energy" />
</Row>

一种主要供武器与部分数据水晶工具使用的进攻型升级卡。

适用对象：
<Row>
  <ItemImage id="data_energistics:matter_converging_crossbow" />
  <ItemImage id="data_energistics:data_light_saber" />
  <ItemImage id="data_energistics:data_sanctifier" />
  <ItemImage id="data_energistics:data_crystal_cutting_knife" />
  <ItemImage id="data_energistics:data_crystal_sword" />
  <ItemImage id="data_energistics:data_crystal_axe" />
  <ItemImage id="data_energistics:data_crystal_pickaxe" />
  <ItemImage id="data_energistics:data_crystal_hoe" />
  <ItemImage id="data_energistics:data_crystal_shovel" />
</Row>

效果：  
<ItemImage id="data_energistics:matter_converging_crossbow" />：最终伤害 = 基础伤害 × (聚能卡数量 × 2) × 当前速度(3.15)，暴击后再 × 1.5。若使用满电 <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" /> 作为弹药，则还能进一步提高最大百分比真实伤害。  
<ItemImage id="data_energistics:data_light_saber" />：提高伤害上限、增加耗能，并允许满电状态下左键发射光刃。  
<ItemImage id="data_energistics:data_sanctifier" />：与数据光剑属于同一强化分支。  
<ItemImage id="data_energistics:data_crystal_cutting_knife" />：增加传送范围与数据流存储能力。  
<ItemImage id="data_energistics:data_crystal_sword" />：攻击可消耗数据流，暂时剥夺目标 AI。  
<ItemImage id="data_energistics:data_crystal_axe" />：可消耗数据流连锁整棵树。  
<ItemImage id="data_energistics:data_crystal_pickaxe" />：可消耗数据流连锁周围矿石并额外复制一份掉落。  
<ItemImage id="data_energistics:data_crystal_hoe" />：可消耗数据流让耕地保持湿润。  
<ItemImage id="data_energistics:data_crystal_shovel" />：可切换范围挖掘，并在破坏时消耗数据流。

---

## 红石调整卡
<Row>
  <ItemImage id="data_energistics:redstone_tuning_card" scale="6" />
  <RecipeFor id="data_energistics:redstone_tuning_card" />
</Row>

用于把部分设备行为转换为与红石联动的模式。

适用对象：
<Row>
  <ItemImage id="data_energistics:adaptive_pattern_provider" />
  <ItemImage id="data_energistics:matter_converging_crossbow" />
</Row>

效果：  
<ItemImage id="data_energistics:adaptive_pattern_provider" />：  
这一分支中的样板供应器在发出红石脉冲时会主动发送一次脉冲；接收到红石脉冲时，会对内部每个可合成样板各请求一次主产物输出。  
<ItemImage id="data_energistics:matter_converging_crossbow" />：  
普通弹药单发耗电变为基础耗电 × 5，并使弹射物获得追踪最近非玩家活体的能力。
