# AE2 Lightning Tech Lightning Capability Integration Design

## Background

DataEnergistics already contains several energy-bearing gameplay objects:

- AE2-powered items backed by `IAEItemPowerStorage`
- AE2-powered block entities backed by `AENetworkedPoweredBlockEntity`
- a tower-style FE aggregation and routing block entity backed by `IEnergyStorage`
- a mod-owned energy abstraction layer (`DataEnergyStorage` / `MutableDataEnergyStorage`) introduced only as an internal seam

AE2 Lightning Tech (`ae2lt`) is now an optional compatibility dependency and its internal access points are already isolated behind guarded runtime bridges. This makes it possible to discuss a future Lightning Energy capability bridge without committing to an implementation.

This document does **not** implement the bridge.

This document intentionally avoids defining default AE / FE / Lightning conversion ratios without gameplay approval.

This document recommends a conservative first implementation.

## Current State

At the time of writing:

- AE2LT is declared as an **optional** dependency in `neoforge.mods.toml`
- no runtime code registers `AE2LTCapabilities.LIGHTNING_ENERGY_ITEM`
- no runtime code registers `AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK`
- no runtime code implements `ILightningEnergyHandler`
- no runtime code imports `com.moakiee.ae2lt.api.lightning.*` for bridge behavior
- `Ae2LtCompat`, `Ae2LtRuntimeBridge`, `Ae2LtInternalNames`, and `Ae2LtMixinGuards` only support guarded existing AE2LT integration paths
- `DataEnergyStorage` and `MutableDataEnergyStorage` do **not** represent AE2LT Lightning Energy
- `AeItemPowerStorageAdapter` and `FeEnergyStorageAdapter` are wrappers around existing AE2 item power and FE storage semantics only

## Non-goals

- Implementing a Lightning capability bridge
- Registering AE2LT item or block capabilities
- Defining final AE2-to-Lightning or FE-to-Lightning conversion ratios
- Changing current item capacities, machine capacities, charge rates, NBT keys, or data component keys
- Changing current server tick logic, GUI behavior, packet flow, or recipe balance
- Making all existing powered items or powered block entities Lightning-capable by default

## Existing Energy Systems in DataEnergistics

### AE2 item energy

The repository currently uses AE2 item energy through `IAEItemPowerStorage` and `AEComponents.STORED_ENERGY`.

Confirmed item-side energy patterns:

- `PoweredEnergyItem` stores energy in `AEComponents.STORED_ENERGY`
- `PoweredItem` and its powered tool variants inherit `PoweredEnergyItem`
- `DataCaptureBallItem` implements `IAEItemPowerStorage` directly and stores energy in `AEComponents.STORED_ENERGY`
- `MatterConvergingCrossbowItem` implements `IAEItemPowerStorage` directly and stores energy in `AEComponents.STORED_ENERGY`
- `DataFlowPortableCellItem` uses inherited AE2 portable-cell energy behavior and returns an AE2 energy cell containing its stored AE power when disassembled

### AE2 network and machine energy

Confirmed AE2-powered block entities:

- `DataTeleportAnchorBlockEntity`
- `DataSolarPanelBlockEntity`
- `DataExtractorBlockEntity`
- `DataRipperReassemblerBlockEntity`
- `DataMimeticFieldBlockEntity`

These classes extend `AENetworkedPoweredBlockEntity` and use AE2 energy through calls such as:

- `getAECurrentPower()`
- `extractAEPower(...)`
- `injectExternalPower(...)`
- `grid.getEnergyService().extractAEPower(...)`
- `grid.getEnergyService().injectPower(...)`

`AdaptivePatternProviderLogic` also consumes AE2 network energy directly through `IEnergyService`.

### FE energy

Confirmed FE-facing object:

- `DataDistributionTowerBlockEntity`

`DataDistributionTowerBlockEntity`:

- exposes block FE capability via `Capabilities.EnergyStorage.BLOCK`
- returns a cached `TowerEnergyStorage`
- aggregates remote FE endpoints
- simulates and executes `receiveEnergy(...)` / `extractEnergy(...)`
- may also query AE2Flux integration during extraction accounting

### Internal abstraction

The new internal abstraction currently defines:

- `DataEnergyStorage`
- `MutableDataEnergyStorage`
- `AeItemPowerStorageAdapter`
- `FeEnergyStorageAdapter`

These wrappers are intentionally generic and do not define any Lightning semantics.

## AE2LT Lightning Energy Concepts

This document assumes that AE2LT Lightning Energy is a **separate energy concept** from:

- AE2 item power
- AE2 network energy
- FE
- DataEnergistics internal abstraction

