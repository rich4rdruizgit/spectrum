#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

in vec2 v_TexCoord;
uniform samplerExternalOES u_Texture;
out vec4 fragColor;

void main() {
    fragColor = texture(u_Texture, v_TexCoord);
}
