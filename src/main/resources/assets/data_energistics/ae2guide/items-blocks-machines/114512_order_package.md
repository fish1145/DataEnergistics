---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: Order Package
  icon: data_energistics:order_package
  position: 114512
---

# Order Package

<Row>
  <ItemImage id="data_energistics:order_package" scale="6" />
  <ItemImage id="data_energistics:order_package" />
</Row>

An Order Package is a special item for **virtual orders**. It is not an actual item, fluid, or data resource. Instead, it carries a target AEKey as a requested output. When an Order Package with a target is written into a pattern, the crafting system interprets it as a request for that resource.

## Setting An Order Target

1. Right-click the Order Package while holding it to open its interface.
2. Drag the item, fluid, or other AE resource to request into the target slot.
3. The target slot records the resource type, not an amount.
4. Close the interface to save the target in the package's data component.
5. Open the package again to inspect or replace its target.

The target slot accepts JEI and EMI drag-and-drop and any generic resource understood by AE2. Items and fluids are stored as their AEKey; Data Flow and other generic resources retain their original generic-resource key instead of being converted into ordinary items.

Right-click the target slot to clear it. An unconfigured package can still be carried and stored, but cannot be used as a valid virtual-order output.

## Using It In A Pattern

Place a configured Order Package in the output slot of an AE2 pattern encoding terminal and encode it like an ordinary output. When a crafting request is submitted:

- The package output count determines the requested amount.
- The crafting plan reads the target resource saved in the package.
- The package itself is not delivered as the final item.
- The virtual output is useful when automation must request a resource without consuming or delivering a package item.
- The target still needs an available recipe, pattern provider, and crafting CPU.

An Order Package can be used as a virtual output in processing patterns and Trinity crafting. Trinity reads the target AEKey and tracks that resource directly; the pattern and crafting plan still determine the amount, inputs, and execution order.

## Marked And Ordinary Packages

Trinity previews and pattern tools can create packages with a target already marked. A marked package shows a target hint on its icon and displays the target name in its tooltip.

- **Marked package**: can be used as a virtual-order output; its target comes from the package data component.
- **Unmarked package**: has no target and can only be stored or configured later.
- Changing the target does not change the item count; the pattern output count determines the order amount.
- A package can be configured for another target and reused after the pattern is encoded again.

## Notes

- Order Packages cannot stack; each package stores its own target.
- They do not provide AE storage and do not directly transfer items or fluids.
- They cannot bypass recipe, material, workstation, or crafting-CPU availability checks.
- Exact AEKeys, including Data Components or NBT, are preserved without automatically ignoring their differences.
- Target data is stored in the item component and travels with the package into containers and patterns.
