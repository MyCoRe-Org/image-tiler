package org.mycore.imagetiler.internal;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.Optional;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class MCRTileDebugger {

    public static void debugImage(final String name, BufferedImage debugImage) {
        if (!Optional.ofNullable(System.getProperty(MCRTileDebugger.class.getName()))
            .map(Boolean::parseBoolean).orElse(false)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            BufferedImage image = debugImage;
            JFrame frame = new JFrame(name);

            // Get the size of the screen
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

            // Determine the new dimensions if the image is larger than the screen
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            if (imageWidth > screenSize.width || imageHeight > screenSize.height) {
                double widthScale = (double) screenSize.width / imageWidth;
                double heightScale = (double) screenSize.height / imageHeight;
                double scale = Math.min(widthScale, heightScale);
                imageWidth = (int) (imageWidth * scale);
                imageHeight = (int) (imageHeight * scale);

                Image scaledImage = image.getScaledInstance(imageWidth, imageHeight, Image.SCALE_SMOOTH);
                image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_BYTE_GRAY);
                Graphics2D g2d = image.createGraphics();
                g2d.drawImage(scaledImage, 0, 0, null);
                g2d.dispose();
            }

            JLabel imageLabel = new JLabel(new ImageIcon(image));
            frame.add(imageLabel);

            frame.pack();
            frame.setLocationRelativeTo(null); // Center the frame
            frame.setVisible(true);
        });

    }

}
