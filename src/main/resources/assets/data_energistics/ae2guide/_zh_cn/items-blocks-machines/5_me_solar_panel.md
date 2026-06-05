---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: ME太阳能板
  icon: data_energistics:me_solar_panel
  position: 6
item_ids:
- data_energistics:me_solar_panel
- data_energistics:me_solar_panel_part
---

# ME太阳能板
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:me_solar_panel" x="0" y="0" z="0" />
   <IsometricCamera yaw="25" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:me_solar_panel" />
</Row>
一台小而精简的太阳能发电机,工作时需其上方没有任何方块阻挡  
在白天拥有3000AE/T发电量，夜晚只白天效率的1/3 

--- 

ME太阳能板贴片形式  

<Row>
    <ItemImage id="data_energistics:me_solar_panel_part" scale="6" />
</Row>  
<Row>
  <RecipeFor id="data_energistics:me_solar_panel_part" />
</Row>
一台小而精简的太阳能发电机,工作时需其上方没有任何方块阻挡因其贴片的原因，减轻了厚度，从而削弱了发电能力  
在白天拥有2500AE/T发电量，夜晚只白天效率的1/3，当其在侧面时再削弱1/3，在底面时不进行任何发电 
---

# 升级
 <ItemLink id="ae2:speed_card"/>：每张 +75% 发电  

<Row>
    <ItemImage id="ae2:speed_card" />
</Row>
公式：基础发电 × (1 + 加速卡数量 × 0.75)

---

<ItemLink id="ae2:energy_card"/>  ：每张 +80000 AE 容量  

<Row>
    <ItemImage id="ae2:energy_card" />
</Row>
公式：基础容量 160000 AE + 能源卡数量 × 80000 AE