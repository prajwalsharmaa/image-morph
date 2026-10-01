import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * ImageMorph
 *
 * Rearranges the pixels of an input image so that their positions
 * gradually form the appearance of a reference image.
 *
 * IMPORTANT:
 * The pixels keep their ORIGINAL colors.
 * Only their positions are changed.
 */
public class ImageMorph extends JPanel {

    // Maximum image dimension used by the algorithm.
    // Keeping this moderate makes pixel matching and animation manageable.
    private static final int MAX_SIZE = 500;

    // Animation duration in milliseconds.
    private static final int ANIMATION_DURATION = 8000;

    // Approximately 50 FPS.
    private static final int TIMER_DELAY = 20;

    private BufferedImage inputImage;
    private BufferedImage referenceImage;

    private Pixel[] pixels;

    private Timer timer;

    private long animationStartTime;

    private double progress = 0.0;

    private boolean animationFinished = false;


    public ImageMorph(
            BufferedImage inputImage,
            BufferedImage referenceImage) {

        this.inputImage = resizeImage(inputImage);
        this.referenceImage = resizeImage(referenceImage);

        // Both images must have the same dimensions.
        int width = Math.min(
                this.inputImage.getWidth(),
                this.referenceImage.getWidth()
        );

        int height = Math.min(
                this.inputImage.getHeight(),
                this.referenceImage.getHeight()
        );

        this.inputImage = resizeTo(
                this.inputImage,
                width,
                height
        );

        this.referenceImage = resizeTo(
                this.referenceImage,
                width,
                height
        );

        setPreferredSize(
                new Dimension(width, height)
        );

        // Create pixels and calculate their destinations.
        createPixelMapping();

        // Start animation.
        startAnimation();
    }


