#version 300 es
precision mediump float;

const int MAX_DEVICES = 15;

// Per-device data: xy = NDC center, z = intensity (0=hidden), w = phase offset
uniform vec4 u_NodeCenters[MAX_DEVICES];
// Per-device color: rgba
uniform vec4 u_NodeColors[MAX_DEVICES];
// Per-device flags: x = isConnected (1.0 = yes), yzw = unused
uniform vec4 u_NodeFlags[MAX_DEVICES];

uniform int   u_NodeCount;
uniform float u_Time;
uniform float u_AspectRatio;   // viewport width / height
uniform float u_NodeRadius;    // node circle radius in aspect-corrected NDC
uniform float u_RingWidth;     // ring band half-width
uniform float u_LineWidth;     // connection line half-width
uniform float u_LineIntensity; // connection line max alpha
uniform float u_MaxRingRadius; // max expansion radius in aspect-corrected NDC

in vec2 v_TexCoord;
out vec4 fragColor;

// ── Helpers ──────────────────────────────────────────────────────────

/**
 * Minimum distance from point p to the line segment a→b.
 */
float distToSegment(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float lenSq = dot(ab, ab);
    if (lenSq < 0.0001) return distance(p, a);
    float t = clamp(dot(p - a, ab) / lenSq, 0.0, 1.0);
    return distance(p, a + t * ab);
}

void main() {
    // Convert UV (0–1) → NDC (−1…1), apply aspect ratio correction
    vec2 uv = v_TexCoord * 2.0 - 1.0;
    uv.x *= u_AspectRatio;

    vec4 color = vec4(0.0);

    for (int i = 0; i < MAX_DEVICES; i++) {
        if (i >= u_NodeCount) break;

        vec2  center      = u_NodeCenters[i].xy;
        center.x         *= u_AspectRatio;
        float intensity   = u_NodeCenters[i].z;
        float phase       = u_NodeCenters[i].w;
        vec4  nodeColor   = u_NodeColors[i];
        float isConnected = u_NodeFlags[i].x;

        // Skip zero-intensity slots (behind camera or empty)
        if (intensity < 0.001) continue;

        float dist = distance(uv, center);

        // ── 1. Device node (circle SDF) ──────────────────────────────
        float nodeRadius  = u_NodeRadius * mix(0.6, 1.4, intensity);

        // Hard circle edge
        float nodeSDF  = nodeRadius - dist;
        float nodeHard = smoothstep(-0.005, 0.005, nodeSDF);

        // Soft glow halo around the node
        float glowRadius   = nodeRadius * 2.5;
        float glowStrength = mix(0.15, 0.55, intensity);
        float glow         = exp(-dist / glowRadius) * glowStrength * intensity;

        // Combine: solid center + glow
        vec4 nodeContrib = nodeColor * (nodeHard * intensity + glow);

        // ── 2. Proximity ring (expanding per-device) ─────────────────
        // Ring radius cycles 0 → u_MaxRingRadius repeatedly
        float ringSpeed   = mix(0.3, 1.4, intensity);
        float ringRadius  = fract(u_Time * ringSpeed + phase) * u_MaxRingRadius;
        float ringDist    = abs(dist - ringRadius);
        float ring        = smoothstep(u_RingWidth, 0.0, ringDist);

        // Fade ring out as it expands (old ring = transparent)
        float ringFade    = 1.0 - (ringRadius / u_MaxRingRadius);
        vec4  ringContrib = nodeColor * ring * ringFade * intensity * 0.55;

        // ── 3. Connection line (all devices, alpha by connection state) ───────────────
        float lineDist   = distToSegment(uv, vec2(0.0), center);
        float line       = smoothstep(u_LineWidth, 0.0, lineDist);
        float originDist = distance(uv, vec2(0.0));
        float originFade = smoothstep(0.0, nodeRadius * 2.0, originDist);
        float dashPhase  = fract(originDist * 4.0 - u_Time * 1.5);
        float dash       = smoothstep(0.1, 0.4, dashPhase) * smoothstep(0.9, 0.6, dashPhase);
        float lineAlpha  = isConnected > 0.5 ? u_LineIntensity : u_LineIntensity * 0.4;
        vec4 lineContrib = nodeColor * line * originFade * dash * lineAlpha * intensity;

        // ── Accumulate ───────────────────────────────────────────────
        color += nodeContrib + ringContrib + lineContrib;
    }

    // Cap alpha to keep camera feed visible
    color.a = min(color.a, 0.88);
    fragColor = color;
}
