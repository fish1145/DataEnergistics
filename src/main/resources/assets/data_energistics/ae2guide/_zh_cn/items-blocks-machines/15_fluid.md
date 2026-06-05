---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 流体
  icon: data_energistics:ender_bucket
  position: 15
item_ids:
- data_energistics:ender_bucket
- data_energistics:data_corrosion_liquid_bucket
---

# 流体

<Row>
  <ItemImage id="data_energistics:ender_bucket" scale="6" />
  <ItemImage id="data_energistics:data_corrosion_liquid_bucket" scale="6" />
</Row>

---

## 末影桶

当活体接触到末影流体时，会在附近区域内随机传送。

<Row>
    <ItemImage id="data_energistics:ender_bucket" scale="3" />
    <Recipe id="data_energistics:data_energistics/data_reassembler/ender" />
</Row>

<GameScene zoom="6" background="transparent">
  <Block id="data_energistics:guide_ender_display" x="0" y="0" z="0" />
  <IsometricCamera yaw="25" pitch="25" />
</GameScene>

这种流体本身不会持续造成伤害，但会不断扰乱站在其中生物的位置。

---

## 数据腐蚀液桶

数据腐蚀液拥有极强的腐蚀性，接触到它的活体会持续受到极高伤害。它也可能生成在数位化陨石周围。

<Column>
  <Row>
    <ItemImage id="data_energistics:data_corrosion_liquid_bucket" scale="3" />
  </Row>
</Column>

<GameScene zoom="6" background="transparent">
  <Block id="data_energistics:guide_data_corrosion_liquid_display" x="0" y="0" z="0" />
  <IsometricCamera yaw="25" pitch="25" />
</GameScene>

它还会发出微弱光照，适合用在危险区域与特殊陷阱中。
