---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: Upgrades
  icon: data_energistics:card_saber_energy
  position: 4
item_ids:
- data_energistics:card_saber_energy
- data_energistics:redstone_tuning_card
---

# Upgrades

## Saber Energy Card
<Row>
    <ItemLink id="data_energistics:card_saber_energy"/>  
    <ItemImage id="data_energistics:card_saber_energy" />
    <RecipeFor id="data_energistics:card_saber_energy" />
</Row>
Saber Energy Card:  
<ItemImage id="data_energistics:matter_converging_crossbow" /> : Final damage = base damage × (Saber Energy Card quantity × 2) × Current speed (3.15) [critical hit × 1.5] when <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" /> increases the maximum percentage true damage by 5% when ammo  
<ItemImage id="data_energistics:data_light_saber" /> : Max damage = base damage × (Saber Energy Cards × 2). Adds 40 AE to the action cost (50 AE total). A powered <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" /> can fire a light blade on left-click, while <ItemImage id="data_energistics:data_sanctifier" components="ae2:stored_energy=20000.0d" /> becomes twice its normal size.  
<ItemImage id="data_energistics:data_crystal_cutting_knife" /> : Expand the transmission range  
The following tools will also add a Data Flow storage slot  
<ItemImage id="data_energistics:data_crystal_sword" /> : Max damage = base damage × (Saber Energy Card quantity × 2) Increases 40ae extra energy cost (total power cost 50ae), consumes 20Data Flow on attack to strip entities of 20 Tick AI  
<ItemImage id="data_energistics:data_crystal_axe" /> : Consumes 20 data to chain an entire tree, increasing additional energy consumption by 40ae (total power consumption 50ae)   
<ItemImage id="data_energistics:data_crystal_pickaxe" /> : Consumes 20 Data Chain Peripheral Ore and copies it, increases additional energy cost by 40 ae (total power consumption 50 ae)   
<ItemImage id="data_energistics:data_crystal_hoe" />: Consumes 20 data to farmland that never loses water, increases extra energy consumption by 40ae (total power consumption 50ae)   
<ItemImage id="data_energistics:data_crystal_shovel" /> : You can right-click stealth to adjust the destruction range of 3×3 or 5×5, increasing the extra energy cost by 40ae (total power cost 50ae), and consuming 20 data during destruction  


## Redstone Tuning Card

<Row>
    <ItemLink id="data_energistics:redstone_tuning_card" />
    <ItemImage id="data_energistics:redstone_tuning_card"  />
    <RecipeFor id="data_energistics:redstone_tuning_card" />
</Row>

The Redstone Tuning Card adds redstone-controlled behavior to pattern providers and the Matter Converging Crossbow. Each device supports at most one card.

### Pattern Providers

<Row>
    <ItemImage id="ae2:pattern_provider" />
    <ItemImage id="data_energistics:adaptive_pattern_provider" />
    <ItemImage id="data_energistics:adaptive_pattern_provider_part" />
</Row>

It supports the AE2 Pattern Provider, Adaptive Pattern Provider, and other compatible pattern providers. After installation, the interface offers two modes:

- Emit mode: The provider emits a short redstone pulse whenever it dispatches crafting ingredients.
- Receive mode: When the provider receives a redstone pulse, it requests the primary output of every pattern that can currently be crafted.

### Matter Converging Crossbow

<Row>
    <ItemImage id="data_energistics:matter_converging_crossbow" />
</Row>

After installation, the energy consumption per round of regular ammo becomes five times the base energy consumption, and the projectile tracks the nearest non-player living entity.
