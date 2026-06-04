---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Fluids
  icon: data_energistics:ender_bucket
  position: 15
item_ids:
- data_energistics:ender_bucket
- data_energistics:data_corrosion_liquid_bucket
---

# Fluids

<Row>
  <ItemImage id="data_energistics:ender_bucket" scale="6" />
  <ItemImage id="data_energistics:data_corrosion_liquid_bucket" scale="6" />
</Row>

---

## Ender Bucket

When a living entity comes into contact with ender fluid, it will be randomly teleported within the nearby area.

<Row>
    <ItemImage id="data_energistics:ender_bucket" scale="3" />
    <Recipe id="data_energistics:data_energistics/data_reassembler/ender" />
</Row>

<GameScene zoom="6" background="transparent">
  <Block id="data_energistics:guide_ender_display" x="0" y="0" z="0" />
  <IsometricCamera yaw="25" pitch="25" />
</GameScene>

This fluid does not deal continuous damage by itself, but it constantly disrupts the position of any creature standing in it.

---

## Data Corrosion Liquid Bucket

Data Corrosion Liquid is extremely corrosive. Living entities that come into contact with it will suffer continuous, very high damage. It may also generate around Digitalized Meteorites.

<Column>
  <Row>
    <ItemImage id="data_energistics:data_corrosion_liquid_bucket" scale="3" />
  </Ro
