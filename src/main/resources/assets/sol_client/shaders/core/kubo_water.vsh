#version 330

// Sleep's own liquid surface (water and lava): drawn over the vanilla surface, no shader pack needed.
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;   // camera-relative
in vec4 Color;      // rgb = sky colour, a = shore (0..1)
in vec2 UV0;        // x = time in seconds, y = radius of the effect in blocks
in ivec2 UV1;       // current (flow) direction * 1000
in ivec2 UV2;       // lightmap
in vec3 Normal;     // direction of the sun (or moon); its length = how bright

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec3 camRel;
out vec3 worldPos;
out vec4 skyAndShore;
out vec4 lightColor;
out vec3 sunVec;
out vec2 flow;
out vec2 timeRadius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    camRel = Position;
    // the world position, kept small (only used for the wave patterns)
    worldPos = Position + vec3(CameraBlockPos - (CameraBlockPos / 4096) * 4096) - CameraOffset;
    skyAndShore = Color;
    lightColor = texture(Sampler2, clamp((vec2(UV2) / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    sunVec = Normal;
    flow = vec2(UV1) / 1000.0;
    timeRadius = UV0;
}
