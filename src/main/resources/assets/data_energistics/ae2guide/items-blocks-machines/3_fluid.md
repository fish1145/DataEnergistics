---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: Fluid
  icon: data_energistics:ender_bucket
  position: 3
item_ids:
- data_energistics:ender_bucket
- data_energistics:data_corrosion_liquid_bucket
---

# Fluid

<Row>
  <ItemImage id="data_energistics:ender_bucket" scale="6" />
  <ItemImage id="data_energistics:data_corrosion_liquid_bucket" scale="6" />
</Row>

---

## Ender Bucket

When a living creature comes into contact with Ender Fluid, it will randomly teleport within a nearby area.

<Row>
    <ItemImage id="data_energistics:ender_bucket" scale="3" />
    <Recipe id="data_energistics:data_energistics/data_reassembler/ender" />
</Row>

<GameScene zoom="6" background="transparent">
  <Block id="data_energistics:guide_ender_display" x="0" y="0" z="0" />
  <IsometricCamera yaw="25" pitch="25" />
</GameScene>

This fluid itself does not continuously cause damage, but it constantly disrupts the position of creatures standing within it.

---

## Data Corrosion Liquid Bucket

Data Corrosive Liquid is highly corrosive; living beings exposed to it will suffer extremely high damage. It may also form around digital fossil meteorites.

<Column>
  <Row>
    <ItemImage id="data_energistics:data_corrosion_liquid_bucket" scale="3" />
  </Row>
</Column>

<GameScene zoom="6" background="transparent">
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="0" y="0" z="0" />
  <IsometricCamera yaw="25" pitch="25" />
</GameScene>

It also emits a faint light, making it suitable for hazardous areas and special traps.
