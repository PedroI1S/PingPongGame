# Art style

Target: a low-resolution pixelated render of a grimy industrial bunker
where two people sit across a beat-up duel table. Reference:
**Buckshot Roulette / Mike Klubnika**.

The retro look is **two layers**:

1. **Underlying 3D models** — clean low-poly geometry with grime baked
   into hand-painted diffuse textures. No PBR, no normal maps.
2. **Post-process shader** — renders the scene to a low-resolution
   `FrameBuffer`, then upscales with a fragment shader that does
   palette quantization, ordered dithering, vignette, and chromatic
   aberration. This is what gives every frame the chunky-pixel,
   muddy-color, scanline-grainy look.

The 3D-AI tool only needs to deliver layer 1. Layer 2 is in this repo
under `assets/shaders/retro.{vert,frag}` and
`render/RetroPostProcess.java`.

## Palette

Lock all texture authoring to these tones. The shader quantizes the
final image to this exact palette anyway, so colors outside this set
will snap to the nearest match (often badly).

| Role | Hex |
|---|---|
| Deepest black | `#0A0608` |
| Surface fills | `#1A1014` |
| Concrete / metal mid | `#3B342E`, `#4D4036`, `#6B5B4B` |
| Rust / dried-blood reds | `#5E1F1F`, `#7A2A2A`, `#A03B3B` |
| Sickly greens (felt, paint) | `#2E3B26`, `#4A5A3C`, `#7B8C5A` |
| Dirty whites / creams | `#C4B79E`, `#E8DCC0` |
| Overhead-bulb warm | `#8C5C36`, `#D89F66` |

No cool tones anywhere. No saturated blues. The world is sealed in,
lit by a single warm bulb.

## Output format for AI-generated models

`.glb` (preferred) or `.fbx` / `.obj`. Single-mesh per asset,
hand-painted diffuse texture only — no metallic, no roughness, no
normals. **Y-up. Origin at geometric center** unless noted. Drop into
`assets/models/`.

---

## 1. The duel table

> A grimy improvised ping-pong table in an underground bunker, viewed
> slightly above. Heavy industrial steel frame welded from scrap, paint
> peeling off the legs in big flakes showing rust underneath. Playing
> surface is faded matte green (`#4A5A3C`) with sloppy hand-painted
> white border lines and a centerline — paint chipped, mud streaks,
> dark water-damage stains in two corners, a few rectangular
> service-box markings ghosted in like the table was repurposed from a
> different sport. Edges of the playing surface clad in dark riveted
> steel trim with visible bolt heads and welding scars. The whole table
> reads like it's been used for a hundred matches and never cleaned.
> Dimensions: 6 × 14 × 0.2 units for the playing surface, ~2 units of
> stocky industrial leg beneath. Origin at the geometric center of the
> playing surface. Low-poly, ~3–6k tris. Hand-painted texture only — no
> normal maps, no PBR. Reference aesthetic: Buckshot Roulette / Mike
> Klubnika low-res horror grime.

## 2. The net + posts

> A worn, frayed table-tennis net spanning a gritty industrial table.
> Off-white cloth (`#C4B79E`) tinted with brown grime stains, edges
> frayed and missing strands at the bottom, top tape band yellowed and
> stained darker in patches. The two posts are heavy iron clamps in
> oxidized red-rust (`#5E1F1F` over a darker base), bolted onto the
> table edges with visible hex-head bolts and chunks of paint flaking
> off. A single thin chain hangs slack from one post end like it once
> held a tensioner. Dimensions: 6.6 × 0.5 × 0.06 units for the cloth;
> posts add ~0.6 units of height per side. Origin at the geometric
> center of the cloth (straddles x=0). Alpha-tested mesh weave for the
> cloth. Hand-painted texture, low-poly. Reference: Buckshot Roulette
> underground prop weathering.

## 3. The ball

> A standard 40 mm table-tennis ball, but unmistakably dirty — base
> color faded cream-white (`#E8DCC0`), small rusty fingerprint smudge
> on one side, a hairline crack near the seam, faint dust speckling
> across the whole surface. Light specular only on the cleanest patch;
> the rest is matte grime. 0.36 units diameter, origin at center, ~300
> tris. Hand-painted texture, no normals. The ball should look like
> it's been bouncing around this room for months.

