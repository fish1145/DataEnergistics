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

This document now records the project author's decision to defer AE2LT Lightning capability support entirely until new gameplay requirements exist.

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

## Author Decisions

The project author has explicitly rejected the currently proposed AE2LT Lightning capability path.

Recorded decisions:

1. Lightning Energy must **not** interoperate with AE2 Energy.
2. Lightning Energy must **not** interoperate with FE.
3. No dedicated Lightning bridge block will be added.
4. Existing AE2 powered items will **not** be automatically or manually whitelisted as Lightning containers.
5. The feature will **not** be enabled by default.
6. HV capacity is **not** defined.
7. EHV capacity is **not** defined.
8. HV-to-AE and HV-to-FE conversion ratios are **not** defined.
9. EHV-to-AE and EHV-to-FE conversion ratios are **not** defined.
10. Conversion loss is **not** defined.
11. Lightning Energy persistence is **not** defined.
12. No UI display is required.

Given these decisions, the current design conclusion is to **defer AE2LT Lightning capability implementation**.

This means:

- no Lightning capability bridge is planned for immediate implementation
- no Lightning Energy storage model should be introduced
- no existing item or block should expose Lightning capability
- no bridge block should be designed or implemented
- no conversion configuration should be added
- no UI work should be started
- future work can only resume after new gameplay requirements are approved

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

The table below lists real repository candidates that were surveyed during design analysis. It is **survey only**, not an implementation plan. Under the current author decisions, every row below resolves to **Deferred / not selected** or **Do not integrate**.

