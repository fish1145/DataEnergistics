---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Data Extractor
  icon: data_energistics:data_extractor
  position: 7
item_ids:
- data_energistics:data_extractor
- data_energistics:data_carrier
---

# Data Extractor
The Data Extractor draws hidden patterns from vast streams of information, transforming raw materials into structured data.
<GameScene zoom="6" background="transparent">
    <Block id="data_energistics:data_extractor" x="0" y="0" z="0" />
   <IsometricCamera yaw="205" pitch="25" />
</GameScene>
<Row>
  <RecipeFor id="data_energistics:data_extractor" />
</Row>

---

# Upgrades

<Row>
    <ItemLink id="ae2:capacity_card"/>
    <ItemImage id="ae2:capacity_card" />
</Row>
Capacity Card:  
Increases both the target limit and the working range.

Target Limit Formula:  
Target Limit = Base Target Limit + Capacity Cards x Extra Targets Per Card

Default configuration:  
Target Limit = 20 + Capacity Cards x 5

Range Formula:
- Horizontal: each Capacity Card adds 1 block to each side
- Vertical Height = 3 + Capacity Cards x 2

Default:
- 0 Capacity Cards: horizontal base radius 1, vertical height 3
- Each additional card: +1 block per side horizontally, +2 blocks vertically

---

<Row>
    <ItemLink id="ae2:speed_card"/>
    <ItemImage id="ae2:speed_card" />
</Row>
Speed Cards shorten the working cycle.

Interval Formula:  
Final Work Time (seconds) = Base Work Time - Speed Cards x 1

Default base work time is 5 seconds:
- 0 cards: works every 5 seconds
- 1 card: works every 4 seconds
- 2 cards: works every 3 seconds
- 3 cards: works every 2 seconds
- 4+ cards: works every 1 second

---

<Row>
    <ItemLink id="ae2:energy_card"/>
    <ItemImage id="ae2:energy_card" />
</Row>
Energy Cards increase the base data flow generated per cycle and boost the internal AE energy buffer.

Base Data Flow Formula:  
Base Data Flow Per Cycle = Base Value + Energy Cards x 200 + Damage Dealt x Data Flow Per Damage

Default configuration:  
Base Data Flow Per Cycle = 100 + Energy Cards x 200 + Damage Dealt x 20

Internal AE Buffer Formula:  
Internal Buffer = 1600 + Energy Cards x 100

---

# Multi-Target Multiplier
When a single cycle affects multiple targets, the base data flow is multiplied by a target multiplier.

Formula:  
Final Data Flow = Base Data Flow Per Cycle x (1 + max(0, Target Count - 1) x Extra Target Multiplier)

Default extra target multiplier is 0.25:
- 1 target: x 1.00
- 2 targets: x 1.25
- 3 targets: x 1.50
- 4 targets: x 1.75

These base values can be further adjusted in the configuration file.

---

# Data Carrier
<ItemImage id="data_energistics:data_carrier"  scale="6" />

The Data Extractor uses a blank  
<Row>
    <ItemLink id="data_energistics:data_carrier"/>
    <ItemImage id="data_energistics:data_carrier" />
</Row>
carrier as its basic medium.  
Once recording is complete, it automatically transforms into the corresponding finished data carrier.

Basic Rules:
- Place a blank data carrier in the top-left main slot
- One blank carrier can only record one category at a time
- Once mob data recording starts, ore or crop data cannot be mixed in
- Once ore data recording starts, mob or crop data cannot be mixed in
- Once crop data recording starts, mob or ore data cannot be mixed in
- When progress reaches the requirement, the blank carrier transforms into the corresponding finished carrier

---

# Mob Data Carrier
<Row>
    <ItemLink id="data_energistics:mob_data_carrier"/>
    <ItemImage id="data_energistics:mob_data_carrier" />
</Row>

Recording Process:
- Place a blank data carrier in the main slot
- A weapon can be placed in the sword slot; if empty, the machine uses base magic damage
- The extractor periodically damages living targets within range
- On the first successful recording, the carrier locks onto the currently damaged mob type
- Afterwards, only effective damage dealt to the same mob type accumulates progress

Progress Sources:
- Progress follows the actual damage dealt to the target mob
- Once the requirement is met, the blank carrier becomes <ItemImage id="data_energistics:mob_data_carrier" />

Features:
- Ideal for locking onto a specific mob type
- The finished carrier can later be used in the Data Mimetic Field to simulate that mob's drops

---

# Ore Data Carrier
<Row>
    <ItemLink id="data_energistics:ore_data_carrier"/>
    <ItemImage id="data_energistics:ore_data_carrier" />
</Row>

Recording Process:
- Place a blank data carrier in the main slot
- Place an ore in the ore slot, or other valid mineral inputs as configured
- On the first recording, the carrier locks onto the mineral target represented by the current input
- Afterwards, only matching inputs continue to advance progress

Progress Sources:
- Default: standard ore counts at a 1:1 ratio
- Default: raw ore counts at a 0.5:1 ratio
- If input_rules contain ore rules, they take priority:
  - recorded_item
  - progress_per_item
  - required_amount

Completion Condition:
- Once the requirement is met, the blank carrier becomes <ItemImage id="data_energistics:ore_data_carrier" />

---

# Crop Data Carrier
<Row>
    <ItemLink id="data_energistics:crop_data_carrier"/>
    <ItemImage id="data_energistics:crop_data_carrier" />
</Row>

Recording Process:
- Place a blank data carrier in the main slot
- Place crops, saplings, fungi, flowers, bamboo, sugar cane, cactus, or other configured valid inputs in the crop slot
- On the first recording, the carrier locks onto the crop target represented by the current input
- Afterwards, only matching inputs continue to advance progress

Progress Sources:
- Default: standard crop inputs count at a 1:1 ratio
- Some mapped inputs can be converted via configuration, for example:
  - Wheat Seeds -> Wheat, 0.5
  - Beetroot Seeds -> Beetroot, 0.5
  - Melon Seeds / Melon Slice -> Melon, 0.5
  - Pumpkin Seeds -> Pumpkin, 0.5
- If input_rules contain crop rules, they take priority:
  - recorded_item
  - progress_per_item
  - required_amount

Valid Targets:
- Regular crops
- Nether Wart
- Saplings and Mangrove Propagules
- Mushrooms and Fungi
- Sweet Berries
- Melons and Pumpkins
- Bamboo, Sugar Cane, Cactus
- All types of flowers
- Any additional inputs you configure

Completion Condition:
- Once the requirement is met, the blank carrier becomes <ItemImage id="data_energistics:crop_data_carrier" />

---

# Configuration Rules
The Data Extractor and Data Mimetic Field share a single rules file:

`config/data_energistics-data_extractor_rules.json`

---

Where:
- input_rules controls what can be placed in each extractor slot, what data is recorded, how much progress each item provides, and the total required amount
- output_rules controls what finished data carriers produce in the Data Mimetic Field

If no matching input_rules entry is found, the Data Extractor falls back to its built-in default rules.
If no corresponding output_rule exists, the Data Mimetic Field falls back to default loot tables or hardcoded drop logic.
