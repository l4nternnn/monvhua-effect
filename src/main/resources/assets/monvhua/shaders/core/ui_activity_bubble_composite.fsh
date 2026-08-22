#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 sample = texture(InSampler, texCoord);
    bool pixelStyle = vertexColor.a < 0.998;
    if (pixelStyle) {
        // A pixel in the 28x18 source mask should stay crisp when it covers
        // several screen pixels, but blend normally when the bubble is small.
        vec2 sourceSize = vec2(512.0, 346.0);
        vec2 nearestUv = (floor(texCoord * sourceSize) + 0.5) / sourceSize;
        vec4 nearest = texture(InSampler, nearestUv);
        float logicalPixelFootprint = max(
            fwidth(texCoord.x) * 28.0,
            fwidth(texCoord.y) * 18.0
        );
        float crispWeight = 1.0 - smoothstep(0.35, 1.25, logicalPixelFootprint);
        // Restrict the nearest-neighbor correction to the outer alpha edge.
        // Image and item content remain linearly sampled inside the bubble.
        float edgeBand = 4.0 * sample.a * (1.0 - sample.a);
        float edgeGradient = clamp(fwidth(sample.a) * 4.0, 0.0, 1.0);
        float edgeWeight = crispWeight * max(edgeBand, edgeGradient);
        sample = mix(sample, nearest, edgeWeight);
    }
    fragColor = sample;
}
