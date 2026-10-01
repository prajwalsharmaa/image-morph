import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.image.BufferedImage;

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
        pixels = PixelMapper.createMapping(this.inputImage, this.referenceImage);

        // Start animation.
        startAnimation();
    }


    /**
     * Starts the morphing animation.
     */
        void startAnimation() {

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
     * Main application.
     */
    public static void main(String[] args) {
        ImageMorphApp.main(args);
    }
}