Until gameplay review says otherwise, Lightning Energy should be treated as a distinct resource family with at least two relevant conceptual tiers:

- `HIGH_VOLTAGE`
- `EXTREME_HIGH_VOLTAGE`

Current recommendation: do **not** collapse these concepts into existing AE2 or FE storage automatically.

## Candidate Integration Targets

The table below lists real repository candidates that could be considered later. It is a design inventory, not an implementation plan.

| Class | Type | Current energy system | Phase 1 suitability | Recommended conclusion | Reason | Risk | Config gate |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PoweredEnergyItem` | item base / AE2 item power | AE2 item energy via `AEComponents.STORED_ENERGY` | Low | **Pending, whitelist only** | It is a broad base interface covering many tools; auto-bridging would affect many items at once | Large gameplay blast radius | Yes |
| `PoweredItem` | item base / AE2 item power | AE2 item energy | Low | **Do not auto-expose** | Base behavior is generic utility/tool energy, not Lightning-specific storage | Changes many derived items together | Yes |
| `PoweredAxeItem` / `PoweredHoeItem` / `PoweredPickaxeItem` / `PoweredShovelItem` / `PoweredSwordItem` / `PoweredCuttingKnifeItem` | item / AE2 item power | AE2 item energy | Low | **Do not include in first batch** | These are normal powered tools; auto-conversion risks turning utility tools into unintended Lightning batteries | Balance, repair, charging, UI expectations | Yes |
| `DataCaptureBallItem` | item / AE2 item power | AE2 item energy | Medium | **Pending, likely separate review** | It already has custom storage gameplay and depletion semantics | Capture cost and depletion loop could be destabilized | Yes |
| `MatterConvergingCrossbowItem` | item / AE2 item power | AE2 item energy | Low | **Do not include in first batch** | Weapon power directly affects combat throughput | High balance risk | Yes |
| `DataFlowPortableCellItem` | item / AE2 portable cell with power | AE2 item energy | Low | **Do not include in first batch** | It already decomposes into an AE2 energy cell; implicit Lightning bridging would blur storage identity | Interop confusion, item conversion edge cases | Yes |
| `DataTeleportAnchorBlockEntity` | block entity / AE2 powered BE | AE2 machine buffer + AE2 grid refill | Low | **Do not include in first batch** | Teleport cost is discrete and high-impact | Mobility and bypass risk | Yes |
| `DataSolarPanelBlockEntity` | block entity / AE2 powered BE | AE2 buffer + AE2 grid injection | Low | **Do not include in first batch** | Generator-like behavior makes it a likely exploit vector if Lightning conversion is wrong | Passive generation exploit risk | Yes |
| `DataExtractorBlockEntity` | block entity / AE2 powered BE | AE2 local buffer + AE2 grid draw | Low | **Do not include in first batch** | Continuous work loop uses simulate/extract semantics every tick | Hot-path correctness risk | Yes |
| `DataRipperReassemblerBlockEntity` | block entity / AE2 powered BE | AE2 local buffer + AE2 grid draw | Low | **Do not include in first batch** | Machine progression depends on energy availability | Crafting/balance regression risk | Yes |
| `DataMimeticFieldBlockEntity` | block entity / AE2 powered BE | AE2 local buffer + AE2 grid draw | Low | **Do not include in first batch** | Another machine with direct AE2 gameplay costs | Unknown balance coupling | Yes |
| `AdaptivePatternProviderLogic` | AE2 logic class | AE2 network energy only | Very low | **Do not expose capability** | It is logic, not a storage object | Wrong abstraction boundary | No |
| `DataDistributionTowerBlockEntity` | block entity / FE storage | FE capability aggregation | Medium | **Pending, prefer dedicated bridge instead** | It already centralizes FE exposure and simulation semantics | Could become universal FE/Lightning converter by accident | Yes |
| Future dedicated bridge item | dedicated item | none yet | High | **Preferred Phase 1 target** | Narrow scope and explicit semantics | Requires new content and design approval | Yes |
| Future dedicated bridge block | dedicated block | none yet | High | **Preferred Phase 1 target** | Keeps Lightning behavior off existing machines | Requires new content and persistence design | Yes |

## Recommended Phase 1 Policy

Recommended conservative policy:

1. Do **not** automatically make all existing AE2-powered items Lightning containers.
2. Do **not** automatically make all FE storages Lightning storages.
3. Do **not** automatically make all `AENetworkedPoweredBlockEntity` machines Lightning-capable.
4. Prefer a **whitelist** over blanket opt-in.
5. Prefer a **dedicated bridge block** or **dedicated bridge item** over retrofitting existing gameplay objects.
6. Keep all Lightning interoperability **disabled by default**.

This aligns with the current codebase shape:

- existing powered items use AE2 power semantics already
- existing machines use AE2 buffer/grid semantics already
- the FE tower is an aggregator and hot path, not a safe first bridge surface

## Energy Conversion Policy

### Decision 1: Should Lightning Energy interoperate with AE2 Energy?

Options considered:

- no direct interoperability
- Lightning -> AE2 only
- AE2 -> Lightning only
- bidirectional AE2 <-> Lightning
- interoperability only through a dedicated bridge machine
- interoperability only when enabled by config

**Recommended answer:** no direct interoperability in Phase 1. If interoperability is ever added, it should be:

- explicit
- server-side only
- gated by config
- preferably routed through a dedicated bridge object

**Alternative:** allow dedicated bridge machine only.

**Risk of direct interoperability:** existing AE2-powered tools and machines would gain a new energy economy without a balance pass.

### Decision 2: Should Lightning Energy interoperate with FE?

Options considered:

- no direct interoperability
- FE -> Lightning only
- Lightning -> FE only
- bidirectional FE <-> Lightning
- interoperability only through `DataDistributionTowerBlockEntity`
- interoperability only when enabled by config

**Recommended answer:** no direct interoperability in Phase 1.

**Alternative:** dedicated bridge block only, optionally reusing FE-facing internals later.

**Do not use `DataDistributionTowerBlockEntity` as the default first bridge target.**

Reason:

- it is already a cross-endpoint FE router
- it depends heavily on correct `simulate` semantics
- broadening it into FE/Lightning conversion would amplify compatibility and balance risk

### Decision 3: How should `HIGH_VOLTAGE` and `EXTREME_HIGH_VOLTAGE` be mapped?

Options considered:

- two independent storages
- HV only
- EHV only
- EHV can down-convert to HV
- HV can up-convert to EHV
- map both into a future `DataEnergyTier`
- avoid mapping them onto current `DataEnergyStorage`

**Recommended answer:** treat HV and EHV as distinct conceptual tiers and do not map them onto current `DataEnergyStorage`.

Recommended sub-policy:

- no implicit HV -> EHV upgrade
- no implicit EHV -> HV downgrade
- any cross-tier conversion must be explicit, separately configured, and machine-mediated

### Decision 4: How should conversion ratios be defined?

Options considered:

- define no ratios yet
- fixed defaults
- config-defined defaults
- per-tier ratios
- per-object ratios
- optional loss percentage
- minimum unit rounding

**Recommended answer:** do not define default production ratios in code or in policy yet.

Instead, Phase 1 design should expose these as **open configuration proposals** only.

This document therefore recommends:

- no default AE <-> Lightning ratio
- no default FE <-> Lightning ratio
- no assumed loss model
- no assumed minimum unit

until gameplay approval exists.

## Capability Exposure Policy

### Items

Existing AE2 powered items should **not automatically become Lightning containers unless explicitly whitelisted**.

Recommended item policy:

- default deny
- explicit whitelist only
- start with either:
  - no existing items, or
  - a dedicated bridge item

For later phases, candidate exposure modes per item should be:

- read-only
- insert-only
- extract-only
- bidirectional

but only after gameplay review.

### Blocks

Existing FE storage should **not automatically become Lightning storage unless explicitly configured**.

Existing AE2 powered block entities should also **not automatically expose Lightning**.

Recommended block policy:

- Phase 1 prototype should prefer a dedicated bridge block
- `DataDistributionTowerBlockEntity` should stay FE-focused unless explicitly approved
- `AENetworkedPoweredBlockEntity` machines should not receive blanket Lightning capability support

### Sides and capability scope

If block capability is ever added:

- side-aware exposure should be supported where meaningful
- server must remain authoritative
- side-less global capability should be avoided unless the backing object is genuinely omni-directional

## Persistence and Synchronization

### Simulate semantics

If a Lightning bridge is implemented later, `simulate = true` must:

- not modify `AEComponents.STORED_ENERGY`
- not modify block entity NBT
- not modify any dedicated Lightning storage
- not call `setChanged()`
- not call `saveChanges()`
- not call `markForClientUpdate()`
- not send packets
- not consume AE2 energy
- not consume FE energy
- not create overflow or side effects

### Persistence

Recommended answer:

- do **not** store Lightning Energy inside existing AE2 `STORED_ENERGY` unless Lightning is explicitly declared to be the same resource
- do **not** silently reuse existing FE values as Lightning storage
- if Lightning storage is later approved, use a dedicated storage object or dedicated persistence field

Possible persistence choices for a later implementation:

1. dedicated `DataComponent` for item Lightning storage
2. dedicated NBT fields for block Lightning storage
3. dedicated per-object bridge storage implementation
4. explicit adapter that proxies another resource only when conversion is enabled

Current recommendation:

- dedicated storage is safer than overloading existing AE2 or FE state

## Server / Client Responsibility

Recommended policy:

- server computes all authoritative insert/extract results
- server owns persistence
- server owns conversion
- client only renders and displays synchronized values
- client never performs authoritative conversion math

If UI support is added later:

- display must clearly distinguish AE2 power, FE, and Lightning
- mixed-resource displays should only appear when explicitly enabled

## Configuration Proposal

The following config keys are proposed for a later implementation. They are design placeholders only.

```text
enableAe2LtLightningCompat
enableLightningToAe2Conversion
enableAe2ToLightningConversion
enableLightningToFeConversion
enableFeToLightningConversion
hvCapacity
ehvCapacity
hvToAeRatio
ehvToAeRatio
hvToFeRatio
ehvToFeRatio
conversionLossPercent
allowedLightningItems
allowedLightningBlocks
```

Recommended defaults:

- `enableAe2LtLightningCompat = false`
- all conversion toggles = `false`
- all item/block allow-lists empty until explicitly configured
- no default ratio committed until approved

## Failure and Fallback Behavior

If any Lightning bridge is added later, failure behavior must be narrow:

- AE2LT missing -> bridge disabled, mod still starts
- AE2LT version too old -> bridge disabled, mod still starts
- AE2LT public API missing -> bridge disabled, mod still starts
- AE2LT internal classes changed -> bridge disabled, mod still starts
- capability registration failure -> bridge disabled, mod still starts
- client-only class missing -> disable client bridge/UI path only
- dedicated server missing client UI code -> server still starts

The fallback rule should be:

> Disable the AE2LT Lightning bridge only. Never take down core DataEnergistics features.

## Testing Plan

### Build validation

1. `.\gradlew.bat clean build`
2. `.\gradlew.bat runData`
3. `.\gradlew.bat runClient`
4. `.\gradlew.bat runServer`

### Without AE2LT installed

1. client startup succeeds
2. dedicated server startup succeeds
3. no AE2LT Lightning bridge registration occurs
4. no `ClassNotFoundException` or linkage errors from Lightning bridge code

### With AE2LT installed

1. client startup succeeds
2. dedicated server startup succeeds
3. bridge config disabled -> no-op behavior
4. bridge config enabled -> capability registration occurs
5. item insert / extract works
6. block insert / extract works
7. `simulate = true` is side-effect free
8. save / load works
9. network synchronization works
10. GUI display is correct
11. multi-dimension behavior works
12. chunk unload / reload works
13. reconnect behavior works
14. config toggles behave correctly
15. AE2 network disconnect behavior is safe
16. FE capability missing behavior is safe

### Compatibility failure tests

1. AE2LT absent
2. AE2LT below minimum supported version
3. AE2LT public API class missing
4. AE2LT internal class changed
5. capability registration failure
6. client and server config mismatch
7. server without any client UI support

## Open Questions

1. Should Lightning ever interoperate directly with AE2 item energy?
2. Should Lightning ever interoperate directly with AE2 network energy?
3. Should Lightning ever interoperate directly with FE?
4. Is a dedicated bridge block required before touching any existing item or machine?
5. Should any existing item be whitelisted in the first usable release?
6. Should any existing machine be whitelisted in the first usable release?
7. What are the actual HV and EHV capacities?
8. Are HV and EHV convertible at all?
9. What are the conversion ratios, if any?
10. Is there conversion loss?
11. What is the minimum transfer unit and rounding rule?
12. Should UI display Lightning separately from AE2/FE at all times?
13. Should dedicated server configs fully disable client display code paths?

## Implementation Phases

### Phase A: design confirmation

- answer the open questions
- approve or reject AE2 interoperability
- approve or reject FE interoperability
- approve or reject a dedicated bridge block
- approve whitelist strategy

### Phase B: prototype

- implement a guarded prototype behind config
- prefer dedicated bridge content over retrofitting existing content
- support strict `simulate` semantics
- add no-op fallback when AE2LT is absent or incompatible

### Phase C: gameplay review

- validate progression and balance
- confirm no unintended energy arbitrage loops
- confirm no exploit path through existing AE2 tools, machines, or FE routing blocks

### Phase D: broaden scope only if approved

- consider whitelisting carefully selected existing items or blocks
- only after balance and persistence behavior are accepted
