// Buckshot-Roulette-style retro post-process.
// Reads the offscreen scene texture and:
//   1. snaps UVs to a virtual low-res grid (chunky pixels)
//   2. samples R/G/B at slightly different offsets (chromatic aberration)
//   3. perturbs luminance with a 4×4 Bayer dither pattern
//   4. quantizes to a fixed 16-color muddy palette
//   5. multiplies a soft circular vignette and warm tint
//
// Designed to run as the SpriteBatch shader during a single full-screen blit.
// Inputs come from RetroPostProcess.java.

#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;

// Virtual pixel resolution (chunkiness). 480×270 ~ Buckshot Roulette at 1080p.
uniform vec2 u_lowRes;

// Strength knobs (0.0 disables each).
uniform float u_aberration;   // RGB channel split, e.g. 0.0025
uniform float u_dither;       // dither magnitude in palette space, e.g. 0.06
uniform float u_vignette;     // radial darkening, e.g. 0.55
uniform float u_warm;         // amount of warm-bulb tint, e.g. 0.18

// 16-color palette (locked to docs/art-style.md). RGB in 0..1.
const int PALETTE_SIZE = 16;
uniform vec3 u_palette[PALETTE_SIZE];

// 4×4 Bayer matrix, normalized to [-0.5, 0.5).
float bayer4(vec2 cell) {
    int x = int(mod(cell.x, 4.0));
    int y = int(mod(cell.y, 4.0));
    int i = y * 4 + x;
    // Hand-unrolled because GLSL ES doesn't allow non-const array indexing.
    if (i ==  0) return  0.0/16.0 - 0.5;
    if (i ==  1) return  8.0/16.0 - 0.5;
    if (i ==  2) return  2.0/16.0 - 0.5;
    if (i ==  3) return 10.0/16.0 - 0.5;
    if (i ==  4) return 12.0/16.0 - 0.5;
    if (i ==  5) return  4.0/16.0 - 0.5;
    if (i ==  6) return 14.0/16.0 - 0.5;
    if (i ==  7) return  6.0/16.0 - 0.5;
    if (i ==  8) return  3.0/16.0 - 0.5;
    if (i ==  9) return 11.0/16.0 - 0.5;
    if (i == 10) return  1.0/16.0 - 0.5;
    if (i == 11) return  9.0/16.0 - 0.5;
    if (i == 12) return 15.0/16.0 - 0.5;
    if (i == 13) return  7.0/16.0 - 0.5;
    if (i == 14) return 13.0/16.0 - 0.5;
                 return  5.0/16.0 - 0.5;
}

vec3 quantizeToPalette(vec3 c) {
    vec3  best     = u_palette[0];
    float bestDist = dot(c - best, c - best);
    for (int i = 1; i < PALETTE_SIZE; i++) {
        vec3  p = u_palette[i];
        float d = dot(c - p, c - p);
        if (d < bestDist) { bestDist = d; best = p; }
    }
    return best;
}

void main() {
    // 1. Snap UV to the virtual pixel grid so neighbouring fragments
    //    sample identical texels — that's the chunky-pixel look.
    vec2 cell    = floor(v_texCoords * u_lowRes);
    vec2 lowUv   = (cell + 0.5) / u_lowRes;

    // 2. Chromatic aberration: shift R away from centre, B toward.
    vec2 caDir = (lowUv - 0.5);
    float r = texture2D(u_texture, lowUv + caDir * u_aberration).r;
    float g = texture2D(u_texture, lowUv).g;
    float b = texture2D(u_texture, lowUv - caDir * u_aberration).b;
    vec3 color = vec3(r, g, b);

    // 3. Ordered dithering to break up palette banding.
    color += bayer4(cell) * u_dither;
    color  = clamp(color, 0.0, 1.0);

    // 4. Snap to the 16-color palette.
    color = quantizeToPalette(color);

    // 5a. Warm-bulb tint pulled toward bottom-centre of the frame.
    vec2  warmCentre = vec2(0.5, 0.35);
    float warmDist   = distance(v_texCoords, warmCentre);
    float warm       = (1.0 - smoothstep(0.0, 0.7, warmDist)) * u_warm;
    color = mix(color, color * vec3(1.15, 0.95, 0.78), warm);

    // 5b. Vignette — strong at edges, full brightness at the centre.
    float vDist = distance(v_texCoords, vec2(0.5));
    float vig   = 1.0 - smoothstep(0.35, 0.95, vDist) * u_vignette;
    color *= vig;

    gl_FragColor = vec4(color, 1.0) * v_color;
}
