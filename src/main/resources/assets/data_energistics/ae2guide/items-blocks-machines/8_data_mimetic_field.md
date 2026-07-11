---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Data Mimetic Field
  icon: data_energistics:data_mimetic_field
  position: 8
item_ids:
- data_energistics:data_mimetic_field
- data_energistics:mob_data_carrier
- data_energistics:crop_data_carrier
- data_energistics:ore_data_carrier
---

# Data Mimetic Field
In the consciousness sea of extinct civilizations, data rekindles the starlight of life in a silicon universe.
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:data_mimetic_field" x="0" y="0" z="0" />
   <IsometricCamera yaw="205" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:data_mimetic_field" />
</Row>

---

# Upgrades
<Row>
    <ItemLink id="ae2:speed_card"/>
    <ItemImage id="ae2:speed_card" />
</Row>
Speed Card:  
Final Speed = Base Speed - Speed Cards x 40

## Reference Table

| Speed Cards | Cycle | Output Per Carrier | Wrapped Stack Cost Per Carrier |
|:----|:-----|:------|:-----------|
| 0   | 200t | 800    | 320        |
| 1   | 160t | 1600   | 370        |
| 2   | 120t | 2400   | 420        |
| 3   | 80t  | 3200   | 470        |
| 4   | 40t  | 4000   | 520        |
  
Mob/Ore/Crop Output = 800 x (Speed Cards + 1)
Wrapped Stack Cost Per Active Carrier = 320 + Speed Cards x 50
Total Wrapped Stack Cost = Active Carriers x (320 + Speed Cards x 50)
Idle Power Draw = Active Carriers x 500 AE/t

---

<Row>
    <ItemLink id="ae2:capacity_card" /> 
    <ItemImage id="ae2:capacity_card" />
</Row>
Capacity Card:
Base Capacity + Capacity Cards x 4

---

# Carriers  
Above the data chip, every flowing current is a "carrier" - a vessel of an era's will, bearing the key that leads civilization from chaos toward a computable future.
<Row>
    <ItemImage id="data_energistics:mob_data_carrier"  scale="6" />
    <ItemImage id="data_energistics:crop_data_carrier"  scale="6" />
    <ItemImage id="data_energistics:ore_data_carrier"  scale="6" />
</Row>

<ItemImage id="data_energistics:data_mimetic_field"  scale="6" />  
Only accepts completed data carriers of the three types.  
Once inserted, the carrier automatically produces corresponding output based on its type.

## Mob Data Carrier

- Recorded Target: A specific mob type
- Function in the Mimetic Field: Simulates killing that mob and producing its drops
- Drop Sources:
    - Uses configured mob output rules first
    - Otherwise, calls the mob's own loot table
    - If the mob naturally spawns with equipment, those are included in the output
    - If the mob has companion entities, passengers, or mounts, their drops are also calculated

## Ore Data Carrier

- Recorded Target: A specific ore/mineral target
- Function in the Mimetic Field: Continuously produces the corresponding mineral
- Drop Sources:
    - Uses configured ore output rules first
    - If no custom configuration exists, defaults to outputting the recorded mineral item directly

## Crop Data Carrier

- Recorded Target: A specific crop, sapling, fungus, flower, sugar cane, cactus, or other recordable plant
- Function in the Mimetic Field: Continuously produces the corresponding plant drops
- Drop Sources:
    - Uses configured crop output rules first
    - If the record is a sapling/propagule, prioritizes the corresponding tree loot table
    - If the source block was recorded, generates drops from the mature block's drop table
    - If none of the above produce results, defaults to outputting the recorded item itself