    /**
     * Creates the input pixel array and assigns every input pixel
     * a destination position in the reference image.
     */
    private void createPixelMapping() {

        int width = inputImage.getWidth();
        int height = inputImage.getHeight();

        int totalPixels = width * height;

        pixels = new Pixel[totalPixels];

        /*
         * Create input pixels.
         */
        int index = 0;

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                int rgb = inputImage.getRGB(x, y);

                pixels[index] =
                        new Pixel(
                                x,
                                y,
                                rgb
                        );

                index++;
            }
        }

        /*
         * Find destination positions in the reference image.
         */
        assignDestinationPositions();
    }


    /**
     * Assigns every input pixel to one unique destination pixel
     * in the reference image.
     *
     * The destination is selected based on color similarity.
     */
    private void assignDestinationPositions() {

        int width = referenceImage.getWidth();
        int height = referenceImage.getHeight();

        /*
         * Create buckets for input pixels.
         *
         * Instead of comparing every reference pixel against
         * every input pixel, we group similar colors together.
         */
        int bucketCount = 32;

        Map<Integer, Queue<Pixel>> buckets =
                new HashMap<>();

        /*
         * Put every input pixel into a color bucket.
         */
        for (Pixel pixel : pixels) {

            int bucket =
                    getColorBucket(
                            pixel.rgb,
                            bucketCount
                    );

            buckets
                    .computeIfAbsent(
                            bucket,
                            k -> new ArrayDeque<>()
                    )
                    .add(pixel);
        }

        /*
         * Create a list of reference positions.
         */
        ReferencePixel[] referencePixels =
                new ReferencePixel[
                        width * height
                        ];

        int index = 0;

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                int rgb =
                        referenceImage.getRGB(x, y);

                referencePixels[index++] =
                        new ReferencePixel(
                                x,
                                y,
                                rgb
                        );
            }
        }

        /*
         * Sort reference pixels by brightness.
         *
         * This helps preserve the general structure of
         * light and dark areas.
         */
        Arrays.sort(
                referencePixels,
                Comparator.comparingDouble(
                        r -> brightness(r.rgb)
                )
        );

        /*
         * Assign each reference position an input pixel.
         */
        for (ReferencePixel target : referencePixels) {

            Pixel bestPixel =
                    findBestPixel(
                            target.rgb,
                            buckets,
                            bucketCount
                    );

            if (bestPixel != null) {

                bestPixel.targetX = target.x;
                bestPixel.targetY = target.y;
            }
        }
    }


    /**
     * Finds an unused input pixel whose color is closest
     * to the requested reference color.
     */
    private Pixel findBestPixel(
            int targetRGB,
            Map<Integer, Queue<Pixel>> buckets,
            int bucketCount) {

        int targetBucket =
                getColorBucket(
                        targetRGB,
                        bucketCount
                );

        /*
         * First look inside the exact color bucket.
         */
        Queue<Pixel> queue =
                buckets.get(targetBucket);

        if (queue != null && !queue.isEmpty()) {

            Pixel best = null;

            double bestDistance =
                    Double.MAX_VALUE;

            /*
             * Examine a limited number of pixels from
             * the bucket.
             *
             * This prevents extremely large buckets from
             * becoming expensive.
             */
            int checks =
                    Math.min(
                            queue.size(),
                            100
                    );

            Pixel[] candidates =
                    new Pixel[checks];

            for (int i = 0; i < checks; i++) {

                candidates[i] =
                        queue.poll();
            }

            for (Pixel candidate : candidates) {

                double distance =
                        colorDistance(
                                candidate.rgb,
                                targetRGB
                        );

                if (distance < bestDistance) {

                    bestDistance = distance;
                    best = candidate;

                } else {

                    /*
                     * Put candidates that were not selected
                     * back into the queue.
                     */
                    queue.add(candidate);
                }
            }

            if (best != null) {

                return best;
            }
        }


        /*
         * Exact bucket was empty.
         *
         * Search neighboring buckets.
         */
        for (int radius = 1; radius <= 4; radius++) {

            for (int r = -radius; r <= radius; r++) {

                for (int g = -radius; g <= radius; g++) {

                    for (int b = -radius; b <= radius; b++) {

                        int bucket =
                                moveBucket(
                                        targetBucket,
                                        r,
                                        g,
                                        b,
                                        bucketCount
                                );

                        Queue<Pixel> candidateQueue =
                                buckets.get(bucket);

                        if (candidateQueue != null &&
                                !candidateQueue.isEmpty()) {

                            return candidateQueue.poll();
                        }
                    }
                }
            }
        }


        /*
         * As a last resort, find any remaining pixel.
         */
        for (Queue<Pixel> q : buckets.values()) {

            if (!q.isEmpty()) {

                return q.poll();
            }
        }

        return null;
    }


    /**
     * Converts RGB into a color bucket.
     */
    private int getColorBucket(
            int rgb,
            int bucketCount) {

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        int br =
                r * bucketCount / 256;

        int bg =
                g * bucketCount / 256;

        int bb =
                b * bucketCount / 256;

        return
                (br << 10) |
                        (bg << 5) |
                        bb;
    }


    /**
     * Moves a bucket in RGB bucket space.
     */
    private int moveBucket(
            int original,
            int dr,
            int dg,
            int db,
            int bucketCount) {

        int r =
                (original >> 10) & 31;

        int g =
                (original >> 5) & 31;

        int b =
                original & 31;

        r += dr;
        g += dg;
        b += db;

        r = Math.max(
                0,
                Math.min(
                        bucketCount - 1,
                        r
                )
        );

        g = Math.max(
                0,
                Math.min(
                        bucketCount - 1,
                        g
                )
        );

        b = Math.max(
                0,
                Math.min(
                        bucketCount - 1,
                        b
                )
        );

        return
                (r << 10) |
                        (g << 5) |
                        b;
    }


    /**
     * Calculates Euclidean RGB color distance.
     */
    private double colorDistance(
            int rgb1,
            int rgb2) {

        int r1 =
                (rgb1 >> 16) & 255;

        int g1 =
                (rgb1 >> 8) & 255;

        int b1 =
                rgb1 & 255;


        int r2 =
                (rgb2 >> 16) & 255;

        int g2 =
                (rgb2 >> 8) & 255;

        int b2 =
                rgb2 & 255;


        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;

        return
                dr * dr +
                        dg * dg +
                        db * db;
    }


    /**
     * Calculates brightness of a pixel.
     */
    private double brightness(int rgb) {

        int r =
                (rgb >> 16) & 255;

        int g =
                (rgb >> 8) & 255;

        int b =
                rgb & 255;

        return
                0.299 * r +
                        0.587 * g +
                        0.114 * b;
    }


    /**
     * Starts the morphing animation.
     */
    private void startAnimation() {

        progress = 0.0;

        animationFinished = false;

        animationStartTime =
                System.currentTimeMillis();

        if (timer != null) {

            timer.stop();
        }

        timer =
                new Timer(
                        TIMER_DELAY,
                        e -> updateAnimation()
                );

        timer.start();
    }


    /**
     * Updates animation progress.
     */
    private void updateAnimation() {

        long elapsed =
                System.currentTimeMillis()
                        - animationStartTime;

        progress =
                Math.min(
                        1.0,
                        elapsed /
                                (double)
                                        ANIMATION_DURATION
                );

        /*
         * Smooth easing.
         *
         * Starts slowly, moves faster in the middle,
         * and slows down near the end.
         */
        double easedProgress =
                easeInOut(progress);

        for (Pixel pixel : pixels) {

            pixel.currentX =
                    pixel.startX +
                            (pixel.targetX - pixel.startX)
                                    * easedProgress;

            pixel.currentY =
                    pixel.startY +
                            (pixel.targetY - pixel.startY)
                                    * easedProgress;
        }

        repaint();

        if (progress >= 1.0) {

            animationFinished = true;

            timer.stop();

            /*
             * Make sure the final coordinates are exact.
             */
            for (Pixel pixel : pixels) {

                pixel.currentX =
                        pixel.targetX;

                pixel.currentY =
                        pixel.targetY;
            }

            repaint();
        }
    }


    /**
     * Smoothstep easing function.
     */
    private double easeInOut(double value) {

        return
                value *
                        value *
                        (3 - 2 * value);
    }


    /**
     * Draws the current animation frame.
     */
    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        /*
         * Create a blank frame.
         */
        BufferedImage frame =
                new BufferedImage(
                        getWidth(),
                        getHeight(),
                        BufferedImage.TYPE_INT_RGB
                );

        /*
         * Draw every ORIGINAL pixel at its
         * current position.
         */
        for (Pixel pixel : pixels) {

            int x =
                    (int) Math.round(
                            pixel.currentX
                    );

            int y =
                    (int) Math.round(
                            pixel.currentY
                    );

            if (x >= 0 &&
                    x < frame.getWidth() &&
                    y >= 0 &&
                    y < frame.getHeight()) {

                /*
                 * IMPORTANT:
                 *
                 * We use pixel.rgb directly.
                 *
                 * We are NOT changing the color.
                 */
                frame.setRGB(
                        x,
                        y,
                        pixel.rgb
                );
            }
        }

        /*
         * Draw the frame.
         */
        g.drawImage(
                frame,
                0,
                0,
                null
        );
    }


    /**
     * Resize while preserving aspect ratio.
     */
    private BufferedImage resizeImage(
            BufferedImage image) {

        int originalWidth =
                image.getWidth();

        int originalHeight =
                image.getHeight();

        double scale =
                Math.min(
                        1.0,
                        Math.min(
                                MAX_SIZE /
                                        (double)
                                                originalWidth,

                                MAX_SIZE /
                                        (double)
                                                originalHeight
                        )
                );

        int newWidth =
                Math.max(
                        1,
                        (int)
                                (originalWidth * scale)
                );

        int newHeight =
                Math.max(
                        1,
                        (int)
                                (originalHeight * scale)
                );

        return resizeTo(
                image,
                newWidth,
                newHeight
        );
    }


    /**
     * Resize image to exact dimensions.
     */
    private BufferedImage resizeTo(
            BufferedImage image,
            int width,
            int height) {

        BufferedImage result =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_RGB
                );

        Graphics2D graphics =
                result.createGraphics();

        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );

        graphics.drawImage(
                image,
                0,
                0,
                width,
                height,
                null
        );

        graphics.dispose();

        return result;
    }


    /**
     * Represents one pixel from the input image.
     */
    private static class Pixel {

        int startX;
        int startY;

        int targetX;
        int targetY;

        double currentX;
        double currentY;

        int rgb;


        Pixel(
                int x,
                int y,
                int rgb) {

            this.startX = x;
            this.startY = y;

            this.targetX = x;
            this.targetY = y;

            this.currentX = x;
            this.currentY = y;

            this.rgb = rgb;
        }
    }


    /**
     * Represents a position/pixel in the reference image.
     */
    private static class ReferencePixel {

        int x;
        int y;
        int rgb;


        ReferencePixel(
                int x,
                int y,
                int rgb) {

            this.x = x;
            this.y = y;
            this.rgb = rgb;
        }
    }


    /**
     * Opens an image file chooser.
     */
    private static BufferedImage chooseImage(
            Component parent,
            String title) {

        JFileChooser chooser =
                new JFileChooser();

        chooser.setDialogTitle(title);

        int result =
                chooser.showOpenDialog(parent);

        if (result !=
                JFileChooser.APPROVE_OPTION) {

            return null;
        }

        try {

            return ImageIO.read(
                    chooser.getSelectedFile()
            );

        } catch (IOException e) {

            JOptionPane.showMessageDialog(
                    parent,
                    "Could not read image:\n"
                            + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );

            return null;
        }
    }


    /**
     * Main application.
     */
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            /*
             * Select input image.
             */
            BufferedImage input =
                    chooseImage(
                            null,
                            "Select Input Image"
                    );

            if (input == null) {
                return;
            }


            /*
             * Select reference image.
             */
            BufferedImage reference =
                    chooseImage(
                            null,
                            "Select Reference Image"
                    );

            if (reference == null) {
                return;
            }


            /*
             * Create the morph panel.
             */
            ImageMorph morph =
                    new ImageMorph(
                            input,
                            reference
                    );


            /*
             * Create JFrame.
             */
            JFrame frame =
                    new JFrame(
                            "ImageMorph"
                    );

            frame.setDefaultCloseOperation(
                    JFrame.EXIT_ON_CLOSE
            );


            /*
             * Restart button.
             */
            JButton restartButton =
                    new JButton(
                            "Restart Morph"
                    );

            restartButton.addActionListener(
                    e -> morph.startAnimation()
            );


            /*
             * Control panel.
             */
            JPanel controls =
                    new JPanel();

            controls.add(
                    restartButton
            );


            /*
             * Main layout.
             */
            frame.setLayout(
                    new BorderLayout()
            );

            frame.add(
                    morph,
                    BorderLayout.CENTER
            );

            frame.add(
                    controls,
                    BorderLayout.SOUTH
            );


            frame.pack();

            frame.setLocationRelativeTo(null);

            frame.setVisible(true);
        });
    }
}