## 4. The arena room

This is the big one. The current scene is just a floor slab; without
the room around it the table will float in black and the whole vibe
collapses.

> A small, claustrophobic underground bunker-room set, designed as a
> 360° interior with a ping-pong table at the center. Bare red-brick
> walls heavily blackened by soot and stained with rust streaks.
> Concrete floor with cracks, dark grease stains, scattered cigarette
> butts, and a single circular pool of warm yellow light (`#D89F66`)
> under a hanging industrial pendant lamp directly over the table.
> Walls dressed with: an old beige tube TV / oscilloscope sitting on a
> wooden crate to one side; a battered chrome boombox cassette deck on
> the other; tangles of black power cables and red-and-black wires
> draped along the ceiling and down the walls; a cluster of stage-light
> fixtures clipped onto an exposed steel I-beam overhead, two of them
> aimed down toward the table; a couple of ragged dark cloth banners
> hung from chains. Ambient atmosphere: dust motes in the lamp cone,
> deep shadows in every corner, no skybox, no windows — this place is
> sealed in. Footprint about 30 × 30 units, walls ~10 units tall.
> Origin at the center of the floor. Low-poly, ~10–20k tris total.
> Texture in the limited palette specified. Reference aesthetic:
> Buckshot Roulette host room, Cruelty Squad indoor sets, Iron Lung
> interior.

## Loading the models in code

After dropping the `.glb` files into `assets/models/`, replace the
`ModelBuilder` calls in `MatchArenaRenderer.buildModels()` with:

```java
assetManager.load("models/table.glb", Model.class);
assetManager.load("models/net.glb",   Model.class);
assetManager.load("models/ball.glb",  Model.class);
assetManager.load("models/arena.glb", Model.class);
// then later:
tableInstance = new ModelInstance(assetManager.get("models/table.glb", Model.class));
```

For `.glb` you'll need libGDX's `gdx-gltf` extension dependency (it's
not in the core distribution). Or convert to `.g3db` with `fbx-conv`
and load directly through `G3dModelLoader`.

## Post-process pipeline

Source files:

- `assets/shaders/retro.vert` — pass-through vertex shader for a
  full-screen sprite quad.
- `assets/shaders/retro.frag` — does the work: pixelate UV → sample
  with chromatic aberration → apply Bayer 4×4 dither → quantize to the
  palette → vignette.
- `core/.../render/RetroPostProcess.java` — wraps a window-sized
  `FrameBuffer` and the shader. Each screen calls `begin()` /
  `endAndBlit()` around its render code.

The shader's virtual resolution (the chunkiness of the pixels) is the
`u_lowRes` uniform. Lower = chunkier. The default is 480 × 270 — close
to the source-of-truth Buckshot Roulette look at 1080p.

The palette is hard-coded in the fragment shader (16 colors). Adjust
both there and in `Palette.java` if you change the visual target.

### How to wire it into a screen

```java
// In show():
postProcess = context.getPostProcess();

// In render(delta):
postProcess.begin();
//   ... existing 3D pass ...
//   ... existing 2D HUD pass ...
postProcess.endAndBlit();
```

That's the whole change — the FBO captures everything you would have
drawn to the back buffer, the shader does the look, the screen
ultimately gets the post-processed frame.

## Things that should NOT happen

- **Don't author PBR materials.** The shader ignores anything that's
  not in the diffuse channel, and PBR setups will look flat in this
  lighting.
- **Don't pre-pixelate textures.** The pixelation is global, applied
  once at the end of the frame. If your textures are already lo-fi
  they'll get double-quantized into mush.
- **Don't add bloom or HDR.** The palette is fixed and very
  low-dynamic-range. Bloom blows out the carefully limited tones.
- **Don't add real-time shadows.** Buckshot Roulette doesn't have them
  and they read wrong against the chunky pixel grid.
- **Don't render UI text at the FBO's low res.** It'll be unreadable.
  Render the HUD at native resolution — the post-process shader is
  designed to read crisp UI text and crunch only the 3D scene.
