/**
 * Represents a position/pixel in the reference image.
 */
class ReferencePixel {

    int x;
    int y;
    int rgb;

    ReferencePixel(int x, int y, int rgb) {
        this.x = x;
        this.y = y;
        this.rgb = rgb;
    }
}