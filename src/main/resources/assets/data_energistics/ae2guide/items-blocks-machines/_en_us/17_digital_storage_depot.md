---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Digital Storage Depot
  icon: data_energistics:digital_storage_depot
  position: 17
item_ids:
- data_energistics:digital_storage_depot
---

# Digital Storage Depot

The Digital Storage Depot is a hybrid storage block that can be connected to an ME network. It can simultaneously store items, fluids, and non-item Keys such as Data Flow and Data.

<GameScene zoom="6" background="transparent">
  <Block id="data_energistics:digital_storage_depot" x="0" y="0" z="0" />
  <IsometricCamera yaw="205" pitch="25" />
</GameScene>

<Row>
  <RecipeFor id="data_energistics:digital_storage_depot" />
</Row>

---

## Storage Structure

The depot has 21 item slots, 3 fluid slots, and 3 Key slots.

- Item slots can hold regular items and are accessible as ME Storage on the network
- Each fluid slot has a base capacity of 64,000 mB
- Each Key slot has a base capacity of 64,000, ideal for storing non-item, non-fluid Keys like Data Flow and Data
- The same type of fluid or Key only occupies one slot and cannot be recorded across multiple slots

Installing AE2 Capacity Cards increases all three capacity types.

<Row>
  <ItemLink id="ae2:capacity_card" />
  <ItemImage id="ae2:capacity_card" />
</Row>

Capacity Formula:
Final Capacity = Base x (1 + 4 x Capacity Cards)

Up to 4 Capacity Cards can be installed by default. If removing a card would cause existing contents to exceed the downgraded capacity, the card will be protected and cannot be removed.

---

## ME Network

When connected to an ME network, the Digital Storage Depot mounts its internal contents as an independent storage unit.

<Row>
  <ItemImage id="ae2:fluix_covered_cable" />
  <ItemImage id="data_energistics:digital_storage_depot" />
</Row>

It supports AE2 storage priority. Higher priority makes the network prefer writing content here first; lower priority makes it suitable as overflow or backup storage.

AE2 Export Buses can also be used with the depot. Configuring Generic Keys like Data Flow and Data on an Export Bus allows direct import into the depot's Key slots. Regular items and fluids are handled through their respective slots.

---

## Auto-Output

The auto-output button in the left toolbar has three states:

- Off: Does not actively output internal contents
- Container: Automatically pushes items, fluids, and Keys to adjacent containers
- AE: Automatically pushes items, fluids, and Keys back to the connected AE network

When auto-output is in Container mode, the output side configuration can be opened. Items, fluids, and Keys each have independent six-face toggles, allowing different content types to be routed to different devices.

---

## Portable Bucket Mode

When held in hand, the Digital Storage Depot can also function as a portable container. Press Z by default to toggle bucket mode.

When bucket mode is active:

- Right-click a fluid source or fluid container to collect one bucket into the currently selected fluid slot
- Right-click on a placeable position to pour out one bucket from the current fluid slot
- Only one marked fluid slot is exposed as a fluid container to other devices
- If a Key slot is marked, it acts as a portable container for that Key type in AE interactions

Selecting slots:

- Hold Ctrl and scroll to switch the current fluid slot
- Hold Alt and scroll to switch the current Key slot

The tooltip previews items, fluids, and Keys already stored in the depot. When replaced, stored fluids and Keys are preserved with the block data.
