#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 uv;
in float kind;
in float glow;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    float d = length(uv);
    float a;
    if (kind > 1.5) {
        // light shaft: soft across, fading towards the bottom
        a = (1.0 - abs(uv.x)) * (1.0 - abs(uv.x)) * smoothstep(-1.0, 0.6, uv.y);
    } else if (kind > 0.5) {
        // foam patch: a lumpy, bubbly disc
        float b = hash(floor(uv * 6.0 + 17.0));
        a = (1.0 - smoothstep(0.55, 1.0, d)) * (0.55 + 0.45 * b);
    } else {
        // droplet: a soft round dot with a bright core
        a = 1.0 - smoothstep(0.35, 1.0, d);
        a = a * a;
    }
    vec4 c = vec4(vertexColor.rgb * (glow > 0.5 ? 1.0 + (1.0 - d) * 0.6 : 1.0), vertexColor.a * a) * ColorModulator;
    if (c.a < 0.02) {
        discard;
    }
    fragColor = apply_fog(c, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart,
            FogRenderDistanceEnd, FogColor);
}
