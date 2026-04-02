#version 300 es
precision mediump float;

// ── Inputs ──────────────────────────────────────────────────────────────────
in vec2 v_TexCoord;

// ── Uniforms ─────────────────────────────────────────────────────────────────
// AP centers: xy = NDC position, z = signal weight (0.1–1.0), w = unused
uniform vec4  u_ApCenters[8];

// AP territory colors: rgba (premultiplied, alpha is territory fill alpha)
uniform vec4  u_ApColors[8];

// Number of active APs (1..8)
uniform int   u_ApCount;

// Elapsed time in seconds for animations
uniform float u_Time;

// Viewport aspect ratio (width / height) for aspect-corrected distance
uniform float u_AspectRatio;

// Distance delta threshold: pixels within this NDC delta of the border get border treatment
uniform float u_BorderThreshold;

// Territory fill alpha (0.0–1.0, typically 0.25)
uniform float u_TerritoryAlpha;

// Pulse speed multiplier for border animation (radians/second)
uniform float u_BorderPulseSpeed;

// ── Output ────────────────────────────────────────────────────────────────────
out vec4 fragColor;

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Weighted distance from pixel to AP center.
 * Divides euclidean distance by signal weight so stronger APs "attract" more pixels.
 * Aspect ratio correction ensures circles, not ellipses.
 */
float weightedDist(vec2 pixel, vec4 apCenter) {
    vec2 delta = pixel - apCenter.xy;
    delta.x *= u_AspectRatio; // correct for non-square viewport
    float dist = length(delta);
    float weight = max(apCenter.z, 0.001); // guard against division by zero
    return dist / weight;
}

void main() {
    // Convert tex coords (0..1) to NDC (-1..1)
    vec2 ndc = v_TexCoord * 2.0 - 1.0;

    int count = clamp(u_ApCount, 0, 8);

    if (count == 0) {
        fragColor = vec4(0.0);
        return;
    }

    // ── 1. Find winner and runner-up via weighted Voronoi ────────────────────

    float minDist  = 1e9;
    float minDist2 = 1e9; // second smallest
    int   winner   = 0;

    for (int i = 0; i < count; i++) {
        float d = weightedDist(ndc, u_ApCenters[i]);
        if (d < minDist) {
            minDist2 = minDist;
            minDist  = d;
            winner   = i;
        } else if (d < minDist2) {
            minDist2 = d;
        }
    }

    // ── 2. Border zone detection ─────────────────────────────────────────────

    float borderGap = minDist2 - minDist; // how close is the runner-up?
    bool inBorderZone = (borderGap < u_BorderThreshold) && (count > 1);

    // ── 3. Compose output color ──────────────────────────────────────────────

    vec4 winnerColor = u_ApColors[winner];
    vec4 outColor;

    if (inBorderZone) {
        // Pulsing white border: blend winner color toward white based on proximity + time
        float borderFactor = 1.0 - (borderGap / u_BorderThreshold); // 0=edge of zone, 1=dead center
        float pulse = 0.5 + 0.5 * sin(u_Time * u_BorderPulseSpeed); // 0..1 oscillation
        float borderAlpha = borderFactor * pulse;

        vec4 borderColor = vec4(1.0, 1.0, 1.0, 1.0); // white
        outColor = mix(
            vec4(winnerColor.rgb, u_TerritoryAlpha),
            vec4(borderColor.rgb, 0.75),
            borderAlpha
        );
    } else {
        // Territory fill — winner color at territory alpha
        outColor = vec4(winnerColor.rgb, u_TerritoryAlpha);
    }

    fragColor = outColor;
}
