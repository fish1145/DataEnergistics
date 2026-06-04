---
navigation:
  parent: data_energistics:items-blocks-machines/data_energistics.md
  title: Universal Terminal
  icon: data_energistics:universal_terminal
  position: 14
item_ids:
- data_energistics:universal_terminal

---

# Universal Terminal
Consolidates multiple terminal functions into a single item, reducing the need to frequently switch between the toolbar and inventory.

<Row>
  <ItemImage id="data_energistics:universal_terminal" scale="6" />
</Row>

---

# Basic Usage
The Universal Terminal is essentially a "terminal container."

It can install multiple terminal items into a single item and switch between the currently active terminal interface during use.  
The item tooltip directly lists all currently installed terminals.

Built-in default support:
- ME Terminal
- Crafting Terminal
- Pattern Access Terminal
- Pattern Encoding Terminal

In addition, the system will automatically attempt to recognize compatible terminal-type parts from other mods and register them as installable objects.

---

# Crafting and Expansion
The Universal Terminal has two merging rules:

1. Universal Terminal + a supported terminal
- Installs the new terminal into the existing Universal Terminal
- If the terminal is already installed, it will not be added again

2. Two different supported terminals
- Directly crafts them into a new Universal Terminal
- The new Universal Terminal preserves both terminals

Limitations:
- Two Universal Terminals cannot be merged together
- Two identical terminals cannot be merged into something new

---

# Switching
The Universal Terminal remembers a "currently active terminal."

When used:
- Opens the interface corresponding to the currently active terminal
- Can cycle through installed terminals
- After switching, the current mode is saved in the item data

If the currently active terminal is invalid, the system automatically falls back to the first terminal in the installed list.

---

# Data Preservation
The Universal Terminal stores the following inside the item:
- List of installed terminals
- Original terminal item information for each terminal
- Name of the currently active terminal

This means:
- A single Universal Terminal can grow over time
- Adding new terminals later does not lose existing content
- The tooltip displays all currently installed terminals

---

# Compatibility Notes
The Universal Terminal is primarily compatible with AE2 native terminals.  
For other mod terminals, the support works as follows:
- Automatically detects the terminal part type
- Automatically matches the appropriate menu type
- Identifies whether it belongs to Storage, Crafting, Pattern Access, or Pattern Encoding

Therefore, some third-party terminals can also be integrated into the Universal Terminal.  
If a terminal is not recognized, it is usually because its part type or menu structure does not match the current compatibility rules.
