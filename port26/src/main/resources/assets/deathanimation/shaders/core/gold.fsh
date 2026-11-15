#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in float goldAmount;
in float metalShine;
out vec4 fragColor;
void main() {
    vec4 original=texture(Sampler0,texCoord0);
    if(original.a<.05)discard;
    float luminance=dot(original.rgb,vec3(.2126,.7152,.0722));
    vec3 gold=mix(vec3(.42,.22,.025),vec3(1.0,.79,.24),.38+.62*luminance);
    gold+=metalShine*vec3(.27,.24,.15);
    vec4 color=vec4(mix(original.rgb,gold,clamp(goldAmount,0.0,1.0)),original.a);
    color*=vertexColor*ColorModulator;
    color.rgb=mix(overlayColor.rgb,color.rgb,overlayColor.a);
    color*=lightMapColor;
    fragColor=apply_fog(color,sphericalVertexDistance,cylindricalVertexDistance,FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,FogColor);
}
