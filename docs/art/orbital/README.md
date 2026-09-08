# Orbital construct assets

The persistent projection, three firing echoes and descending payload share five authored modules: core, rail,
ring segment, cradle and payload. Each module has an opaque body, a reduced body and separate luminous inserts.
The files under `src/main/resources/assets/data_energistics/models/orbital/` are ordinary Minecraft JSON models
and can be imported into Blockbench. Coordinates use the conventional 16 model units per block. The client
assembles and scales modules; model files do not encode a 512-block-sized cube.

## Texture atlas

`textures/block/orbital/construct.png` is a 1024 x 1024 RGBA atlas. It was generated with the built-in image tool,
then normalized from the returned 1254 x 1254 image using nearest-neighbor sampling for power-of-two mipmaps.
The full source prompt is in `texture-prompt.txt`. The original concept is an art reference; production assets
are the JSON models and this atlas, not the concept image.

The atlas has four equally sized columns and rows. Entries below are ordered left to right:

| Row | Column 1 | Column 2 | Column 3 | Column 4 |
| --- | --- | --- | --- | --- |
| 1 | Ceramic armor | Recessed armor | Ribbed armor | Focusing-ring armor |
| 2 | Graphite structure | Cooling vents | Rail conductor | Hardware socket |
| 3 | Cyan circuitry | Energy core | Violet focusing lattice | Digital lattice |
| 4 | Stepped side panel | Status hardware | Plain ceramic | Dark end cap |

Each tile occupies four UV units in Minecraft's 0..16 UV space. Faces inset their UV rectangle by 0.15 units
to avoid sampling adjoining tiles. Thin luminous inserts use the energy/circuit tiles in a separate emissive
material pass. Armor remains solid and normally lit. Holographic redeployment and firing echoes use a translucent
body pass. The beam reuses Minecraft's beacon texture. Resource reloads and resource packs use the normal model
manager and atlas pipeline; no static copies of baked quads or GPU buffers survive a reload.

## Assembly and performance

- Main rail length: 512 world blocks, aligned along X. The core is offset toward the mass driver, with four
  focusing rings on the opposite end and a downward-facing containment cradle.
- Full detail: up to 1024 blocks, eight rail sections per side and sixteen segments per ring.
- Reduced detail: up to 4096 blocks, simplified module meshes and eight segments per ring.
- Distant detail: two long rail sections per side, the same core/cradle and four simplified rings. Overall scale
  and silhouette are preserved instead of replacing the construct with a smaller box.
- Limits: four full-detail main projections, four full-detail echoes, 64 visible main projections and 32 echoes.
- Model, emissive and beam passes are batched by material. Transparent instances are submitted farthest first.
  The orbital layer uses its own immediate buffer so deferred entity batching (including Iris's unflushable
  wrapper) cannot draw these vertices after the temporary fog parameters have been restored. Native scratch
  memory is reused between frames and released on client logout.
- Camera-relative depth compression retains angular size and direction inside the vanilla far plane. Frustum
  bounds include the actual transformed assembly and any ground link or target ring. Orbital fog changes are
  confined to the rendered batches and restored afterwards.
- Animation uses a pause-aware client clock anchored to public server revisions. Delayed baselines cannot rewind
  cosmetic time. Attack phases, beam targets and damage remain server-authoritative.

## Verification

Compile and run the existing GameTests with the repository wrapper. `GRADLE_USER_HOME` must remain an environment
variable; the following commands do not set a private cache path:

```powershell
.\gradlew.bat compileJava compileTestJava processResources
.\gradlew.bat runGameTestServer
```

The six new GameTests cover sparse-baseline timing, pause/reconnect behavior, precision in old worlds, LOD
boundaries/detail budgets, angular-size preservation, transformed culling bounds and nearby world scale.

For native model screenshots, without loading or changing a saved world:

```powershell
.\gradlew.bat --init-script docs/art/orbital/preview.init.gradle runGameTestClient
```

The init script adds the already-declared test framework to this client run's runtime classpath and sets the
opt-in `DE_ORBITAL_MODEL_PREVIEW` environment flag. It does not change saved Gradle configuration. Dismiss any
third-party mod update prompt to reach the title screen. The test fixture then captures seven views to
`run/gametest/client/screenshots/orbital-model-*.png` and exits. These are actual Minecraft renders of the production
assembly, not generated illustrations.

To exercise the real world renderer, first copy a stopped GameTest world's `world/` directory into
`run/gametest/client/saves/OrbitalRenderVerification/`, without replacing an existing destination, then run:

```powershell
.\gradlew.bat --init-script docs/art/orbital/world-preview.init.gradle runGameTestClient
```

The fixture requires this exact isolated save name. It switches its local test player to spectator, establishes
daylight, and publishes synthetic client-only visual snapshots without creating weapons, damage or attacks.
It captures deployed, redeploying, distant and three-array firing views through the real level-render stage.
Screenshots use the `orbital-world-*.png` prefix. It restores its temporary GUI/focus options and exits after capture.

The 2026-09-08 verification passed all 232 required GameTests, including the six new math regressions. Native
model previews and world-stage smoke captures were checked at 1536 x 864 with Sodium and Iris installed and no
shader pack selected. All 15 models loaded with the 1024 atlas and four mipmap levels. Arbitrary shader packs,
nighttime/underwater views and maximum-count performance stress remain separate validation scenarios.
