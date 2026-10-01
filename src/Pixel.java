/**
 * Represents one pixel from the input image.
 */
class Pixel {

    int startX;
    int startY;

    int targetX;
    int targetY;

    double currentX;
    double currentY;

    int rgb;

    Pixel(int x, int y, int rgb) {
        this.startX = x;
        this.startY = y;
        this.targetX = x;
        this.targetY = y;
        this.currentX = x;
        this.currentY = y;
        this.rgb = rgb;
    }
}