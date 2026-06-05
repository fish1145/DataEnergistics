---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 部分材料
  icon: data_energistics:obsidian_dust
  position: 3
item_ids:
- data_energistics:obsidian_dust
- data_energistics:solidified_obsidian
- data_energistics:digisidian_memorize_ingot
- data_energistics:data_framework
- data_energistics:residual_data_ore
- data_energistics:data_capture_ball
---

# 材料/框架

# 数据框架
一种极其坚固的框架。利用水晶的优良传导以及折射传输数据
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:data_framework" x="0" y="0" z="0" />
   <IsometricCamera yaw="25" pitch="25" />
</GameScene>
<Row>
    <RecipeFor id="data_energistics:data_framework" />
</Row>

---

## 固化黑曜石
一种坚固具有优良的绝缘性的材料
<Row>
    <ItemImage id="data_energistics:obsidian_dust" scale="6" />
    <ItemImage id="data_energistics:solidified_obsidian" scale="6" />
</Row>  

<Row>
  <RecipeFor id="data_energistics:obsidian_dust" />
  <RecipeFor id="data_energistics:solidified_obsidian" />
</Row>

---

## 数曜记忆合金
一种具备“记忆”功能的特殊合金，能够在经历变形后，通过温度变化自动恢复到原始形状。具有优良的绝缘性以及数据传导性
<Row>
    <ItemImage id="data_energistics:digisidian_memorize_ingot" scale="6" />
    <RecipeFor id="data_energistics:digisidian_memorize_ingot" />
</Row>  

---

## 矿石
一种存在于末地的奇异矿石，当破坏的一瞬间，你感觉什么东西似乎进入了你的身体？
<GameScene zoom="6" background="transparent">
<Block id="data_energistics:residual_data_ore" x="0" y="0" z="0" />
<IsometricCamera yaw="25" pitch="25" />
</GameScene>
挖掘时有概率掉落0~3个即散数据，受时运影响

---

## 数据捕捉球
经过物质凝聚的能量形成的球体，用于捕捉即散数据，当能量耗空时它会被销毁里面的数据也会被销毁
<Row>
<ItemImage id="data_energistics:data_capture_ball" scale="6" />
</Row>  
<Row>
<Recipe id="data_energistics:condenser/data_capture_ball" />
</Row>