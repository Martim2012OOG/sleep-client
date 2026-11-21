#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec3 camRel;
in vec3 worldPos;
in vec4 skyAndShore;
in vec4 lightColor;
in vec3 sunVec;
in vec2 flow;
in vec2 timeRadius;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

void main() {
    float t = timeRadius.x;
    vec2 p = worldPos.xz * 0.55 - flow * t * 0.9;
    // slowly churning molten rock: warp the pattern by itself
    vec2 q = vec2(fbm(p + vec2(0.0, t * 0.06)), fbm(p + vec2(5.2, 1.3) - vec2(t * 0.05, 0.0)));
    float n = fbm(p + q * 1.8 + vec2(t * 0.03, -t * 0.02));
    float cracks = smoothstep(0.50, 0.78, fbm(p * 2.2 + q * 2.5 - vec2(t * 0.08)));

    vec3 crust = vec3(0.22, 0.03, 0.01);
    vec3 hot = vec3(1.00, 0.36, 0.03);
    vec3 white = vec3(1.00, 0.86, 0.42);
    vec3 col = mix(crust, hot, smoothstep(0.30, 0.70, n));
    col = mix(col, white, cracks * 0.75);

    // a wet, glossy look: the sky and the sun on top
    float e = 0.05;
    float h0 = fbm(p * 1.5), hx = fbm((p + vec2(e, 0.0)) * 1.5), hz = fbm((p + vec2(0.0, e)) * 1.5);
    vec3 nrm = normalize(vec3(-(hx - h0) / e * 0.35, 1.0, -(hz - h0) / e * 0.35));
    vec3 V = normalize(-camRel);
    vec3 R = reflect(-V, nrm);
    float fres = 0.04 + 0.96 * pow(1.0 - max(dot(nrm, V), 0.0), 5.0);
    col += skyAndShore.rgb * fres * 0.25;
    float sunI = length(sunVec);
    if (sunI > 0.001) {
        float s = max(dot(R, sunVec / sunI), 0.0);
        col += vec3(1.0, 0.9, 0.7) * pow(s, 120.0) * 0.8 * sunI;
    }

    float a = 1.0 - smoothstep(timeRadius.y - 10.0, timeRadius.y, length(camRel.xz));
    if (a < 0.01) {
        discard;
    }
    // lava glows: no lightmap, the fog still applies
    fragColor = apply_fog(vec4(col, a) * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
