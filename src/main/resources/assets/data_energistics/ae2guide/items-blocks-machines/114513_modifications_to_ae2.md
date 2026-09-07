---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: Changes to AE2
  icon: ae2:controller
  position: 114513
---

# Changes to AE2

Data Energistics extends AE2 crafting planning, processing patterns, pattern providers, storage keys, and terminal interfaces while preserving AE2's network and storage contracts. This page summarizes the changes that affect normal gameplay and automation.

## Trinity Planning Improvements Compared With AE2

Ordinary acyclic requests may still use AE2's native planner. When Trinity takes over a request, the main improvements are narrower graph search, explicit cycle handling, result reuse, and controlled computation scheduling:

- **Compile only the target's reachable recipe graph**: Trinity collects the reachable subgraph backwards from the requested output, deduplicates and expands input-binding variants, and compiles an immutable structure. A request does not traverse the entire recipe network when unrelated recipes cannot produce its target.
- **Partition cycles with strongly connected components**: Tarjan analysis separates cyclic components from acyclic portions and builds a condensed DAG. Acyclic portions use reverse-demand propagation, while each cycle is solved inside its own component instead of being repeatedly expanded into an incorrect linear chain.
- **Use exact solving and compressed plans for cycles**: Deterministic cycles derive their starting seed and repetition count from net changes and maximum prefix deficits. More complex cycles use non-negative integer optimization to minimize recipe firings, then verify the result exactly with `BigInteger`. Repeated rounds are stored as compressed schedules rather than redundant per-round plan nodes.
- **Aggregate demand and select routes globally**: Demand from multiple producers, boundary materials, and cycle seeds is resolved together. A shared search budget allows bounded backtracking to choose a feasible route instead of committing to one local producer path.
- **Reuse layered cache entries and merge in-flight requests**: Reachable graphs, pattern variants, target structures, route hints, and identical in-flight requests can be reused. Concurrent calculations with the same Grid, graph revision, and request semantics join one shared computation; graph revisions and Grid unloads invalidate or cancel stale work.
- **Schedule calculations asynchronously and with bounds**: Initial planning and Trinity CPU remaining-work replanning use separate bounded worker lanes and queues, so long searches do not occupy the game thread. If the optimality-proof deadline is reached after a feasible result has been verified, that executable plan is retained; requests beyond search bounds receive an explicit diagnostic.

Here, "calculation acceleration" means faster and more reusable crafting-plan search and replanning. It is separate from machine processing speed provided by Speed Cards or Energy Cards. Requests that do not need Trinity features still retain AE2's native path as a fallback.

## Cyclic Crafting Orders

AE2's native planner is best suited to acyclic dependencies. Data Energistics adds cyclic recipe planning and execution for Trinity crafting CPUs, allowing recipe chains in which outputs return to an earlier step, several recipes provide inputs to each other, or intermediate materials are reused across repeated rounds.

For example:

1. Recipe A consumes X and produces Y.
2. Recipe B consumes Y, returns X, and produces the requested Z.
3. The planner calculates the minimum starting amount of X, the net output of one round, and the required repetitions.
4. The order executes A and B in a proven safe sequence and repeats the complete cycle until the requested amount of Z is produced.

The planner calculates:

- **starting seed**: the minimum stock required before the first round;
- **in-cycle reuse**: intermediate products consumed again in the same or later rounds;
- **net consumption and production**: the final balance after one complete round;
- **cycle repetitions**: the number of complete rounds required for the final request;
- **stage order**: an order that does not run a stage before its required input exists.

### Confirmation Screen

When a plan contains cycles, the crafting confirmation screen marks each cycle with a distinct color. Hover a material to inspect its role, inventory usage, starting seed, net change, recipe execution count, and repetition count.

If one material belongs to several cycles, use the Previous Trinity Cycle and Next Trinity Cycle keys to switch the hovered details. The crafting-plan tree retains complete cycle relationships instead of displaying a cyclic dependency as an incorrect one-way chain.