| Class | Type | Current energy system | Phase 1 suitability | Recommended conclusion | Reason | Risk | Config gate |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PoweredEnergyItem` | item base / AE2 item power | AE2 item energy via `AEComponents.STORED_ENERGY` | Low | **Deferred / not selected** | It is a broad base interface covering many tools; auto-bridging would affect many items at once | Large gameplay blast radius | N/A |
| `PoweredItem` | item base / AE2 item power | AE2 item energy | Low | **Do not integrate** | Base behavior is generic utility/tool energy, not Lightning-specific storage | Changes many derived items together | N/A |
| `PoweredAxeItem` / `PoweredHoeItem` / `PoweredPickaxeItem` / `PoweredShovelItem` / `PoweredSwordItem` / `PoweredCuttingKnifeItem` | item / AE2 item power | AE2 item energy | Low | **Do not integrate** | These are normal powered tools; auto-conversion risks turning utility tools into unintended Lightning batteries | Balance, repair, charging, UI expectations | N/A |
| `DataCaptureBallItem` | item / AE2 item power | AE2 item energy | Medium | **Do not integrate** | It already has custom storage gameplay and depletion semantics | Capture cost and depletion loop could be destabilized | N/A |
| `MatterConvergingCrossbowItem` | item / AE2 item power | AE2 item energy | Low | **Do not integrate** | Weapon power directly affects combat throughput | High balance risk | N/A |
| `DataFlowPortableCellItem` | item / AE2 portable cell with power | AE2 item energy | Low | **Do not integrate** | It already decomposes into an AE2 energy cell; implicit Lightning bridging would blur storage identity | Interop confusion, item conversion edge cases | N/A |
| `DataTeleportAnchorBlockEntity` | block entity / AE2 powered BE | AE2 machine buffer + AE2 grid refill | Low | **Do not integrate** | Teleport cost is discrete and high-impact | Mobility and bypass risk | N/A |
| `DataSolarPanelBlockEntity` | block entity / AE2 powered BE | AE2 buffer + AE2 grid injection | Low | **Do not integrate** | Generator-like behavior makes it a likely exploit vector if Lightning conversion is wrong | Passive generation exploit risk | N/A |
| `DataExtractorBlockEntity` | block entity / AE2 powered BE | AE2 local buffer + AE2 grid draw | Low | **Do not integrate** | Continuous work loop uses simulate/extract semantics every tick | Hot-path correctness risk | N/A |
| `DataRipperReassemblerBlockEntity` | block entity / AE2 powered BE | AE2 local buffer + AE2 grid draw | Low | **Do not integrate** | Machine progression depends on energy availability | Crafting/balance regression risk | N/A |
| `DataMimeticFieldBlockEntity` | block entity / AE2 powered BE | AE2 local buffer + AE2 grid draw | Low | **Do not integrate** | Another machine with direct AE2 gameplay costs | Unknown balance coupling | N/A |
| `AdaptivePatternProviderLogic` | AE2 logic class | AE2 network energy only | Very low | **Do not integrate** | It is logic, not a storage object | Wrong abstraction boundary | N/A |
| `DataDistributionTowerBlockEntity` | block entity / FE storage | FE capability aggregation | Medium | **Do not integrate** | It already centralizes FE exposure and simulation semantics | Could become universal FE/Lightning converter by accident | N/A |
| Future dedicated bridge item | dedicated item | none yet | High | **Deferred / rejected for now** | Narrow scope and explicit semantics were considered, but no gameplay requirement remains | Requires new content and design approval | N/A |
| Future dedicated bridge block | dedicated block | none yet | High | **Deferred / rejected for now** | Keeps Lightning behavior off existing machines, but the author rejected adding one | Requires new content and persistence design | N/A |

## Recommended Phase 1 Policy

Recommended policy under the current author decisions:

1. Do **not** implement a Lightning capability bridge at this time.
2. Keep AE2LT as an **optional** compatibility dependency only.
3. Keep the existing Adaptive Pattern Provider-related AE2LT compatibility only.
4. Do **not** expose Lightning Energy on existing items.
5. Do **not** expose Lightning Energy on existing blocks.
6. Do **not** add a dedicated bridge block.
7. Do **not** define conversion, storage, or UI behavior.
8. Treat AE2LT Lightning capability support as **deferred until new gameplay requirements are approved**.

Previously considered bridge-item, bridge-block, whitelist, and conversion options are retained in this document as rejected or deferred alternatives for historical reference only.

## Energy Conversion Policy

Current policy:

- No AE2 Energy <-> Lightning Energy conversion.
- No FE <-> Lightning Energy conversion.
- No HV/EHV conversion ratio.
- No conversion loss.
- No tier conversion between HV and EHV.
- No implicit conversion through existing `DataEnergyStorage`.
- Any future conversion requires a new design decision with new gameplay requirements.

Deferred / rejected options:

- `enableLightningToAe2Conversion`
- `enableAe2ToLightningConversion`
- `enableLightningToFeConversion`
- `enableFeToLightningConversion`
- `hvToAeRatio`
- `ehvToAeRatio`
- `hvToFeRatio`
- `ehvToFeRatio`
- `conversionLossPercent`

These are **not applicable under the current author decisions** and must not be implemented unless a new approved design replaces this document state.

## Capability Exposure Policy

Current policy:

- Do **not** register `LIGHTNING_ENERGY_ITEM`.
- Do **not** register `LIGHTNING_ENERGY_BLOCK`.
- Do **not** expose capability on `PoweredEnergyItem`.
- Do **not** expose capability on `DataCaptureBallItem`.
- Do **not** expose capability on `MatterConvergingCrossbowItem`.
- Do **not** expose capability on `DataFlowPortableCellItem`.
- Do **not** expose capability on `DataDistributionTowerBlockEntity`.
- Do **not** expose capability on `AENetworkedPoweredBlockEntity` machines.
- Do **not** add a dedicated Lightning bridge block.
- Do **not** use whitelist-based exposure at this time.

The candidate table above remains useful as a repository survey only. It does **not** imply that any item or block is approved for future capability exposure.

## Persistence and Synchronization

Current policy:

- No Lightning Energy state will be persisted.
- No new NBT key will be added.
- No new `DataComponent` will be added.
- No existing AE2 stored energy component will be reused for Lightning Energy.
- No FE storage will be reused for Lightning Energy.
- No network synchronization will be added.
- No UI display is required.

If the feature is ever reopened, persistence and synchronization must be redesigned from scratch under a new approved requirement set.

## Server / Client Responsibility

Current policy:

- No new server-side Lightning authority path is required because no bridge is being implemented.
- No new client display path is required because no UI is being added.
- Existing AE2LT optional compatibility remains limited to the already shipped guarded compatibility paths.

## Configuration Proposal

Under the current author decisions, no runtime configuration is required because the capability bridge is not implemented.

Potential configuration keys previously considered are deferred and must **not** be implemented without a new approved design.

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

Current validation scope:

1. `.\gradlew.bat clean build`
2. `.\gradlew.bat runData`
3. `.\gradlew.bat runClient`

Documentation and no-op verification:

1. confirm no new capability registration was added
2. confirm no `ILightningEnergyHandler` implementation was added
3. confirm no Lightning Energy storage was added
4. confirm no new NBT or `DataComponent` fields were added
5. confirm AE2LT remains an optional dependency
6. confirm the existing Adaptive Pattern Provider compatibility remains unaffected

If Lightning capability work is ever reopened, a separate capability test plan must be written before implementation starts.

## Open Questions

There are no active open implementation questions at this time because the feature has been deferred.

Future reconsideration can only begin if new gameplay requirements explicitly define:

1. whether Lightning should interact with AE2 at all
2. whether Lightning should interact with FE at all
3. whether any dedicated item or block should exist
4. the storage model
5. HV and EHV capacities
6. conversion ratios
7. whether conversion loss exists
8. whether persistence exists
9. whether any UI is required

## Implementation Phases

Current status:

- Phase 0: build and compatibility foundation completed.
- Phase 1: AE2LT Lightning capability design documented.
- Phase 2: implementation deferred by author decision.

No implementation phase is active.

Future implementation can only start after new approved requirements define:

- whether Lightning should interact with AE2 Energy
- whether Lightning should interact with FE
- whether any dedicated item or block should exist
- the storage model
- HV/EHV capacity
- conversion ratios
- UI requirements

Until then, maintain the current optional AE2LT compatibility and do not implement Lightning capability support.
