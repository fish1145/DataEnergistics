---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 数据水晶
  icon: data_energistics:data_crystal
  position: 2
item_ids:
- data_energistics:budding_data_crystal_0
- data_energistics:budding_data_crystal_1
- data_energistics:budding_data_crystal_2
- data_energistics:budding_data_crystal_3
- data_energistics:budding_data_crystal_4
- data_energistics:small_data_crystal_bud
- data_energistics:medium_data_crystal_bud
- data_energistics:large_data_crystal_bud
- data_energistics:data_crystal_cluster
- data_energistics:data_crystal
- data_energistics:data_dust
- data_energistics:data_crystal_block
---

# 数据水晶家族
探索，移动，编辑以神秘的构造存在于晶体之中,其具有良好的数据传导性,往往用于电路板以及框架的制作，但是没有人知道它会发生什么后果
<GameScene zoom="3" background="transparent">
  <Block id="data_energistics:budding_data_crystal_0" x="0" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_1" x="1" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_2" x="2" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_3" x="3" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_4" x="4" y="0" z="0" />

  <Block id="data_energistics:small_data_crystal_bud" x="1" y="1" z="0" />
  <Block id="data_energistics:medium_data_crystal_bud" x="2" y="1" z="0" />
  <Block id="data_energistics:medium_data_crystal_bud" x="3" y="1" z="0" />
  <Block id="data_energistics:data_crystal_cluster" x="4" y="1" z="0" />
 <IsometricCamera yaw="0" pitch="25" />
</GameScene>

---

## 数据水晶母岩/块
 它与赛特斯石英一样，随宇宙中的陨石而来。你可以在落地的 AE 数位化陨石内部发现它。  
 充盈数据水晶母岩有百分之27的概率在陨石里随机刷1～2块
<GameScene zoom="4" background="transparent">
    <Block id="data_energistics:budding_data_crystal_0" x="0" y="0" z="0" />
    <Block id="data_energistics:budding_data_crystal_4" x="2" y="0" z="0" />
    <IsometricCamera yaw="0" pitch="25" />
</GameScene>  

---

由失活数据水晶母岩激活而获得亏欠数据水晶母岩，在每次生长成功后，有1/12下降一个等级
<GameScene zoom="4" background="transparent">
  <Block id="data_energistics:budding_data_crystal_1" x="2" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_2" x="4" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_3" x="6" y="0" z="0" />
  <IsometricCamera yaw="0" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:budding_data_crystal_3" />
</Row>
---

## 数据晶

<Row>
  <ItemImage id="small_data_crystal_bud" scale="3" />
  <ItemImage id="medium_data_crystal_bud" scale="3" />
  <ItemImage id="large_data_crystal_bud" scale="3" />
  <ItemImage id="data_crystal_cluster" scale="3" />
  <ItemImage id="data_crystal" scale="3" />
  <ItemImage id="data_dust" scale="3" />
</Row>

## 对照表

| 阶段 | 非精准采集下掉落 | 掉落物及概率 |
| :-- | :-- | :-- |
| <ItemImage id="data_energistics:small_data_crystal_bud" /> | <ItemImage id="data_energistics:data_dust" /> | 0% <ItemImage id="data_energistics:data_dust" /> |
| <ItemImage id="data_energistics:medium_data_crystal_bud" /> | <ItemImage id="data_energistics:data_dust" /> | 15% <ItemImage id="data_energistics:data_dust" /> |
| <ItemImage id="data_energistics:large_data_crystal_bud" /> | <ItemImage id="data_energistics:data_dust" /> | 25% <ItemImage id="data_energistics:data_dust" /> |
| <ItemImage id="data_energistics:data_crystal_cluster" /> | <ItemImage id="data_energistics:data_dust" /> <ItemImage id="data_energistics:data_crystal" /> | 40% <ItemImage id="data_energistics:data_dust" />，否则 60% <ItemImage id="data_energistics:data_crystal" /> |

## 数据水晶
<Column>
    <Row>
        <Recipe id="data_energistics:ae2/charger/data_crystal" />
        <RecipeFor id="data_energistics:data_crystal" />
        <Recipe id="data_energistics:crafting/data_crystal" />
    </Row>
</Column>
由于一瞬间的猛烈激活而具有良好的导电性以及晶体韧性，往往被用于做成各个设备的框架。