### Limits

- Cyclic plans require an available **Trinity crafting CPU**. Ordinary AE2 CPUs do not execute compressed cycle plans.
- If the starting seed is missing, the confirmation screen reports the exact shortage instead of retrying indefinitely.
- A cycle with no productive net output, no route to the requested item, or excessive configured complexity is rejected.
- Execution dispatches only the amount that current inputs and machine capacity can accept.
- Remaining repetitions, stage progress, and wait states persist across server restarts.

---

## Ignoring NBT On Processing Outputs

The first item output of a processing pattern can use either of two matching modes:

- **Exact Data Components**: the runtime output must equal the complete item key declared by the pattern.
- **Same registered item**: the registered item ID must match, while Data Component differences are ignored.

Since Minecraft 1.20.5, most data historically stored in item NBT, including custom names, enchantments, durability, and container contents, is represented by Data Components. The "ignore Data Components" option therefore covers what is commonly called "ignore NBT."

### Configuration

1. Open an AE2 Pattern Encoding Terminal and select Processing Pattern mode.
2. Configure at least one item output.
3. Open the amount dialog for the first output.
4. Use the new left-toolbar button to switch between exact matching and same-item matching.
5. Encode or re-encode the pattern. The choice is stored on that encoded pattern.

The option applies only to the **first item output** of a processing pattern. Crafting patterns, fluid outputs, other generic AE keys, and patterns without an item output cannot use it.

### Runtime Behavior

If a pattern declares an ordinary iron sword but the machine returns the same registered sword with another name, enchantment, or durability value, a Trinity CPU may accept it as that declared output.

The returned stack is never rewritten:

- all actual names, enchantments, durability, and other components are preserved;
- component ignoring only decides which pending declaration may accept the runtime key;
- an intermediate same-item output may be reused only inside the crafting job that produced it;
- a final requested output remains isolated for delivery and cannot be consumed by later stages;
- ambiguous or conflicting dynamic-output ownership is rejected.

Requests containing same-item output matching require a Trinity crafting CPU. The confirmation screen reports this requirement instead of sending the plan to an ordinary AE2 CPU.

---

## Uploading Patterns After Encoding

Data Energistics adds a post-encoding upload workflow to the AE2 Pattern Encoding Terminal and the Universal Pattern Encoding Terminal. **Enable Data Energistics Upload** is enabled by default in terminal settings; when it is disabled, the encode button returns to AE2's native behavior and does not open the upload panel.

### Upload Workflow

1. Configure a recipe and encode the pattern in a pattern-encoding terminal.
2. After encoding, open the upload panel to view available pattern-provider groups on the current network.
3. Filter providers with the search box.
4. Left-click a provider group or a concrete provider leaf to write the encoded pattern into available provider slots.
5. Right-click, or use the open-provider action, to inspect the provider GUI, slot usage, name, and physical location.

The upload panel shows written-pattern count, total slots, block/part/external provider kind, and recognized recipe categories and workstations. Processing patterns prefer providers whose recipe category and workstation context exactly match the encoded pattern. After a group is selected, the server tries its currently available physical leaves in order until the pattern count is fully committed or no eligible target remains.

### Workstation And Machine-Mode Validation

- Encoded patterns retain recipe category, recipe ID, and workstation context.
- Before a processing pattern is uploaded, its recipe source is resolved and providers are ranked against the workstation context currently exposed by the network.
- The Data Integrated Charger is registered as a pattern-upload workstation. Charging, dust processing, enrichment, inscribing, and crystal-growth patterns require the machine to be in the corresponding mode.
- If the selected mode does not match, a pattern maps to multiple modes, its recipe ID is stale, or its recipe cannot be resolved, the upload is rejected and the encoder pattern is not consumed.
- Workstation validation happens before provider inventory mutation, and machine state plus pattern insertion are coordinated as one reversible transaction.

