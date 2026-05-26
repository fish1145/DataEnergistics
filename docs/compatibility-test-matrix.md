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
| `ae2wtlib` | Yes | Yes | `compileOnly` + `runtimeOnly` (+ extracted API jar as `compileOnly`) | `Ae2WtLibCompat` load check via `OptionalMods.isLoaded()` | No dedicated mixin guard | `Ae2WtLibCompat` screen replacement reflection | **Client startup flow entered** and **dedicated server passed** with AE2WTLib runtime jar removed from the dev classpath | **Client startup flow entered** and **dedicated server passed** in baseline dev runtime | Medium | Optional absence was validated by filtered dev-launch classpath replay. The extracted `ae2wtlib_api` compile-time jar still appears on the dev classpath, so this is runtime-mod absence coverage, not full API absence coverage. |
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
- Without `ae2wtlib`: **Client startup flow entered**, **dedicated server passed**
- With `ae2wtlib`: **Client startup flow entered**, **dedicated server passed** in baseline dev runtime
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
| Without `ae2wtlib` | **Startup flow entered** | **Passed** | Not verified | Executed by replaying the real dev launch with the AE2WTLib runtime jar removed from the runtime and legacy classpaths. The extracted `ae2wtlib_api` jar still remained available on the classpath. |
| With `ae2wtlib` | **Startup flow entered** in baseline dev runtime | **Passed** in baseline dev runtime | **Passed** in baseline dev runtime | Current validation is baseline-present rather than an AE2WTLib-only isolated environment. |
| Without `ae2lt` and `ae2wtlib` | **Startup flow entered** | **Passed** | Not verified | Executed by replaying the real dev launch with both runtime jars removed. `ae2wtlib_api` still remained on the classpath from the extracted compile-time artifact. |
| Dedicated server startup | N/A | **Passed** | N/A | Verified on 2026-05-25 with `Done (4.117s)! For help, type "help"` in `run\\logs\\debug.log`. |
| Client startup | **Startup flow entered** | N/A | N/A | Not a gameplay interaction pass. |
| Data generation | N/A | N/A | **Passed** | `runData` completed successfully in this phase. |

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
- `ae2wtlib` absence coverage currently removes the runtime mod jar, but the extracted `ae2wtlib_api` compile-time artifact still remains on the dev classpath.
- Several compatibility surfaces are guarded in code but are **not** declared as optional dependencies in `mods.toml` (`advanced_ae`, `appflux`, `appliedcreate`, `create`, `extendedae`, `neoecoae`).
- AE2LT Lightning capability remains deferred.
- No Lightning Energy capability is registered.

## Future Test Runs

Recommended next runs:

1. Exercise the AE2WTLib screen replacement path in-game, because startup coverage alone does not prove the reflected UI path.
2. Add isolated absent/present checks for `ae2cs`, `advanced_ae`, `extendedae_plus`, `mekanism` + `appmek`, `appflux`, `appliedcreate` + `create`, `extendedae`, and `neoecoae`.
3. If needed, add a stricter AE2WTLib-absent environment that also removes the extracted `ae2wtlib_api` artifact from the replayed classpath.
4. Keep AE2LT Lightning capability deferred unless project requirements change.
