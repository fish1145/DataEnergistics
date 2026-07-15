---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: Digitized Meteorite
  icon: data_energistics:data_meteorite_0
  position: 1
item_ids:
- data_energistics:data_meteorite_0
- data_energistics:data_meteorite_1
- data_energistics:data_meteorite_2
- data_energistics:data_meteorite_compass
---

# Digitized meteorite

<GameScene zoom="3" background="transparent">
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="3" y="0" z="4" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="2" y="0" z="4" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="1" y="0" z="4" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="0" y="0" z="4" />

  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="0" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="1" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="2" />
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="4" y="0" z="3" />

  <Block id="ae2:sky_stone_block" x="3" y="0" z="0" />
  <Block id="data_energistics:data_meteorite_0" x="3" y="0" z="1" />
  <Block id="ae2:sky_stone_block" x="3" y="0" z="2" />
  <Block id="ae2:sky_stone_block" x="0" y="0" z="3" />
  <Block id="data_energistics:data_meteorite_1" x="1" y="0" z="3" />
  <Block id="ae2:sky_stone_block" x="2" y="0" z="3" />

  <Block id="data_energistics:budding_data_crystal_0" x="0" y="0" z="0" />
  <Block id="ae2:damaged_budding_quartz" x="1" y="0" z="0" />
  <Block id="ae2:flawed_budding_quartz" x="2" y="0" z="0" />
  <Block id="ae2:quartz_block" x="0" y="0" z="1" />
  <Block id="ae2:flawed_budding_quartz" x="0" y="0" z="2" />
  <Block id="ae2:chipped_budding_quartz" x="1" y="0" z="1" />
  <Block id="data_energistics:budding_data_crystal_0" x="1" y="0" z="2" />
  <Block id="ae2:flawless_budding_quartz" x="2" y="0" z="1" />
  <Block id="ae2:quartz_block" x="2" y="0" z="2" />


  <Block id="ae2:mysterious_cube" x="1" y="1" z="1" />
  <Block id="ae2:large_quartz_bud" x="1" y="1" z="0" />
  <Block id="ae2:small_quartz_bud" x="2" y="1" z="0" />
  <Block id="ae2:quartz_cluster" x="0" y="1" z="2" />
  <Block id="ae2:large_quartz_bud" x="2" y="1" z="1" />


  <Block id="data_energistics:data_meteorite_2" x="3" y="1" z="2" />
  <Block id="ae2:quartz_block" x="2" y="2" z="2" />
  <Block id="data_energistics:budding_data_crystal_4" x="2" y="2" z="1" />
  <Block id="ae2:flawless_budding_quartz" x="1" y="2" z="2" />

  <IsometricCamera yaw="150" pitch="25" />
</GameScene>
A special variant based on AE meteorites, where both craters and meteorites are larger than normal AE meteorites  
There is a 27% chance inside meteorites to have 1~2 Charged Data Crystal Motherrock (at least from what I've seen, most players actually find it quite hard to find).  
The blocks that can appear inside the meteorite are already shown in this overview structure (as shown above).  

> ⚠ it will be the start of your mod
>

---

## Digital meteorite

| Meteorite | Basic drop | Additional output |
|---|-|---|
| <ItemImage id="ae2:sky_stone_block" /> | 100% <ItemImage id="ae2:sky_stone_block" /> | None |
| <ItemImage id="data_energistics:data_meteorite_0" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 10% + luck per level +3% <ItemImage id="ae2:ender_dust" /> | 5% + luck per level +3%: generate 1 Dispersing Data entities |
| <ItemImage id="data_energistics:data_meteorite_1" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 20% + Luck per level +3% <ItemImage id="ae2:ender_dust" /> 10% + Luck per level +3% <ItemImage id="ae2:sky_dust" /> | 10% + Luck +3% per level: Generates 1 Dispersing Data entity |
| <ItemImage id="data_energistics:data_meteorite_2" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 25% + luck per level +3% <ItemImage id="ae2:ender_dust" /> 15% + luck per level +3% <ItemImage id="ae2:sky_dust" /> | 15% + Luck per level +3%: Generate 1~2 Dispersing Data entities 15%: 6³ random spatial teleport|

---

## Dispersing Data

<GameScene zoom="6" background="transparent">
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:0}" x="0" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:1}" x="1" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:2}" x="2" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:3}" x="3" y="0" z="0" />
  <IsometricCamera yaw="0" pitch="25" />
</GameScene>

Dispersing Data are mining digital meteorites (drop rates as shown in the table above) and mining residual data ores (guaranteed to drop 1~3).  
> ⚠ if you leave it alone for a minute, it will fade away  
> 
When he appears in the world, you need to use it
<Row> 
    <ItemLink id="data_energistics:data_capture_ball" />
    <ItemImage id="data_energistics:data_capture_ball" />
</Row>  
    <RecipeFor id="data_energistics:data_capture_ball" />
captures it; when energy is depleted, it is destroyed and the data inside is also destroyed
  

You can also make one
<Row>
    <ItemLink id="data_energistics:me_vacuum" />
    <ItemImage id="data_energistics:me_vacuum" />
</Row>  
    <RecipeFor id="data_energistics:me_vacuum" />
Place Data Capture Ball in it for area capture

---

## Data Corrosion Liquid
<GameScene zoom="3" background="transparent">
    <Block id="data_energistics:guide_data_corrosion_liquid_display" x="3" y="0" z="4" />
  <IsometricCamera yaw="150" pitch="25" />
</GameScene>

Comes with mutant meteorites, but generally only forms in meteorites that crash onto the plains  
  

You can also make one
<Row>
    <ItemLink id="data_energistics:me_vacuum" />
    <ItemImage id="data_energistics:me_vacuum" />
</Row>  
    <RecipeFor id="data_energistics:me_vacuum" />
Perform a range of suction on the loaded fluid disk  
> ⚠ it deals about twice the damage of Dragon Breath, so proceed with caution
>

## Digitalized Meteorite Compass  

> Meteorite Compass after specialization (synthesis), specifically used to find meteorites in this module
> The usage method is the same as a regular meteorite compass

<Row>
    <ItemImage id="data_energistics:data_meteorite_compass" />
    <RecipeFor id="data_energistics:data_meteorite_compass" />
</Row> 
