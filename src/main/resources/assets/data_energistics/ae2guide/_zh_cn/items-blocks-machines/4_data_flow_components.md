---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: 数据流元件
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

# 数据流元件

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

数据流元件是一组围绕数据流存储构建的组件与成品元件。

---

##  存储组件

数据流存储组件决定了对应元件的容量等级。当前提供了 1K 到 256K 的五个等级。

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

##  元件外壳

数据流元件外壳用于将存储组件封装为可使用的元件。

<Row>
  <RecipeFor id="data_flow_component_housing" />
</Row>

---

##  数据流存储元件

组装完成后，可以得到对应等级的数据流存储元件。

<Column>
  <Row>
    <RecipeFor id="data_flow_cell_1k" />
  </Row>

  <Row>
    <Recipe id="data_energistics:crafting/cell/data_flow_cell_1k_1" />
  </Row>
</Column>

其他存储组件也是一致的配方
没有容纳任何数据流时，可以 Shift+右键拆卸

---

##  便携式数据流元件

其他便携存储组件也是一致的配方

<Column>
  <Row>
    <RecipeFor id="portable_data_flow_cell_1k" />
  </Row>
</Column>

没有容纳任何数据流时，可以 Shift+右键拆卸  

---

# 升级  

<Row>
    <ItemLink id="ae2:energy_card"/>  
    <ItemImage id="ae2:energy_card" />
</Row>
能源卡:
最大能源 = 基础容量 × (1 + 8 × 能源卡数量)

---

# 创造元件
  <Row>
    <ItemImage id="data_cell_infinity" />
  </Row>
一种未知力量形成的元件，能无限取出数据流以及数据，没有人知道它从何而来，也没有人知道它将会去哪