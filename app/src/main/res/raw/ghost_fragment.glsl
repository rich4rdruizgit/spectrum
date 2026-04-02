#version 300 es
precision mediump float;

const int MAX_WAVES = 20;

uniform int u_WaveCount;
uniform vec4 u_WaveCenters[MAX_WAVES];  // xy = NDC, z = intensity, w = phase
uniform vec4 u_WaveColors[MAX_WAVES];   // rgba per wave
uniform float u_Time;                    // elapsed seconds
uniform float u_AspectRatio;             // width / height
uniform float u_WaveFrequency;           // ring count per unit distance
uniform float u_WaveSpeed;              // animation speed (rad/sec)
uniform float u_FalloffRate;            // exponential distance falloff

in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
    // Convert UV (0-1) to NDC (-1 to 1), correct for aspect ratio
    vec2 uv = v_TexCoord * 2.0 - 1.0;
    uv.x *= u_AspectRatio;

    vec4 color = vec4(0.0);

    for (int i = 0; i < MAX_WAVES; i++) {
        if (i >= u_WaveCount) break;

        vec2 center = u_WaveCenters[i].xy;
        center.x *= u_AspectRatio;  // match aspect correction
        float intensity = u_WaveCenters[i].z;
        float phase = u_WaveCenters[i].w;

        // Early-exit for zero-intensity waves (behind camera, etc.)
        if (intensity == 0.0) continue;

        highp float dist = distance(uv, center);

        // Concentric rings: sin wave modulated by distance
        highp float wave = sin(dist * u_WaveFrequency - u_Time * u_WaveSpeed + phase);
        wave = wave * 0.5 + 0.5;  // remap -1..1 to 0..1

        // Sharpen rings (makes rings thinner and more defined)
        wave = smoothstep(0.3, 0.7, wave);

        // Distance falloff: exponential decay from center
        float falloff = exp(-dist * u_FalloffRate) * intensity;

        // Accumulate additive color contribution
        color += u_WaveColors[i] * wave * falloff;
    }

    // Cap alpha to maintain transparency over camera feed
    color.a = min(color.a, 0.8);
    fragColor = color;
}
