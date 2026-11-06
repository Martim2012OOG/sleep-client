#version 330

// Sleep's spray / foam / ember particles: one draw for all of them.
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;   // -1..1 across the particle; x > 2 = flat foam patch (x - 4), x < -2 = light shaft (x + 4), y > 2 = glowing (y - 4)
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 uv;
out float kind;
out float glow;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    kind = UV0.x > 2.0 ? 1.0 : (UV0.x < -2.0 ? 2.0 : 0.0);
    glow = UV0.y > 2.0 ? 1.0 : 0.0;
    uv = vec2(UV0.x - (kind == 1.0 ? 4.0 : (kind == 2.0 ? -4.0 : 0.0)), UV0.y - glow * 4.0);
    vec4 light = texture(Sampler2, clamp((vec2(UV2) / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    vertexColor = glow > 0.5 ? Color : Color * vec4(light.rgb, 1.0);
}
