---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 自适应样板供应器
  icon: data_energistics:adaptive_pattern_provider
  position: 10
item_ids:
- data_energistics:adaptive_pattern_provider
- data_energistics:adaptive_pattern_provider_part
---

# 自适应

## 自适应样板供应器
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:adaptive_pattern_provider" x="0" y="0" z="0" />
   <IsometricCamera yaw="25" pitch="25" />
</GameScene>

<Row>
  <RecipeFor id="data_energistics:adaptive_pattern_provider" />
</Row>
抗拒，解析，适应，融合，以继承的方式执行本体  
在GUI里放上对应的样板供应器，则继承它该属性

---

<Row>
    <ItemImage id="data_energistics:adaptive_pattern_provider_part" scale="6" />
  <RecipeFor id="data_energistics:adaptive_pattern_provider_part" />
</Row>
它的贴片样式

---

# 升级
<Row>
    <ItemLink id="ae2:capacity_card" /> 
    <ItemImage id="ae2:capacity_card" />
</Row>
容量卡：
基础容量 + 容量卡数量 × 4

<Row>
    <ItemLink id="data_energistics:redstone_tuning_card" /> 
    <ItemImage id="data_energistics:redstone_tuning_card" />
</Row>
红石调整卡：
装载后可调整发出红石脉冲或接收红石信脉冲。  
发出红石脉冲：如同字面意思发出一次红石脉冲(装载在AE2CS的自装配供应器时会因为合成太快，从而发送的脉冲次数过少.处理样板的话需开启物品返回会阻挡模式才能正确发送脉冲，否则只会发送一次 )  
接受红石脉冲：接受红石脉冲时。自动为样板供应器内可下单的样板请求一次主产物(当脉冲过快时，可能会出点问题)