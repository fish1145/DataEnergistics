# License Audit: ExtendedAE Plus and Third-party Compatibility

## Scope

This audit reviews the current DataEnergistics repository for:

1. project license metadata and documentation
2. possible copied or adapted code from ExtendedAE Plus
3. whether the repository should remain MIT or move to a mixed-license model

This is an engineering compliance audit, not a final legal opinion.

## Current DataEnergistics license state

- Repository root `LICENSE`: **MIT License**
- `gradle.properties`: `mod_license=MIT`
- `build.gradle`: expands `mod_license` into generated mod metadata
- `src/main/templates/META-INF/neoforge.mods.toml`: `license = "${mod_license}"`
- `build.gradle`: does not currently add a separate Maven POM license block under `publishing`

Observed gaps before this update:

- `README.md` did not previously summarize the project license
- no `NOTICE.md` or `THIRD_PARTY_NOTICES.md` was present
- no `COPYING`, `COPYING.LESSER`, or per-file SPDX headers were present

## ExtendedAE Plus reference used for comparison

Comparison was performed against a local, non-committed ExtendedAE Plus clone kept outside the tracked source set.

Confirmed upstream license signals from that clone:

- `LICENSE.txt`: GNU Lesser General Public License Version 3
- `gradle.properties`: `mod_license=LGPL-3.0`

## Audit method

The audit compared:

- repository license files and mod metadata
- README and docs wording
- direct source references to ExtendedAE Plus packages, ids, translation keys, and menu classes
- DataEnergistics entity acceleration / data ripper code
- DataEnergistics pattern terminal, wireless terminal, renamer, and AE2WTLib UI integration code
- corresponding ExtendedAE Plus wireless pattern terminal and pattern-access terminal sources

Representative DataEnergistics files reviewed:

- `src/main/java/com/fish_dan_/data_energistics/integration/ExtendedAePlusCompat.java`
- `src/main/java/com/fish_dan_/data_energistics/integration/ExtendedAeRenamerCompat.java`
- `src/main/java/com/fish_dan_/data_energistics/client/screen/PatternEncodingPreviewScreen.java`
- `src/main/java/com/fish_dan_/data_energistics/client/screen/WirelessPatternEncodingTermScreen.java`
- `src/main/java/com/fish_dan_/data_energistics/client/screen/UniversalTerminalScreenHook.java`
- `src/main/java/com/fish_dan_/data_energistics/client/screen/UniversalTerminalClientHelper.java`
- `src/main/java/com/fish_dan_/data_energistics/integration/Ae2WtLibCompat.java`
- `src/main/java/com/fish_dan_/data_energistics/Config.java`
- `src/main/java/com/fish_dan_/data_energistics/part/DataRipperPart.java`
- `src/main/java/com/fish_dan_/data_energistics/blockentity/DataRipperReassemblerBlockEntity.java`

Representative ExtendedAE Plus files reviewed:

- `src/main/java/com/glodblock/github/extendedae/client/gui/GuiExPatternTerminal.java`
- `src/main/java/com/glodblock/github/extendedae/container/ContainerExPatternTerminal.java`
- `src/main/java/com/glodblock/github/extendedae/xmod/wt/GuiWirelessExPAT.java`
- `src/main/java/com/glodblock/github/extendedae/xmod/wt/ContainerWirelessExPAT.java`
- `src/main/java/com/glodblock/github/extendedae/xmod/wt/HostWirelessExPAT.java`
- `src/main/java/com/glodblock/github/extendedae/container/ContainerRenamer.java`

## Findings

### 1. ExtendedAE Plus is present as an optional integration target, not as vendored source

DataEnergistics contains normal compatibility references to ExtendedAE and ExtendedAE Plus, such as:

- mod ids in metadata and compat helpers
- translation keys like `block.extendedae.ex_pattern_provider`
- reflective or direct runtime references used for optional integration and mixins

These references are expected for compatibility and do **not** by themselves indicate copied source.

### 2. No copied ExtendedAE Plus source code was found in this audit

The current repository audit found **no** file in `src/main/java` or `src/main/resources` that:

- uses the `com.glodblock.github.extendedae` package namespace as repository-owned code
- preserves ExtendedAE Plus copyright or license headers
- contains obvious copied class names such as `GuiExPatternTerminal`, `ContainerExPatternTerminal`, `GuiWirelessExPAT`, `HostWirelessExPAT`, or `ItemWirelessExPAT`
- reproduces ExtendedAE Plus translation namespaces such as `gui.extendedae.*` as repository-owned UI text bundles

The reviewed DataEnergistics classes use their own package structure, their own translation namespace (`data_energistics`), and their own menu/screen helper types.

### 3. Wireless / pattern terminal work is best classified as similar concept only

The pattern terminal and wireless terminal surfaces in both repositories address related AE2 UX problems, but the reviewed DataEnergistics implementation does not present strong evidence of copied ExtendedAE Plus source.

Observed differences include:

- different top-level class names and package structure
- different UI inheritance paths
- different translation key namespaces
- different helper structure for provider upload, rename, and universal terminal behavior
- DataEnergistics `Ae2WtLibCompat` uses a guarded reflective replacement path instead of embedding ExtendedAE Plus screen code

Classification for this area: **B. Similar concept only**

### 4. Entity acceleration / data ripper work does not match ExtendedAE Plus source

DataEnergistics acceleration-related code centers on its own `DataRipper*` classes and config entries such as:

- `dataRipperBaseCost`
- `dataRipperBlacklist`
- `dataRipperMultipliers`
- `Setting<>("accelerate", YesNo.class)`

The ExtendedAE Plus comparison repo did not expose a matching entity-speed or data-ripper implementation surface. The hits found there were unrelated crafting accelerator and tag blacklist logic, not a matching block/entity acceleration subsystem.

Classification for this area: **A. No evidence of copied code**

### 5. One local fallback texture name is not enough to prove derivation

DataEnergistics still contains a fallback texture path:

- `textures/part/entity_speed_ticker_back.png`

This naming is unusual, but the audit did not find a matching ExtendedAE Plus source/resource surface that would justify classifying it as copied or adapted code on its own.

Classification for this indicator remains below a derived-code threshold. Current evidence is insufficient for a copied-code claim.

## Audit conclusion

Overall classification: **A. No evidence of copied code** for ExtendedAE Plus source in the current repository.

Explicit conclusion:

**No copied ExtendedAE Plus source code was found in this audit.**

Based on the evidence reviewed here, the repository should remain under its current MIT project license for original DataEnergistics code.

## Recommended handling

Recommended handling for the current repository state:

- keep the repository `LICENSE` as MIT
- add third-party notices for ExtendedAE Plus and other compatibility dependencies
- do not add `COPYING.LESSER` unless LGPL-derived source is actually introduced or identified
- do not convert the whole repository to LGPL based on the current evidence
- if future evidence shows a specific file was adapted from ExtendedAE Plus, mark that file individually and reassess mixed-license handling

## Follow-up

If future review uncovers stronger provenance evidence for any file:

1. identify the exact upstream source path and commit
2. compare the file body directly
3. decide whether to rework the file, mark it as LGPL-derived, or request additional permission from the upstream author
