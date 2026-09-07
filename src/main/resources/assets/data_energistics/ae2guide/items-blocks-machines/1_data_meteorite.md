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
- data_energistics:data_mysterious_cube
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
  <Block id="data_energistics:budding_data_crystal_1" x="1" y="0" z="0" />
  <Block id="data_energistics:budding_data_crystal_2" x="2" y="0" z="0" />
  <Block id="ae2:quartz_block" x="0" y="0" z="1" />
  <Block id="ae2:flawed_budding_quartz" x="0" y="0" z="2" />
  <Block id="ae2:chipped_budding_quartz" x="1" y="0" z="1" />
  <Block id="data_energistics:budding_data_crystal_3" x="1" y="0" z="2" />
  <Block id="ae2:flawless_budding_quartz" x="2" y="0" z="1" />
  <Block id="ae2:quartz_block" x="2" y="0" z="2" />


  <Block id="data_energistics:data_mysterious_cube" x="1" y="1" z="1" />
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
A special variant based on AE meteorites, with a larger body and crater than an ordinary AE meteorite. Its core has nine positions below the center and nine above it. Each of these 18 positions independently selects one mother rock.

| Mother rock | Chance per position |
|---|---:|
| <ItemImage id="ae2:damaged_budding_quartz" /> Damaged Budding Certus Quartz | 13.75% |
| <ItemImage id="ae2:chipped_budding_quartz" /> Chipped Budding Certus Quartz | 13.75% |
| <ItemImage id="ae2:flawed_budding_quartz" /> Flawed Budding Certus Quartz | 13.75% |
| <ItemImage id="ae2:flawless_budding_quartz" /> Flawless Budding Certus Quartz | 13.75% |
| <ItemImage id="data_energistics:budding_data_crystal_0" /> Deactivated Data Crystal Motherrock | 11.75% |
| <ItemImage id="data_energistics:budding_data_crystal_1" /> Powerless Data Crystal Motherrock | 10.75% |
| <ItemImage id="data_energistics:budding_data_crystal_2" /> Fatigued Data Crystal Motherrock | 9.75% |
| <ItemImage id="data_energistics:budding_data_crystal_3" /> Deficient Data Crystal Motherrock | 8.75% |
| <ItemImage id="data_energistics:budding_data_crystal_4" /> Charged Data Crystal Motherrock | 4.00% |

Higher-quality Data Crystal mother rocks are progressively rarer. A center-layer Certus bud can appear only when the mother rock below it is a Certus tier; a Data Crystal mother rock below leaves that center position empty. This distribution applies only to newly generated digitized meteorites.

All five Data Crystal mother rocks preserve their own tier when mined with Silk Touch. Without Silk Touch, their existing downgrade rules remain:

| Mother rock | Ordinary mining |
|---|---|
| <ItemImage id="data_energistics:budding_data_crystal_0" /> Deactivated | No drop |
| <ItemImage id="data_energistics:budding_data_crystal_1" /> Powerless | <ItemImage id="data_energistics:budding_data_crystal_0" /> Deactivated |
| <ItemImage id="data_energistics:budding_data_crystal_2" /> Fatigued | <ItemImage id="data_energistics:budding_data_crystal_1" /> Powerless |
| <ItemImage id="data_energistics:budding_data_crystal_3" /> Deficient | <ItemImage id="data_energistics:budding_data_crystal_2" /> Fatigued |
| <ItemImage id="data_energistics:budding_data_crystal_4" /> Charged | <ItemImage id="data_energistics:budding_data_crystal_2" /> Fatigued |

> ⚠ it will be the start of your mod
>

---

## Digital meteorite

| Meteorite | Basic drop | Additional output |
|---|-|---|
| <ItemImage id="ae2:sky_stone_block" /> | 100% <ItemImage id="ae2:sky_stone_block" /> | None |
| <ItemImage id="data_energistics:data_meteorite_0" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 10% + luck per level +3% <ItemImage id="ae2:ender_dust" /> | 5% + luck per level +3%: generate 1 Dispersing Manifest Binary entities |
| <ItemImage id="data_energistics:data_meteorite_1" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 20% + Luck per level +3% <ItemImage id="ae2:ender_dust" /> 10% + Luck per level +3% <ItemImage id="ae2:sky_dust" /> | 10% + Luck +3% per level: Generates 1 Dispersing Manifest Binary entity |
| <ItemImage id="data_energistics:data_meteorite_2" /> | 100% <ItemImage id="ae2:sky_stone_block" /> 25% + luck per level +3% <ItemImage id="ae2:ender_dust" /> 15% + luck per level +3% <ItemImage id="ae2:sky_dust" /> | 15% + Luck per level +3%: Generate 1~2 Dispersing Manifest Binary entities 15%: 6³ random spatial teleport|

---

## Dispersing Manifest Binary

<GameScene zoom="6" background="transparent">
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:0}" x="0" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:1}" x="1" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:2}" x="2" y="0" z="0" />
  <Entity id="data_energistics:dispersing_data" data="{TextureVariant:3}" x="3" y="0" z="0" />
  <IsometricCamera yaw="0" pitch="25" />
</GameScene>

Dispersing Manifest Binary are mining digitized meteorites (drop rates as shown in the table above) and mining residual data ores (guaranteed to drop 1~3).<br>

> Nearby Dispersing Manifest Binary attract and merge automatically. Their names show the contained amount, up to 8 Binary units per entity. At 8 units, the entity is about 2 times its base size.
> ⚠ if you leave it alone for a minute, it will fade away<br>
>
When he appears in the world, you need to use it
<Row>
    <ItemLink id="data_energistics:radix_containment_sphere" />
    <ItemImage id="data_energistics:radix_containment_sphere" />
</Row>
    <RecipeFor id="data_energistics:radix_containment_sphere" />
captures it; when the sphere's energy is depleted, it leaks one Manifest Binary into the world every 100 ticks.


You can also make one
<Row>
    <ItemLink id="data_energistics:me_vacuum" />
    <ItemImage id="data_energistics:me_vacuum" />
</Row>
    <RecipeFor id="data_energistics:me_vacuum" />
Place Radix Containment Sphere in it for area capture

---

## Data Corrosion Liquid
<GameScene zoom="3" background="transparent">
    <Block id="data_energistics:guide_data_corrosion_liquid_display" x="3" y="0" z="4" />
  <IsometricCamera yaw="150" pitch="25" />
</GameScene>

Comes with variant meteorites, but generally only forms in meteorites that crash onto plains.<br>
<Row>
    <ItemImage id="data_energistics:me_vacuum" />
</Row>


You can also make one
<Row>
    <ItemLink id="data_energistics:me_vacuum" />
    <ItemImage id="data_energistics:me_vacuum" />
</Row>
    <RecipeFor id="data_energistics:me_vacuum" />
Perform a range of suction on the loaded fluid disk<br>
> ⚠ it deals about twice the damage of Dragon Breath, so proceed with caution
>

## Digitalized Meteorite Compass

> Meteorite Compass after specialization (synthesis), specifically used to find meteorites in this module
> The usage method is the same as a regular meteorite compass

<Row>
    <ItemImage id="data_energistics:data_meteorite_compass" scale="6" />
    <RecipeFor id="data_energistics:data_meteorite_compass" />
</Row>
