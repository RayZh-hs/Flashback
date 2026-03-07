#version 150

uniform sampler2D AccumSampler;
uniform sampler2D BaseSampler;
uniform sampler2D PassSampler;

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 accum = texture(AccumSampler, texCoord);
    vec4 base = texture(BaseSampler, texCoord);
    vec4 pass = texture(PassSampler, texCoord);
    fragColor = accum + (pass - base) * ColorModulator.a;
}
