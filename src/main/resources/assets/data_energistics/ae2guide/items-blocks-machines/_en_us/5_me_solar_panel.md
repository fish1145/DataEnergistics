---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: ME Solar Panel
  icon: data_energistics:me_solar_panel
  position: 6
item_ids:
- data_energistics:me_solar_panel
- data_energistics:me_solar_panel_part
---

# ME Solar Panel
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:me_solar_panel" x="0" y="0" z="0" />
   <IsometricCamera yaw="25" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:me_solar_panel" />
</Row>
A small and compact solar generator. It requires no blocks directly above it to function.
Generates 3000 AE/T during the day, and 1/3 of that at night.

--- 

ME Solar Panel (Part form)  

<Row>
    <ItemImage id="data_energistics:me_solar_panel_part" scale="6" />
</Row>  
<Row>
  <RecipeFor id="data_energistics:me_solar_panel_part" />
</Row>
A small and compact solar generator. It requires no blocks directly above it to function.
Due to its part form, the reduced thickness weakens its generation capacity.
Generates 2500 AE/T during the day, and 1/3 of that at night. When placed on a side face, generation is further reduced by 1/3. When placed on the bottom face, it generates nothing.
---

# Upgrades
 <ItemLink id="ae2:speed_card"/>: +75% generation per card  

<Row>
    <ItemImage id="ae2:speed_card" />
</Row>
Formula: Base Generation x (1 + Speed Cards x 0.75)

---

<ItemLink id="ae2:energy_card"/>  : +80000 AE capacity per card  

<Row>
    <ItemImage id="ae2:energy_card" />
</Row>
Formula: Base Capacity 160000 AE + Energy Cards x 80000 AE
