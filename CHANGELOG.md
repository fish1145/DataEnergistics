# ChangeLog

## Version [v1.2.0](https://github.com/fish1145/DataEnergistics/compare/v1.1.0-1.21...v1.2.0-1.21)

### New Features

- Added Data Sanctum portal mode: the station portal now opens the `data_energistics:data_sanctum` dimension, supports bidirectional teleporting, persists return portal positions, and renders the return-side portal model.
- Added the Data Charger and Extended Data Charger, including block entities, client renderers, Jade integration, loot tables, models, textures, and translations.
- Added the Digital Meteorite Compass with server/client payloads, meteorite lookup cache, custom item model, guide entries, and target structure tags.
- Expanded Data Sanctum station controls with status switching, additional mode UI, and black hole mode behavior.
- Added the Data Sanctum Interface part and upgrade support, including upgrade textures, fixed-size upgrade inventory hooks, AE2 mixin accessors, and menu/screen integration.
- Added the Extreme Capacity Interface sheet form and upgrade-card support.
- Added FE charging support for powered energy items.
- Added Curios left/right shoulder slot data and adjusted shoulder doll rendering.
- Added data crystal budding-block tags and guide updates for data meteorite/data crystal content.

### Bug Fixes

- Fixed Data Sanctum placement crashes and AE disconnect state handling.
- Fixed Data Sanctum port reload discovery and improved ME port node connections.
- Fixed Universal Terminal switching into the Pattern Encoding Terminal.
- Fixed Data Distribution Tower Jade status for the upper block.
- Fixed the active-pull tooltip for the Extreme Capacity Interface.
- Fixed data generation positions under water.
- Fixed Data Sanctum black hole mode bottom-block consumption.
- Fixed model and GUI resource issues around the refreshed Data Sanctum assets.
- Fixed upgrade item registration ordering.

### Internal Changes

- Reworked the black hole spherical consumption flow, including centered expansion, range tuning, persisted expansion radius, dev-environment tick timing, data-flow output, and linked-player protection.
- Adjusted Data Sanctum black hole rendering.
- Adjusted Digital Meteorite Fortune yields.
- Adjusted data crystal cluster drops.
- Adjusted Extreme Capacity Interface capacity limits, page layout, and model formatting.
- Optimized AECS self-assembly product return handling.
- Limited nested Adaptive Pattern Providers.
- Adapted the crystal pickaxe for ore-copying behavior.
- Split the login server run configuration.
- Organized client resource formats and removed debug log output.

### Translation Changes

- Updated `en_us` and `zh_cn` entries for the new blocks, items, portal, compass, charger, upgrades, and UI states.

### Other Changes

- Ran Spotless formatting passes across the release range.
