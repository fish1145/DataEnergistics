---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Tools/Weapons
  icon: data_energistics:data_sanctifier
  position: 13
item_ids:
- data_energistics:data_crystal_sword
- data_energistics:data_crystal_axe
- data_energistics:data_crystal_pickaxe
- data_energistics:data_crystal_hoe
- data_energistics:data_crystal_shovel
- data_energistics:data_crystal_cutting_knife

- data_energistics:data_light_saber
- data_energistics:data_sanctifier

- data_energistics:matter_converging_crossbow
---

# Tools/Weapons
## Data Crystal
A set of basic tools crafted through a special process. They possess hardness comparable to Netherite while being as light as gold. Due to their unique craft, they can store a small amount of energy.
<Row>
  <ItemImage id="data_energistics:data_crystal_sword" scale="4" />
  <ItemImage id="data_energistics:data_crystal_axe" scale="4" />
  <ItemImage id="data_energistics:data_crystal_pickaxe" scale="4" />
  <ItemImage id="data_energistics:data_crystal_hoe" scale="4" />
  <ItemImage id="data_energistics:data_crystal_shovel" scale="4" />
  <ItemImage id="data_energistics:data_crystal_cutting_knife" scale="4" />
</Row>

| Name | Energy Storage | Attack Speed | Attack Damage | Mining Level/Speed | Installable Upgrades |
|------|---------------|--------------|---------------|-------------------|----------------------|
| <ItemImage id="data_energistics:data_crystal_sword" /> Data Crystal Sword | 20kae | 2 | 10 | N/A | <ItemLink id="ae2:speed_card"/> : +0.2 Attack Speed, +100ae usage / <ItemLink id="ae2:energy_card"/> : Max Energy = Base x (1 + 8 x Energy Cards) |
| <ItemImage id="data_energistics:data_crystal_axe" /> Data Crystal Axe | 20kae | 1.2 | 12 | 5\12 | <ItemLink id="ae2:speed_card"/> : +0.2 Attack\Mining Speed, +usage / <ItemLink id="ae2:energy_card"/> : Max Energy = Base x (1 + 8 x Energy Cards) |
| <ItemImage id="data_energistics:data_crystal_pickaxe" /> Data Crystal Pickaxe | 20kae | 1.4 | 6 | 5\12 | <ItemLink id="ae2:speed_card"/> : +0.2 Attack\Mining Speed, +usage / <ItemLink id="ae2:energy_card"/> : Max Energy = Base x (1 + 8 x Energy Cards) |
| <ItemImage id="data_energistics:data_crystal_hoe" /> Data Crystal Hoe | 20kae | 1.4 | 6 | 5\12 | <ItemLink id="ae2:speed_card"/> : +0.2 Attack\Mining Speed, +usage / <ItemLink id="ae2:energy_card"/> : Max Energy = Base x (1 + 8 x Energy Cards) |
| <ItemImage id="data_energistics:data_crystal_shovel" /> Data Crystal Shovel | 20kae | 1.4 | 6 | 5\12 | <ItemLink id="ae2:speed_card"/> : +0.2 Attack/Mining Speed, +usage / <ItemLink id="ae2:energy_card"/> : Max Energy = Base x (1 + 8 x Energy Cards) |
| <ItemImage id="data_energistics:data_crystal_cutting_knife" /> Data Cutting Knife | 20kae | N/A | When loaded with eae, shift-right-click to rename AE devices | Replaced with special function, stored data flow can be transferred between teleport anchors | <ItemLink id="ae2:energy_card"/> : Max Energy = Base x (1 + 8 x Energy Cards) |

These AE-powered tools can also be charged through Forge Energy devices. Incoming FE is converted to AE at the AE2 energy ratio and stored in the item. This compatibility only handles charging and will not output the tool's AE back as FE.

---

## Data Light Saber
A fun and dangerous little toy, perfect for cosplaying a Jedi?
<Row>
    <ItemImage id="data_energistics:data_light_saber" scale="4" />
    <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" scale="4" />
</Row> 
Has 20kae durability, 2 attack speed, and 17 attack damage. Right-click and hold briefly to charge, then release to throw. Open your inventory and right-click with dye or a color applicator to change its color.

# Data Sanctifier  
A special lightsaber crafted from red, yellow, and blue lightsabers.
<Row>
    <ItemImage id="data_energistics:data_sanctifier" scale="4" />
    <ItemImage id="data_energistics:data_sanctifier" components="ae2:stored_energy=20000.0d" scale="4" />
</Row> 
Has 20kae durability and 36 attack damage. Right-click and hold briefly to charge, then release to throw.

---

# Matter Converging Crossbow
A crossbow that fires condensed matter - a concerto of force and beauty, tracing civilization's most violent arc through the air.
<Row>
    <ItemImage id="data_energistics:matter_converging_crossbow" scale="4" />
</Row>
Has 20kae durability. Accepts Matter Balls, Singularities, and fully charged unupgraded Data Light Sabers as ammunition.