### Duplicate And Failure Protection

- If the selected provider group already contains an identical encoded pattern, no second copy is inserted. The encoded pattern is cleared and returned as blank patterns so provider slots are not duplicated.
- If the target is offline, full, or has no matching workstation, the encoded pattern remains in the encoder and the UI reports the reason.
- If the provider inventory changed but the committed delta cannot be proven, the runtime does not consume another encoded pattern; it keeps the current pattern and asks the player to inspect the target.
- After a successful upload, the client records the actual receiving provider, dimension, coordinates, and time for later identification.
- Ordinary AE2 providers, Data Energistics providers, and Trinity aggregate providers may participate through their declared upload targets; an undeclared compatibility relationship is never forced.

---

## Pattern Provider Batch Dispatch

When an ordinary AE2 Pattern Provider sends processing inputs to an external inventory, Data Energistics simulates target capacity and combines repeated logical crafts into one capacity-sized batch.

- The batch never exceeds the requested craft count.
- The batch never exceeds the target's simulated capacity.
- Items, fluids, and generic AE keys are scaled by exact amounts.
- If one input cannot be accepted completely, no partial logical craft is committed.
- Dedicated crafting machines and result-, pulse-, or lock-sensitive routes retain AE2's single-craft behavior.

This reduces repeated one-craft pushes and lets Trinity dispatch according to the actual capacity of the target machine and its input buffers.

---

## Round-Robin Target Distribution

When one Pattern Provider has several usable output sides, target selection retains AE2's round-robin cursor:

1. Start at the current cursor.
2. Skip offline, locked, blocked, or full targets.
3. Dispatch to the first target that can accept the complete batch.
4. Advance the cursor only after a successful dispatch.
5. Start the next batch after that target.

Several adjacent machines can therefore receive orders in rotation. A failed target does not consume the cursor or permanently prevent other sides from receiving work.

---

## Trinity Integration With AE2

The Trinity Data Core publishes three services through its Information Exchange Depot:

- high-capacity generic AE storage;
- one or more virtual crafting CPUs;
- an aggregate crafting-pattern provider backed by Pattern Processing Cores.

These services use AE2 network, channel, storage-priority, pattern-priority, and crafting-job APIs. If the structure becomes invalid, the depot goes offline, or its lease changes, the publications are withdrawn so one Trinity host cannot expose duplicate storage, CPU, or pattern services through several depots.

---

## Pattern Encoding And Recipe Sources

Data Energistics also extends AE2 pattern encoding:

- JEI/EMI transfers preserve recipe-source and workstation information.
- Multi-mode machines such as the Data Integrated Charger validate their selected mode during pattern upload.
- Trinity records stable recipe IDs for AE2 crafting, stonecutting, and smithing patterns.
- After a recipe reload, an unresolved or ambiguous pattern is not silently reinterpreted as another recipe.
- Aggregate pattern management supports browsing, searching, priority, and best-effort migration.

---

## Storage And Generic Keys

Data Flow, Digitalization, Manifest Binary, and other non-item and non-fluid resources participate in AE storage and crafting through generic AE keys.

These keys may be displayed or moved as Wrapped Generic Stack items, but their identity remains the original AEKey with its exact amount. Migration, export, and automation preserve the underlying key instead of treating the wrapper as an ordinary recipe item.

---

## Compatibility And Fallback

- Ordinary acyclic jobs may still use AE2's native planner and normal crafting CPUs.
- A request that Trinity cannot take over but AE2 can process falls back to AE2 and is marked as such on the confirmation screen.
- Trinity-only features, including cyclic execution and same-item dynamic outputs, never fall back to an incompatible ordinary AE2 CPU.
- Third-party providers and workstations gain extended behavior only when their compatibility adapter loads successfully; missing optional mods do not prevent AE2 or Data Energistics from starting.
