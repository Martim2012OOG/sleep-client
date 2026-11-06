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

// height of the waves at p (only its slope is used, for the light)
float waves(vec2 p, float t) {
    float h = 0.0;
    h += sin(dot(p, vec2(0.80, 0.60)) * 1.10 + t * 1.30) * 0.28;
    h += sin(dot(p, vec2(-0.65, 0.76)) * 1.70 + t * 1.75) * 0.17;
    h += sin(dot(p, vec2(0.21, -0.98)) * 2.60 + t * 2.40) * 0.09;
    h += (noise(p * 1.9 + vec2(t * 0.55, t * 0.40)) - 0.5) * 0.36;
    h += (noise(p * 4.7 - vec2(t * 0.85, -t * 0.65)) - 0.5) * 0.16;
    h += (noise(p * 11.0 + vec2(-t * 1.6, t * 1.3)) - 0.5) * 0.05;
    return h;
}

void main() {
    float t = timeRadius.x;
    float fl = length(flow);
    vec2 p = worldPos.xz - flow * t * 1.6; // the pattern travels with the current
    float e = 0.04;
    float h0 = waves(p, t), hx = waves(p + vec2(e, 0.0), t), hz = waves(p + vec2(0.0, e), t);
    float bump = 0.11 + fl * 0.12;
    vec3 n = normalize(vec3(-(hx - h0) / e * bump, 1.0, -(hz - h0) / e * bump));

    vec3 V = normalize(-camRel);
    if (V.y < 0.0) {
        // ---- seen from under the water: the sky only shows through a bright window above you (Snell's window, ~49 degrees);
        // outside it the surface is a dark mirror of the deep water. Both ripple with the waves.
        vec3 nd = vec3(n.x, -n.y, n.z);
        float cosUp = clamp(dot(-V, vec3(0.0, 1.0, 0.0)) + (n.x + n.z) * 0.18, 0.0, 1.0);
        float window = smoothstep(0.55, 0.78, cosUp);
        vec3 litU = lightColor.rgb;
        vec3 skyU = mix(skyAndShore.rgb, vec3(1.0), 0.25) * (0.35 + 0.65 * litU);
        vec3 mirror = vec3(0.02, 0.10, 0.16) * (0.35 + 0.65 * litU);
        float sparkle = pow(max(dot(reflect(V, nd), normalize(sunVec + vec3(0.0001))), 0.0), 40.0) * length(sunVec);
        vec3 cu = mix(mirror, skyU, window) + vec3(sparkle * 0.6);
        float au = mix(0.92, 0.75, window);
        au *= 1.0 - smoothstep(timeRadius.y - 10.0, timeRadius.y, length(camRel.xz));
        fragColor = apply_fog(vec4(cu, au) * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
                FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
        return;
    }
    float ndv = max(dot(n, V), 0.0);
    float fres = 0.02 + 0.98 * pow(1.0 - ndv, 5.0);
    vec3 R = reflect(-V, n);

    vec3 lit = lightColor.rgb;
    vec3 sky = skyAndShore.rgb;
    vec3 horizon = mix(FogColor.rgb, sky, 0.30);
    vec3 refl = mix(horizon, sky, clamp(R.y * 1.8, 0.0, 1.0)) * (0.25 + 0.75 * lit);
    vec3 deep = vec3(0.02, 0.10, 0.13) * (0.30 + 0.70 * lit);
    vec3 col = mix(deep, refl, fres);

    // the sun (or moon) on the water: a sharp glint and a soft glow
    float sunI = length(sunVec);
    if (sunI > 0.001) {
        vec3 L = sunVec / sunI;
        float s = max(dot(R, L), 0.0);
        col += vec3(1.0, 0.95, 0.82) * (pow(s, 700.0) * 9.0 + pow(s, 70.0) * 0.28) * sunI * lit.b;
    }

    // foam along the shore and on fast currents
    float shore = skyAndShore.a;
    float fn = noise(worldPos.xz * 2.6 + vec2(t * 0.25, -t * 0.18)) * 0.6 + noise(worldPos.xz * 7.5 - flow * t * 3.0) * 0.4;
    // only light patches: lines of foam on currents, a little lace near walls
    float foamAmt = smoothstep(0.78, 1.0, fn * 0.7 + fl * 0.5 + shore * 0.08) * min(1.0, fl * 2.5 + 0.15);
    col = mix(col, vec3(0.90, 0.94, 0.96) * (0.25 + 0.75 * lit), foamAmt * 0.75);

    float a = mix(0.50, 0.97, fres); // looking straight down you see the bottom, at a low angle only the reflection
    a = max(a, foamAmt * 0.9);
    // fade out at the edge of the effect, so it blends into the normal water further away
    float radius = timeRadius.y;
    a *= 1.0 - smoothstep(radius - 10.0, radius, length(camRel.xz));
    if (a < 0.01) {
        discard;
    }
    fragColor = apply_fog(vec4(col, a) * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
