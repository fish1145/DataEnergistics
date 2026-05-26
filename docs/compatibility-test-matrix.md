# Compatibility Test Matrix

## Purpose

This document records the current compatibility validation state of DataEnergistics and the guard strategy used for optional integrations.

It is intentionally conservative:

- only real repository integrations are listed
- only actually executed validation runs are marked as verified
- absent/present optional dependency combinations remain `Not verified` unless they were run directly
- AE2LT Lightning capability remains deferred and is not part of this matrix

## Baseline Environment

- JDK: **21.0.11**
- Gradle Wrapper: **8.14.5**
- Minecraft: **1.21.1**
- NeoForge: **21.1.228**
- ModDevGradle: **2.0.141**
- Operating system used for the recorded runs: **Windows 11**

## Required Dependencies

| Mod ID | Status |
| --- | --- |
| `neoforge` | Required |
| `minecraft` | Required |
| `ae2` | Required |

## Optional Dependencies

The table below tracks real optional or compatibility-gated integrations present in the current source tree.

| Mod ID | In `CompatIds` | Optional in `mods.toml` | Build scope | Load guard | Mixin guard | Reflection bridge | Not installed startup | Installed startup | Risk | Notes / next step |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ae2lt` | Yes | Yes | `compileOnly` + `runtimeOnly` | `Ae2LtCompat.isLoaded()`, `OptionalMods.isLoaded()` | `Ae2LtMixinGuards.isPresent()` plus AE2LT sentinel resource in `DataEnergisticsMixinPlugin` | `Ae2LtRuntimeBridge`, `Ae2LtWirelessBridge` | **Client startup flow entered** and **dedicated server passed** with AE2LT runtime jar removed from the dev classpath | **Client startup flow entered** and **dedicated server passed** in baseline dev runtime | **High** | Optional absence was validated by filtered dev-launch classpath replay; no AE2LT CNFE or mixin apply failure was observed. |
| `ae2wtlib` | Yes | Yes | `compileOnly` + `runtimeOnly` (+ extracted API jar as `compileOnly`) | `Ae2WtLibCompat` load check via `OptionalMods.isLoaded()` | No dedicated mixin guard | `Ae2WtLibCompat` screen replacement reflection | **Client startup flow entered** and **dedicated server passed** with the AE2WTLib runtime jar filtered out | **Client startup flow entered** and **dedicated server passed** in baseline dev runtime | Medium | DataEnergistics has no direct `de.mari_023.ae2wtlib.api.*` imports. The only strong AE2WTLib type references in main sources are the client-only `WETMenu` / `WETScreen` base types used by `WirelessPatternEncodingTermScreen`, while `Ae2WtLibCompat` itself stays reflective and guarded. |
| `ae2cs` | Yes | Yes | `compileOnly` + `runtimeOnly` | Class-presence and optional-mod usage in guarded code paths | Yes, `DataEnergisticsMixinPlugin` checks AE2CS sentinel class for `Ae2Cs*` mixins | No dedicated bridge class | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Needs dedicated absent/present startup runs; currently only baseline client presence is observed. |
| `extendedae_plus` | Yes | Yes | `compileOnly` + `runtimeOnly` | `ExtendedAePlusCompat.isLoaded()` via `OptionalMods.isLoaded()` | No dedicated mixin prefix in plugin | No dedicated bridge class | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Present in baseline client run; isolated absent/present coverage still missing. |
| `mekanism` | Yes | Yes | `implementation` | `OptionalMods.areLoaded(mekanism, appmek)` in `AppMekCompat` | No dedicated mixin prefix in plugin | `AppMekCompat` reflects Mekanism capability holder and handler construction | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Should be validated together with and without `appmek`; dedicated server run is still unresolved. |
| `appmek` | Yes | Yes | `implementation` | `OptionalMods.areLoaded(mekanism, appmek)` in `AppMekCompat` | No dedicated mixin prefix in plugin | `AppMekCompat` | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Needs dedicated pairwise validation with `mekanism`. |
| `advanced_ae` | Yes | No | `implementation` | Sentinel class checks in `DataEnergisticsMixinPlugin` | Yes, `AdvancedAe*` mixins only apply when sentinel class exists | Reflection is used in other compat paths such as adapter registration, but no dedicated bridge class | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Build/runtime dependency is present, but `mods.toml` does not currently declare it as optional metadata. |
| `appflux` | Yes | No | `implementation` | `AE2FluxIntegration.isAvailable()` via `OptionalMods.isLoaded()` | No dedicated mixin prefix in plugin | `AE2FluxIntegration` reflects AppFlux key classes | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Needs dedicated absent/present runs and server follow-up; current validation is only baseline client presence. |
| `appliedcreate` | Yes | No | `implementation` | `AppliedCreateCompat` checks `create` + `appliedcreate` via `OptionalMods.isLoaded()` | Yes, `AppliedCreate*` mixins gated by class sentinel in `DataEnergisticsMixinPlugin` | Reflection/class lookup exists in `AdaptivePatternProviderLogic` for mechanical crafter support | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Should be tested with and without both `create` and `appliedcreate`. |
| `create` | Yes | No | `implementation` | `AppliedCreateCompat.isMechanicalProviderSupportEnabled()` | Indirectly, via `AppliedCreate*` mixin sentinel checks | Reflection/class lookup exists in `AdaptivePatternProviderLogic` | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Present in baseline client run; dedicated server currently fails before startup completion. |
| `extendedae` | Yes | No | `implementation` (via `ex-pattern-provider`) | Class-presence checks in plugin and runtime lookup in compat paths | Yes, `ExtendedAe*` mixins and `ExtendedInscriberThreadMixin` gated by sentinel class in plugin | `ExtendedAeRenamerCompat` uses registry lookup, not an internal reflection bridge | Not verified | **Client startup flow entered** in baseline dev runtime | Medium | Needs isolated absent/present coverage; metadata is not currently declared in `mods.toml`. |
| `neoecoae` | Yes | No | `implementation` | `OptionalMods.isLoaded(CompatIds.NEOECOAE)` | Yes, `NeoECOAEClientMixin` is guarded by mod-loaded check in plugin | No dedicated bridge class | Not verified | **Client startup flow entered** in baseline dev runtime | Low | Current use appears limited to client-side guarded mixin behavior; still needs isolated runs. |

## Build Validation

| Check | Status | Notes |
| --- | --- | --- |
| `.\gradlew.bat clean build` | **Passed** | Executed on JDK 21 during this phase. |
| `.\gradlew.bat runData` | **Passed** | Executed on JDK 21 during this phase. |
| `.\gradlew.bat runClient` | **Startup flow entered** | Real client startup and mod loading logs were observed; this is not a full interactive gameplay test. |
| `.\gradlew.bat runServer` | **Passed** | Verified on **2026-05-25** through dedicated server startup completion in `run\\logs\\debug.log`. |

## Latest Validation Run

- Date: **2026-05-25**
- JDK: **21.0.11**
- Gradle Wrapper: **8.14.5**
- Baseline `clean build`: **Passed**
- Baseline `runData`: **Passed**
- Baseline `runClient`: **Startup flow entered**
- Baseline `runServer`: **Passed**
- Without `ae2lt`: **Client startup flow entered**, **dedicated server passed**
- With `ae2lt`: **Client startup flow entered**, **dedicated server passed** in baseline dev runtime
- With `ae2wtlib`: **Startup-only verified** in baseline dev runtime; UI interaction was **not** verified
- Without `ae2wtlib` runtime jar but `ae2wtlib_api` still present: **Client startup flow entered**, **dedicated server passed**
- Strict without `ae2wtlib` runtime and `ae2wtlib_api`: **Not achieved** in filtered replay; `ae2wtlib_api` was still discovered from jar-in-jar dependencies
- Without `ae2lt` and `ae2wtlib`: **Client startup flow entered**, **dedicated server passed**

### Current dedicated server observation

Dedicated server startup is now verified on **2026-05-25**.

Observed completion markers:

- `Starting minecraft server version 1.21.1`
- `Preparing level "world"`
- `Done (4.117s)! For help, type "help"`

The earlier dedicated server crash during mixin preprocessing was resolved before this validation run.

## Runtime Validation Matrix

| Scenario | Client startup | Dedicated server startup | Data generation | Notes |
| --- | --- | --- | --- | --- |
| Baseline with current dev runtime dependencies | **Startup flow entered** | **Passed** | **Passed** | Baseline dev runtime includes the currently declared implementation/runtime dependencies. |
| Without `ae2lt` | **Startup flow entered** | **Passed** | Not verified | Executed by replaying the real dev launch with AE2LT removed from the runtime and legacy classpaths; no AE2LT CNFE or mixin apply error was observed. |
| With `ae2lt` | **Startup flow entered** in baseline dev runtime | **Passed** in baseline dev runtime | **Passed** in baseline dev runtime | Current validation is baseline-present rather than an AE2LT-only isolated environment. |
| With `ae2wtlib` | **Startup-only verified** in baseline dev runtime | **Passed** in baseline dev runtime | **Passed** in baseline dev runtime | Baseline startup was revalidated on 2026-05-26. No AE2WTLib-specific CNFE or mixin apply error was observed, but the wireless terminal UI path itself was not interacted with. |
| Without `ae2wtlib` runtime jar but `ae2wtlib_api` still present | **Startup flow entered** | **Passed** | Not verified | Executed by replaying the real dev launch with `applied-energistics-2-wireless-terminals-*.jar` removed from the runtime and legacy classpaths. No DataEnergistics-owned AE2WTLib runtime classloading crash was observed. |
| Strict without `ae2wtlib` runtime and `ae2wtlib_api` | **Not verified** | **Not verified** | Not verified | A strict absent environment was not actually achieved. Even after filtering direct runtime and legacy classpath entries, `ae2wtlib_api` was still discovered through jar-in-jar dependencies, including `ex-pattern-provider-892005-8025439.jar` (`ae2wtlib_api-19.2.0`) and `advancedae-1084104-7849217.jar` (`ae2wtlib_api-19.2.5`). The filtered replay still started, but that does **not** prove true API absence support. |
| Without `ae2lt` and `ae2wtlib` | **Startup flow entered** | **Passed** | Not verified | Executed by replaying the real dev launch with both runtime jars removed. `ae2wtlib_api` still remained on the classpath from the extracted compile-time artifact. |
| Dedicated server startup | N/A | **Passed** | N/A | Verified on 2026-05-25 with `Done (4.117s)! For help, type "help"` in `run\\logs\\debug.log`. |
| Client startup | **Startup flow entered** | N/A | N/A | Not a gameplay interaction pass. |
| Data generation | N/A | N/A | **Passed** | `runData` completed successfully in this phase. |

## AE2WTLib Audit Notes

- `Ae2WtLibCompat` is guarded by `OptionalMods.isLoaded(CompatIds.AE2WTLIB)` and uses reflective lookup for:
  - `de.mari_023.ae2wtlib.wet.WETScreen`
  - `de.mari_023.ae2wtlib.wet.WETMenu`
  - `com.fish_dan_.data_energistics.client.screen.WirelessPatternEncodingTermScreen`
- The only direct AE2WTLib type imports in main repository sources are client-only runtime classes in `WirelessPatternEncodingTermScreen`:
  - `de.mari_023.ae2wtlib.wet.WETMenu`
  - `de.mari_023.ae2wtlib.wet.WETScreen`
- No main-repository source file directly imports `de.mari_023.ae2wtlib.api.*`.
- The screen replacement hook is wired from `Data_Energistics.ClientEvents` screen events and stayed dedicated-server-safe in all executed runs.
- The style document used by `Ae2WtLibCompat` (`/screens/wtlib/wireless_pattern_encoding_terminal.json`) exists in the AE2WTLib runtime jar, so the remaining gap is UI interaction coverage rather than a missing resource in this repository.

## Mixin Guard Matrix

| Surface | Guard type | Current state |
| --- | --- | --- |
| AE2LT mixins (`Ae2lt*`) | `Ae2LtMixinGuards.isPresent()` + AE2LT sentinel resource + plugin prefix check | Guard exists |
| Advanced AE mixins (`AdvancedAe*`) | `DataEnergisticsMixinPlugin` class-resource sentinel check | Guard exists |
| AE2 Crystal Science mixins (`Ae2Cs*`) | `DataEnergisticsMixinPlugin` class-resource sentinel check | Guard exists |
| Applied Create mixins (`AppliedCreate*`) | `DataEnergisticsMixinPlugin` class-resource sentinel check | Guard exists |
| Extended AE mixins (`ExtendedAe*`, `ExtendedInscriberThreadMixin`) | `DataEnergisticsMixinPlugin` class-resource sentinel check | Guard exists |
| NeoECOAE client mixin | `OptionalMods.isLoaded(CompatIds.NEOECOAE)` in plugin | Guard exists |
| JEI transfer mixin | plugin checks for JEI transfer handler class presence | Guard exists |
| EMI transfer mixin | plugin checks for EMI API and AE2 EMI handler class presence | Guard exists |

## Reflection Bridge Matrix

| Surface | Guard | Failure behavior | Current state |
| --- | --- | --- | --- |
| `Ae2LtRuntimeBridge` | AE2LT loaded + deferred initialization + resolved class names | Logs once and disables the AE2LT internal bridge path | Present |
| `Ae2LtWirelessBridge` | AE2LT loaded + lazy reflective initialization | Returns null/false/empty results when unavailable | Present |
| `Ae2WtLibCompat` | AE2WTLib loaded | Returns `null` and leaves the original screen in place on reflection failure | Present |
| `AppMekCompat` | `mekanism` and `appmek` both loaded | Returns `null` or skips capability registration on reflection failure | Present |
| `AE2FluxIntegration` | `appflux` loaded | Returns zero / unavailable on reflection failure | Present |
| Applied Create mechanical crafter support in `AdaptivePatternProviderLogic` | Runtime `Class.forName(...)` and compat checks | Returns fallback values or skips special handling when classes are missing | Present |

## Known Gaps

- Baseline client startup is not the same thing as per-mod isolated feature interaction validation.
- AE2WTLib UI interaction still requires manual verification.
- Current optional-runtime evidence only proves startup safety when the AE2WTLib runtime jar is absent; it does **not** prove a fully AE2WTLib-free environment.
- Strict absence of `ae2wtlib_api` was not achieved in this phase because jar-in-jar dependency discovery still surfaced `ae2wtlib_api` from other runtime mods.
- Several compatibility surfaces are guarded in code but are **not** declared as optional dependencies in `mods.toml` (`advanced_ae`, `appflux`, `appliedcreate`, `create`, `extendedae`, `neoecoae`).
- AE2LT Lightning capability remains deferred.
- No Lightning Energy capability is registered.

## Future Test Runs

Recommended next runs:

1. Exercise the AE2WTLib wireless pattern terminal screen in-game, because startup coverage alone does not prove the reflected replacement path.
2. Decide whether AE2WTLib API should become fully runtime-optional; if yes, isolate or exclude the jar-in-jar `ae2wtlib_api` providers from `ex-pattern-provider` / `advancedae` test compositions.
3. Add isolated absent/present checks for `ae2cs`, `advanced_ae`, `extendedae_plus`, `mekanism` + `appmek`, `appflux`, `appliedcreate` + `create`, `extendedae`, and `neoecoae`.
4. Keep AE2LT Lightning capability deferred unless project requirements change.
