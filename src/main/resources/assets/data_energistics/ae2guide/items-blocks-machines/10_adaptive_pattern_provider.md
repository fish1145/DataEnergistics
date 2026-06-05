---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Adaptive Pattern Provider
  icon: data_energistics:adaptive_pattern_provider
  position: 10
item_ids:
- data_energistics:adaptive_pattern_provider
- data_energistics:adaptive_pattern_provider_part
---

# Adaptive

## Adaptive Pattern Provider
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:adaptive_pattern_provider" x="0" y="0" z="0" />
   <IsometricCamera yaw="25" pitch="25" />
</GameScene>

<Row>
  <RecipeFor id="data_energistics:adaptive_pattern_provider" />
</Row>
Resist, analyze, adapt, merge - executing the original function through inheritance.
Place a corresponding pattern provider in the GUI to inherit its properties.

---

<Row>
    <ItemImage id="data_energistics:adaptive_pattern_provider_part" scale="6" />
  <RecipeFor id="data_energistics:adaptive_pattern_provider_part" />
</Row>
Its part/cable form.

---

# Upgrades
<Row>
    <ItemLink id="ae2:capacity_card" /> 
    <ItemImage id="ae2:capacity_card" />
</Row>
Capacity Card:
Base Capacity + Capacity Cards x 4

<Row>
    <ItemLink id="data_energistics:redstone_tuning_card" /> 
    <ItemImage id="data_energistics:redstone_tuning_card" />
</Row>
Redstone Tuning Card:
When installed, allows adjusting between emitting a redstone pulse or receiving a redstone signal.  
Emit Redstone Pulse: Emits one redstone pulse when the provider dispatches crafting inputs (when installed on AE2CS auto-crafting providers, pulses may be too rapid due to fast crafting; processing patterns require item return and blocking mode for correct pulse emission, otherwise only one pulse is sent).  
Receive Redstone Pulse: On receiving a redstone pulse, automatically requests the primary output for each craftable pattern in the provider once. (If pulses are too rapid, issues may occur.)
