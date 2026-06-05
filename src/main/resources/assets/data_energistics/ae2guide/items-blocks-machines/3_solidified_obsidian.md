---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Materials
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

# Materials / Framework

# Data Framework
An extremely sturdy framework that uses the excellent conductivity of crystals and refraction to transmit data.
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:data_framework" x="0" y="0" z="0" />
   <IsometricCamera yaw="25" pitch="25" />
</GameScene>
<Row>
    <RecipeFor id="data_energistics:data_framework" />
</Row>

---

## Solidified Obsidian
A sturdy material with excellent insulating properties.
<Row>
    <ItemImage id="data_energistics:obsidian_dust" scale="6" />
    <ItemImage id="data_energistics:solidified_obsidian" scale="6" />
</Row>  

<Row>
  <RecipeFor id="data_energistics:obsidian_dust" />
  <RecipeFor id="data_energistics:solidified_obsidian" />
</Row>

---

## Digisidian Memory Alloy
A special alloy with "memory" functionality that can automatically return to its original shape through temperature changes after deformation. It has excellent insulation and data conductivity.
<Row>
    <ItemImage id="data_energistics:digisidian_memorize_ingot" scale="6" />
    <RecipeFor id="data_energistics:digisidian_memorize_ingot" />
</Row>  

---

## Ore
A strange ore found in the End. When you break it, you feel as if something has entered your body?
<GameScene zoom="6" background="transparent">
<Block id="data_energistics:residual_data_ore" x="0" y="0" z="0" />
<IsometricCamera yaw="25" pitch="25" />
</GameScene>
When mined, has a chance to drop 0-3 Dispersing Data, affected by Fortune.

---

## Data Capture Ball
A sphere formed by condensing matter through energy, used to capture Dispersing Data. When its energy runs out, the ball is destroyed along with the data inside.
<Row>
<ItemImage id="data_energistics:data_capture_ball" scale="6" />
</Row>  
<Row>
<Recipe id="data_energistics:condenser/data_capture_ball" />
</Row>
