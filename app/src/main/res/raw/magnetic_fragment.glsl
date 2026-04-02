#version 300 es
precision mediump float;

in vec2 v_TexCoord;

uniform float u_Time;
uniform float u_AspectRatio;
uniform float u_FieldX;
uniform float u_FieldY;
uniform float u_Magnitude;
uniform int   u_IsAnomaly;
uniform float u_AnomalyIntensity;
uniform vec3  u_Color;

out vec4 fragColor;

void main() {
    vec2 uvA = vec2(v_TexCoord.x * u_AspectRatio, v_TexCoord.y);

    vec4 result = vec4(0.0);

    // ── Dot grid ─────────────────────────────────────────────────────────────
    float dotSpacing = 0.055;
    vec2  gridFract  = fract(uvA / dotSpacing) - 0.5;
    float dotDist    = length(gridFract);
    float dotRadius  = 0.22;
    float dot        = 1.0 - smoothstep(dotRadius - 0.05, dotRadius, dotDist);

    // Base brightness driven by field intensity (normalized to ICNIRP 200 µT)
    float intensity  = clamp(u_Magnitude / 200.0, 0.0, 1.0);
    float baseGlow   = 0.12 + intensity * 0.35;

    result += vec4(u_Color * dot * baseGlow, dot * baseGlow * 0.55);

    // Subtle field-direction shimmer: dots slightly brighter along field axis
    vec2 fieldDir   = vec2(u_FieldX, u_FieldY);
    float shimmer   = 0.5 + 0.5 * sin(dot(uvA, fieldDir) * 18.0 - u_Time * 1.4);
    result          += vec4(u_Color * dot * shimmer * 0.06, dot * shimmer * 0.06);

    // ── Anomaly: dots around center pulse red/orange ──────────────────────────
    if (u_IsAnomaly == 1) {
        vec2  center      = vec2(0.5 * u_AspectRatio, 0.5);
        float distCenter  = length(uvA - center);
        float anomalyZone = u_AspectRatio * 0.35;
        float falloff     = 1.0 - smoothstep(0.0, anomalyZone, distCenter);
        float pulse       = 0.5 + 0.5 * sin(u_Time * 5.0);
        float anomalyGlow = dot * falloff * u_AnomalyIntensity * (0.6 + pulse * 0.4);

        result += vec4(1.0, 0.18, 0.06, anomalyGlow * 0.85);

        // Expanding ring at center
        float pulseT = fract(u_Time * 1.4);
        float ringR  = 0.04 + pulseT * 0.38 * u_AspectRatio;
        float ringW  = 0.018 * u_AspectRatio;
        float ringA  = (1.0 - smoothstep(0.0, ringW, abs(distCenter - ringR)))
                     * (1.0 - pulseT) * u_AnomalyIntensity;
        result += vec4(1.0, 0.10, 0.06, ringA * 0.9);
    }

    fragColor = clamp(result, 0.0, 1.0);
}
