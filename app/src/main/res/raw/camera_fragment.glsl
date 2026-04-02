#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

in vec2 v_TexCoord;
uniform samplerExternalOES u_Texture;
uniform float u_VignetteStrength;
uniform float u_Contrast;
uniform float u_Saturation;
uniform vec3 u_TintColor;
uniform bool u_TrackingLost;
out vec4 fragColor;

void main() {
    vec3 color = texture(u_Texture, v_TexCoord).rgb;

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float effectiveSat = u_TrackingLost ? 0.0 : u_Saturation;
    color = mix(vec3(luma), color, effectiveSat);

    color = (color - 0.5) * u_Contrast + 0.5;
    color = clamp(color, 0.0, 1.0);

    color *= u_TintColor;

    // Rectangular frame vignette — four independent edge falloffs multiplied
    float edgeX = smoothstep(0.0, 0.07, v_TexCoord.x) * smoothstep(1.0, 0.93, v_TexCoord.x);
    float edgeY = smoothstep(0.0, 0.07, v_TexCoord.y) * smoothstep(1.0, 0.93, v_TexCoord.y);
    float vignette = mix(1.0, edgeX * edgeY, u_VignetteStrength);
    color *= vignette;

    if (u_TrackingLost) color *= 0.6;

    fragColor = vec4(color, 1.0);
}