| Ammunition | Damage | Energy Cost |
|-----------|--------|-------------|
| <ItemImage id="ae2:matter_ball" /> Matter Balls | 10 | 200ae |
| <ItemImage id="ae2:singularity" /> Singularity | 25 | 200ae |
| <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" /> Charged Data Light Saber | 20 (1%~5% max % true damage) | 200000 ae + extra cost (up to 300000 AE / shot) |

---

# Upgrades
<Row>
    <ItemLink id="ae2:speed_card"/> 
    <ItemImage id="ae2:speed_card" />
</Row>
Speed Card:
<Row>
    <ItemImage id="data_energistics:data_crystal_sword" />
    <ItemImage id="data_energistics:data_light_saber" />
    <ItemImage id="data_energistics:data_sanctifier" />
</Row>
: +0.2 Attack Speed, +100 AE usage (reduces charge time when installed on lightsabers)
<Row>
    <ItemImage id="data_energistics:data_crystal_axe" />
    <ItemImage id="data_energistics:data_crystal_pickaxe" />
    <ItemImage id="data_energistics:data_crystal_hoe" />
    <ItemImage id="data_energistics:data_crystal_shovel" />
</Row>  
: +0.2 Attack Speed, +100 AE usage, +8 Mining Efficiency

<Row>
    <ItemImage id="data_energistics:matter_converging_crossbow" />
</Row>
Projectile Velocity:  
Normal Ammo: 3.15 + Speed Cards x 1.0  
Special Ammo: 3.15 x 1.5 + Speed Cards x 1.0 (Special ammo does not benefit from projectile velocity bonus)  
When Speed Cards >= 4, pulling the string fully will automatically load and fire immediately.

---

<Row>
 <ItemLink id="ae2:energy_card"/>  
 <ItemImage id="ae2:energy_card" />
</Row>
Energy Card:
<Row>
    <ItemImage id="data_energistics:data_crystal_sword" />
    <ItemImage id="data_energistics:data_crystal_axe" />
    <ItemImage id="data_energistics:data_crystal_pickaxe" />
    <ItemImage id="data_energistics:data_crystal_hoe" />
    <ItemImage id="data_energistics:data_crystal_shovel" />
    <ItemImage id="data_energistics:data_crystal_cutting_knife" /> 
    <ItemImage id="data_energistics:data_light_saber" />
    <ItemImage id="data_energistics:data_sanctifier" />
</Row>  
: Max Energy = Base x (1 + 8 x Energy Cards)  

<ItemImage id="data_energistics:matter_converging_crossbow" />  
: Max Energy = Base x (1 + 8 x Energy Cards)  
% True Damage = 1% + 1% per 25000 AE extra energy cost.

---

<Row>
    <ItemLink id="data_energistics:redstone_tuning_card" /> 
    <ItemImage id="data_energistics:redstone_tuning_card" />
</Row>
Redstone Tuning Card:  
<ItemImage id="data_energistics:matter_converging_crossbow" /> :  
Normal ammo energy cost per shot = Base x 5. When installed, projectiles gain homing targeting the nearest non-player living entity.  

---

<Row>
    <ItemLink id="data_energistics:card_saber_energy"/>  
    <ItemImage id="data_energistics:card_saber_energy" />
</Row>
Saber Energy Card:  
<ItemImage id="data_energistics:matter_converging_crossbow" /> : Final Damage = Base x (Saber Energy Cards x 2) x Current Velocity (3.15) [Crit x 1.5]. When using a charged <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" /> as ammo, also increases max % true damage.  
<ItemImage id="data_energistics:data_light_saber" /> : Max Damage = Base x (Saber Energy Cards x 2), +500 AE usage. When fully charged, left-click attacks fire a blade projectile. <ItemImage id="data_energistics:data_sanctifier" components="ae2:stored_energy=20000.0d" /> becomes twice as large.  
<ItemImage id="data_energistics:data_crystal_cutting_knife" /> : Increases teleport range.  
The following tools also gain a data flow storage slot:  
<ItemImage id="data_energistics:data_crystal_sword" /> : Max Damage = Base x (Saber Energy Cards x 2), +500 AE usage. Attacks consume 20 data flow to disable the target entity AI for 20 ticks.  
<ItemImage id="data_energistics:data_crystal_axe" /> : Consumes 20 data flow to fell an entire tree, +500 AE usage.  
<ItemImage id="data_energistics:data_crystal_pickaxe" /> : Consumes 20 data flow to vein-mine nearby ores and duplicate them once, +500 AE usage.  
<ItemImage id="data_energistics:data_crystal_hoe" /> : Consumes 20 data flow to keep tilled soil permanently hydrated, +500 AE usage.  
<ItemImage id="data_energistics:data_crystal_shovel" /> : Shift-right-click to switch between 3x3 and 5x5 mining area, +500 AE usage, consumes 20 data flow while mining.  
