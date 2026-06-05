---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Data Crystal
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

# Data Crystal Family
Explored, moved, and edited within a mysterious crystalline structure, data crystals possess excellent data conductivity. They are often used in circuit boards and frameworks, but no one knows what consequences may follow.
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

## Data Crystal Motherrock / Block
Like Certus Quartz, it arrived with meteorites from across the universe. You can find it inside the AE Digitalized Meteorite after it lands.
There is a 27% chance of 1-2 Charged Data Crystal Motherrocks spawning randomly inside the meteorite.
<GameScene zoom="4" background="transparent">
    <Block id="data_energistics:budding_data_crystal_0" x="0" y="0" z="0" />
    <Block id="data_energistics:budding_data_crystal_4" x="2" y="0" z="0" />
    <IsometricCamera yaw="0" pitch="25" />
</GameScene>  

---

Activating a Deactivated Data Crystal Motherrock yields a Powerless Data Crystal Motherrock. After each successful growth, there is a 1/12 chance of degrading one tier.
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

## Data Crystal Buds

<Row>
  <ItemImage id="small_data_crystal_bud" scale="3" />
  <ItemImage id="medium_data_crystal_bud" scale="3" />
  <ItemImage id="large_data_crystal_bud" scale="3" />
  <ItemImage id="data_crystal_cluster" scale="3" />
  <ItemImage id="data_crystal" scale="3" />
  <ItemImage id="data_dust" scale="3" />
</Row>

## Reference Table

| Stage | Drop without Silk Touch | Drop and Probability |
| :-- | :-- | :-- |
| <ItemImage id="data_energistics:small_data_crystal_bud" /> | <ItemImage id="data_energistics:data_dust" /> | 0% <ItemImage id="data_energistics:data_dust" /> |
| <ItemImage id="data_energistics:medium_data_crystal_bud" /> | <ItemImage id="data_energistics:data_dust" /> | 15% <ItemImage id="data_energistics:data_dust" /> |
| <ItemImage id="data_energistics:large_data_crystal_bud" /> | <ItemImage id="data_energistics:data_dust" /> | 25% <ItemImage id="data_energistics:data_dust" /> |
| <ItemImage id="data_energistics:data_crystal_cluster" /> | <ItemImage id="data_energistics:data_dust" /> <ItemImage id="data_energistics:data_crystal" /> | 40% <ItemImage id="data_energistics:data_dust" />, otherwise 60% <ItemImage id="data_energistics:data_crystal" /> |

## Data Crystal
<Column>
    <Row>
        <Recipe id="data_energistics:ae2/charger/data_crystal" />
        <RecipeFor id="data_energistics:data_crystal" />
        <Recipe id="data_energistics:crafting/data_crystal" />
    </Row>
</Column>
Due to the intense activation, it gains excellent conductivity and crystal toughness, and is often used as the framework for various devices.
