---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: Data Flow Components
  icon: data_energistics:data_flow_component_housing
  position: 2
item_ids:
- data_energistics:data_storage_component_1k
- data_energistics:data_storage_component_4k
- data_energistics:data_storage_component_16k
- data_energistics:data_storage_component_64k
- data_energistics:data_storage_component_256k
- data_energistics:data_storage_component_1m
- data_energistics:data_storage_component_4m
- data_energistics:data_storage_component_16m
- data_energistics:data_storage_component_64m
- data_energistics:data_storage_component_256m
- data_energistics:data_flow_component_housing
- data_energistics:data_flow_cell_1k
- data_energistics:data_flow_cell_4k
- data_energistics:data_flow_cell_16k
- data_energistics:data_flow_cell_64k
- data_energistics:data_flow_cell_256k
- data_energistics:data_flow_cell_1m
- data_energistics:data_flow_cell_4m
- data_energistics:data_flow_cell_16m
- data_energistics:data_flow_cell_64m
- data_energistics:data_flow_cell_256m
- data_energistics:portable_data_flow_cell_1k
- data_energistics:portable_data_flow_cell_4k
- data_energistics:portable_data_flow_cell_16k
- data_energistics:portable_data_flow_cell_64k
- data_energistics:portable_data_flow_cell_256k
- data_energistics:portable_data_flow_cell_1m
- data_energistics:portable_data_flow_cell_4m
- data_energistics:portable_data_flow_cell_16m
- data_energistics:portable_data_flow_cell_64m
- data_energistics:portable_data_flow_cell_256m
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

Data Flow components are a set of components built around Data Flow storage and finished products.

---

## Storage Components

Data Flow storage components determine the capacity level of the corresponding components. Currently, ten tiers are available, ranging from 1K to 256M.

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

  <Row>
    <RecipeFor id="data_storage_component_1m" />
    <RecipeFor id="data_storage_component_4m" />
    <RecipeFor id="data_storage_component_16m" />
  </Row>

  <Row>
    <RecipeFor id="data_storage_component_64m" />
    <RecipeFor id="data_storage_component_256m" />
  </Row>
</Column>

---

## Component Housing

Data Flow Component Housing is used to encapsulate storage components into usable components.

<Row>
  <RecipeFor id="data_flow_component_housing" />
</Row>

---

## Data Flow Storage Cells

After assembly, you can obtain Data Flow storage components of the corresponding grade.

<Column>
  <Row>
    <RecipeFor id="data_flow_cell_1k" />
  </Row>

  <Row>
    <Recipe id="data_energistics:crafting/cell/data_flow_cell_1k_1" />
  </Row>
</Column>

Other storage components also follow the same formula
When there is no Data Flow to hold, you can use Shift + right-click to disassemble

---

## Portable Data Flow Cells

Other portable storage components also follow the same formula

<Column>
  <Row>
    <RecipeFor id="portable_data_flow_cell_1k" />
  </Row>
</Column>

If there is no Data Flow to hold, you can right-click Shift + remove it  

---

# Upgrade  

<Row>
    <ItemLink id="ae2:energy_card"/>  
    <ItemImage id="ae2:energy_card" />
</Row>
Energy Card:
Maximum energy = base capacity × (1 + 8 × number of energy cards)

---

# Crafting Components
  <Row>
    <ItemImage id="data_cell_infinity" />
  </Row>
A component formed by an unknown force that can infinitely retrieve Data Flow and data; no one knows where it comes from or where it will go
