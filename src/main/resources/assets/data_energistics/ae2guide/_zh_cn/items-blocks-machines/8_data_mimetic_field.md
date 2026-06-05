---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 数据拟生场
  icon: data_energistics:data_mimetic_field
  position: 8
item_ids:
- data_energistics:data_mimetic_field
- data_energistics:mob_data_carrier
- data_energistics:crop_data_carrier
- data_energistics:ore_data_carrier
---

# 数据拟生场
在灭绝文明的意识海，以数据在硅基宇宙重新拟生复苏星芒。
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:data_mimetic_field" x="0" y="0" z="0" />
   <IsometricCamera yaw="205" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:data_mimetic_field" />
</Row>

---

# 升级
<Row>
    <ItemLink id="ae2:speed_card"/>
    <ItemImage id="ae2:speed_card" />
</Row>
加速卡：  
最终速度 = 基础速度 - 加速卡数量 × 40

## 对照表

| 加速卡 | 工作周期 | 单载体产量 | 单载体 key 消耗 |
|:----|:-----|:------|:-----------|
| 0   | 200t | 48    | 150        |
| 1   | 160t | 64    | 200        |
| 2   | 120t | 80    | 250        |
| 3   | 80t  | 96    | 300        |
| 4   | 40t  | 112   | 350        |  
  
生物/矿物/农作产量 = 48 + 加速卡 * 16   
单个活跃载体 key 消耗 = 150 + 加速卡 * 50  
总 key 消耗 = 活跃载体数量 * (150 + 加速卡 * 50)  
待机耗电 = 活跃载体数量 * 500 AE/t

---

<Row>
    <ItemLink id="ae2:capacity_card" /> 
    <ItemImage id="ae2:capacity_card" />
</Row>
容量卡：
基础容量 + 容量卡数量 × 4

---

# 载体  
数据芯片之上，每一缕流动的电流都是时代意志凝固的「载体」，承载着文明从混沌走向可计算未来的密钥。
<Row>
    <ItemImage id="data_energistics:mob_data_carrier"  scale="6" />
    <ItemImage id="data_energistics:crop_data_carrier"  scale="6" />
    <ItemImage id="data_energistics:ore_data_carrier"  scale="6" />
</Row>

<ItemImage id="data_energistics:data_mimetic_field"  scale="6" />  
只能放入已经完成记录的三种数据载体。  
载体放入后会按类型自动产出对应内容。

## 生物数据载体

- 记录对象：某一种生物
- 在拟生场中的作用：模拟击杀该生物并产出对应掉落物
- 产出来源：
    - 优先使用已配置的 mob 产物规则
    - 否则调用该生物自己的战利品表
    - 若该生物生成时自带装备，也会一并加入产出
    - 若该生物带有伴生实体、乘客或坐骑，会一起结算掉落

## 矿物数据载体

- 记录对象：某一种矿石目标
- 在拟生场中的作用：持续产出对应矿物
- 产出来源：
    - 优先使用已配置的 ore 产物规则
    - 若没有单独配置，则默认直接产出记录的矿物物品

## 农作数据载体

- 记录对象：某一种农作物、树苗、菌类、花、甘蔗、仙人掌这类可记录植物
- 在拟生场中的作用：持续产出对应植物掉落
- 产出来源：
    - 优先使用已配置的 crop 产物规则
    - 若记录的是树苗/繁殖体，优先走对应树木 loot table
    - 若记录了来源方块，则按成熟方块的掉落表生成
    - 若以上都没有可用结果，则默认直接产出记录物本身
