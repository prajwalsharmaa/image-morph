import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Starts the ImageMorph application and builds its window.
 */
public class ImageMorphApp {

    private static BufferedImage chooseImage(Component parent, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
            "Supported image files",
            ImageIO.getReaderFileSuffixes()
        ));
        chooser.setDialogTitle(title);

        int result = chooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(chooser.getSelectedFile());
            if (image == null) {
                JOptionPane.showMessageDialog(
                        parent,
                        "The selected file is not a supported image.",
                        "Unsupported image",
                        JOptionPane.ERROR_MESSAGE
                );
            }
            return image;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Could not read image:\n" + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BufferedImage input = chooseImage(null, "Select Input Image");
            if (input == null) {
                return;
            }

            BufferedImage reference = chooseImage(null, "Select Reference Image");
            if (reference == null) {
                return;
            }

            ImageMorph morph = new ImageMorph(input, reference);
            JFrame frame = new JFrame("ImageMorph");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JButton restartButton = new JButton("Restart Morph");
            restartButton.addActionListener(e -> morph.startAnimation());

            JPanel controls = new JPanel();
            controls.add(restartButton);

            frame.setLayout(new BorderLayout());
            frame.add(morph, BorderLayout.CENTER);
            frame.add(controls, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}