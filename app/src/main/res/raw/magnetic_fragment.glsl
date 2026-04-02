#version 300 es
precision mediump float;

in vec2 v_TexCoord;

uniform float u_Time;
uniform float u_AspectRatio;       // viewport width / height
uniform float u_FieldX;            // normalized field direction X [-1, 1]
uniform float u_FieldY;            // normalized field direction Y [-1, 1]
uniform float u_Magnitude;         // raw field strength in µT
uniform int   u_IsAnomaly;         // 0 or 1
uniform float u_AnomalyIntensity;  // [0, 1]
uniform vec3  u_Color;             // pre-computed RGB from magnitude band

out vec4 fragColor;

// ── Utilities ────────────────────────────────────────────────────────────────

float hash21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

// ── Main ─────────────────────────────────────────────────────────────────────
void main() {
    // Aspect-corrected UV space: x in [0, aspectRatio], y in [0, 1]
    vec2 uvA = vec2(v_TexCoord.x * u_AspectRatio, v_TexCoord.y);

    vec2 fieldDir = vec2(u_FieldX, u_FieldY);
    vec2 perpDir  = vec2(-fieldDir.y, fieldDir.x);

    vec4 result = vec4(0.0);

    // ── Layer 1: Animated flow lines ─────────────────────────────────────────
    float lineSpacing = u_AspectRatio / 14.0;

    // Distance to nearest flow line (perpendicular to field)
    float lineCoord = dot(uvA, perpDir);
    float lineDist  = abs(fract(lineCoord / lineSpacing + 0.5) - 0.5) * lineSpacing;
    float lineWidth = lineSpacing * 0.055;
    float lineAlpha = 1.0 - smoothstep(0.0, lineWidth, lineDist);

    // Scrolling animation along field direction
    float flowCoord = dot(uvA, fieldDir);
    float flowAnim  = fract(flowCoord / lineSpacing - u_Time * 0.28);
    float flowFade  = smoothstep(0.0, 0.25, flowAnim) * (1.0 - smoothstep(0.65, 1.0, flowAnim));
    float flowIntensity = lineAlpha * flowFade;

    result += vec4(u_Color * flowIntensity * 0.50, flowIntensity * 0.50);

    // ── Layer 2: Particles flowing along field lines ─────────────────────────
    const float PARTICLE_N = 10.0;
    const float LINE_N     = 14.0;

    for (float i = 0.0; i < PARTICLE_N; i++) {
        float seed      = hash21(vec2(i, 37.5));
        float lineIdx   = floor(i * LINE_N / PARTICLE_N);
        // Perp offset: place particle at its flow-line position
        float perpOff   = (lineIdx + 0.5 + (seed - 0.5) * 0.4) * lineSpacing;
        // Along-field offset: cycle using fract so particle loops endlessly
        float along     = fract(seed * 1.7 + u_Time * (0.18 + seed * 0.22));
        float alongPos  = along * u_AspectRatio * 1.4 - u_AspectRatio * 0.2;

        vec2 particlePos = perpDir * perpOff + fieldDir * alongPos;

        float dist   = length(uvA - particlePos);
        float radius = lineSpacing * 0.13;
        float pAlpha = (1.0 - smoothstep(0.0, radius, dist)) * (0.35 + seed * 0.45);

        result += vec4(u_Color * 1.3 * pAlpha, pAlpha);
    }

    // ── Layer 3: Anomaly pulse ring ──────────────────────────────────────────
    if (u_IsAnomaly == 1) {
        vec2  center = vec2(0.5 * u_AspectRatio, 0.5);
        float distC  = length(uvA - center);
        float pulseT = fract(u_Time * 1.6);
        float ringR  = 0.06 + pulseT * 0.42 * u_AspectRatio;
        float ringW  = 0.022 * u_AspectRatio;
        float ringA  = (1.0 - smoothstep(0.0, ringW, abs(distC - ringR)))
                     * (1.0 - pulseT)
                     * u_AnomalyIntensity;
        result += vec4(1.0, 0.10, 0.06, ringA * 0.88);
    }

    // ── Output ───────────────────────────────────────────────────────────────
    fragColor = clamp(result, 0.0, 1.0);
}
