import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Assigns each input pixel to a destination in the reference image.
 */
class PixelMapper {

    static Pixel[] createMapping(BufferedImage inputImage, BufferedImage referenceImage) {
        int width = inputImage.getWidth();
        int height = inputImage.getHeight();
        int totalPixels = width * height;
        Pixel[] pixels = new Pixel[totalPixels];

        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = inputImage.getRGB(x, y);
                pixels[index] = new Pixel(x, y, rgb);
                index++;
            }
        }

        assignDestinationPositions(pixels, referenceImage);
        return pixels;
    }

    private static void assignDestinationPositions(Pixel[] pixels, BufferedImage referenceImage) {
        int width = referenceImage.getWidth();
        int height = referenceImage.getHeight();
        int bucketCount = 32;
        Map<Integer, Queue<Pixel>> buckets = new HashMap<>();

        for (Pixel pixel : pixels) {
            int bucket = getColorBucket(pixel.rgb, bucketCount);
            buckets.computeIfAbsent(bucket, k -> new ArrayDeque<>()).add(pixel);
        }

        ReferencePixel[] referencePixels = new ReferencePixel[width * height];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = referenceImage.getRGB(x, y);
                referencePixels[index++] = new ReferencePixel(x, y, rgb);
            }
        }

        Arrays.sort(referencePixels, Comparator.comparingDouble(r -> brightness(r.rgb)));

        for (ReferencePixel target : referencePixels) {
            Pixel bestPixel = findBestPixel(target.rgb, buckets, bucketCount);
            if (bestPixel != null) {
                bestPixel.targetX = target.x;
                bestPixel.targetY = target.y;
            }
        }
    }

    private static Pixel findBestPixel(
            int targetRGB,
            Map<Integer, Queue<Pixel>> buckets,
            int bucketCount) {
        int targetBucket = getColorBucket(targetRGB, bucketCount);
        Queue<Pixel> queue = buckets.get(targetBucket);

        if (queue != null && !queue.isEmpty()) {
            Pixel best = null;
            double bestDistance = Double.MAX_VALUE;
            int checks = Math.min(queue.size(), 100);
            Pixel[] candidates = new Pixel[checks];

            for (int i = 0; i < checks; i++) {
                candidates[i] = queue.poll();
            }

            for (Pixel candidate : candidates) {
                double distance = colorDistance(candidate.rgb, targetRGB);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                } else {
                    queue.add(candidate);
                }
            }

            if (best != null) {
                return best;
            }
        }

        for (int radius = 1; radius <= 4; radius++) {
            for (int r = -radius; r <= radius; r++) {
                for (int g = -radius; g <= radius; g++) {
                    for (int b = -radius; b <= radius; b++) {
                        int bucket = moveBucket(targetBucket, r, g, b, bucketCount);
                        Queue<Pixel> candidateQueue = buckets.get(bucket);
                        if (candidateQueue != null && !candidateQueue.isEmpty()) {
                            return candidateQueue.poll();
                        }
                    }
                }
            }
        }

        for (Queue<Pixel> remainingPixels : buckets.values()) {
            if (!remainingPixels.isEmpty()) {
                return remainingPixels.poll();
            }
        }

        return null;
    }

    private static int getColorBucket(int rgb, int bucketCount) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        int br = r * bucketCount / 256;
        int bg = g * bucketCount / 256;
        int bb = b * bucketCount / 256;
        return (br << 10) | (bg << 5) | bb;
    }

    private static int moveBucket(
            int original,
            int dr,
            int dg,
            int db,
            int bucketCount) {
        int r = (original >> 10) & 31;
        int g = (original >> 5) & 31;
        int b = original & 31;
        r += dr;
        g += dg;
        b += db;
        r = Math.max(0, Math.min(bucketCount - 1, r));
        g = Math.max(0, Math.min(bucketCount - 1, g));
        b = Math.max(0, Math.min(bucketCount - 1, b));
        return (r << 10) | (g << 5) | b;
    }

    private static double colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 255;
        int g1 = (rgb1 >> 8) & 255;
        int b1 = rgb1 & 255;
        int r2 = (rgb2 >> 16) & 255;
        int g2 = (rgb2 >> 8) & 255;
        int b2 = rgb2 & 255;
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return dr * dr + dg * dg + db * db;
    }

    private static double brightness(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
}
