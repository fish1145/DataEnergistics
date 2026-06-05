---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Data Flow Components
  icon: data_energistics:data_flow_component_housing
  position: 4
item_ids:
- data_energistics:data_storage_component_1k
- data_energistics:data_storage_component_4k
- data_energistics:data_storage_component_16k
- data_energistics:data_storage_component_64k
- data_energistics:data_storage_component_256k
- data_energistics:data_flow_component_housing
- data_energistics:data_flow_cell_1k
- data_energistics:data_flow_cell_4k
- data_energistics:data_flow_cell_16k
- data_energistics:data_flow_cell_64k
- data_energistics:data_flow_cell_256k
- data_energistics:portable_data_flow_cell_1k
- data_energistics:portable_data_flow_cell_4k
- data_energistics:portable_data_flow_cell_16k
- data_energistics:portable_data_flow_cell_64k
- data_energistics:portable_data_flow_cell_256k
- data_energistics:data_cell_infinity
---

# Data Flow Components

<Column>
  <Row>
    <ItemImage id="data_storage_component_1k" scale="4" />
    <ItemImage id="data_storage_component_4k" scale="4" />
    <ItemImage id="data_storage_component_16k" scale="4" />
  </Row>

  <Row>
    <ItemImage id="data_storage_component_64k" scale="4" />
    <ItemImage id="data_storage_component_256k" scale="4" />
    <ItemImage id="data_flow_component_housing" scale="4" />
  </Row>
</Column>

Data Flow Components are a set of components and finished cells built around data flow storage.

---

## Storage Components

Data Flow Storage Components determine the capacity tier of the corresponding cell. Five tiers from 1K to 256K are currently available.

<Column>
  <Row>
    <RecipeFor id="data_storage_component_1k" />
    <RecipeFor id="data_storage_component_4k" />
    <RecipeFor id="data_storage_component_16k" />
  </Row>

  <Row>
    <RecipeFor id="data_storage_component_64k" />
    <RecipeFor id="data_storage_component_256k" />
  </Row>
</Column>

---

## Component Housing

The Data Flow Component Housing is used to encase a storage component into a usable cell.

<Row>
  <RecipeFor id="data_flow_component_housing" />
</Row>

---

## Data Flow Storage Cells

Once assembled, you get the corresponding tier of Data Flow Storage Cell.

<Column>
  <Row>
    <RecipeFor id="data_flow_cell_1k" />
  </Row>

  <Row>
    <Recipe id="data_energistics:crafting/cell/data_flow_cell_1k_1" />
  </Row>
</Column>

Other storage components follow the same recipe pattern.
When not holding any data flow, you can disassemble it with Shift+Right-click.

---

## Portable Data Flow Cells

Other portable storage cells follow the same recipe pattern.

<Column>
  <Row>
    <RecipeFor id="portable_data_flow_cell_1k" />
  </Row>
</Column>

When not holding any data flow, you can disassemble it with Shift+Right-click.

---

# Upgrades  

<Row>
    <ItemLink id="ae2:energy_card"/>  
    <ItemImage id="ae2:energy_card" />
</Row>
Energy Card:
Max Energy = Base Capacity x (1 + 8 x Energy Cards)

---

# Creative Cell
  <Row>
    <ItemImage id="data_cell_infinity" />
  </Row>
A cell formed by an unknown power, capable of extracting unlimited data flow and data. No one knows where it came from, and no one knows where it will go.
