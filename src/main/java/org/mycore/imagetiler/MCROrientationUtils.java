package org.mycore.imagetiler;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;

/**
 * A utility class for handling image orientations.
 */
public class MCROrientationUtils {

    /**
     * Converts the logical image area to a physical image area based on the given
     * logical image width and height and orientation.
     *
     * @param logicalWidth  The logical width of the image.
     * @param logicalHeight The logical height of the image.
     * @param logical       The logical image area to be converted.
     * @param orientation   The orientation of the image.
     * @return The physical image area as a rectangle.
     */
    public static Rectangle toPhysical(int logicalWidth, int logicalHeight, Rectangle logical,
        MCROrientation orientation) {
        if (orientation == MCROrientation.TOP_LEFT) {
            return logical;
        }
        LogManager.getLogger().debug(logicalWidth + "x" + logicalHeight + ": " + logical);
        int newX = logical.x;
        int newY = logical.y;
        int newWidth = logical.width;
        int newHeight = logical.height;
        if (orientation.getRotationDegree() % 180 != 0) {
            newHeight = logical.width;
            newWidth = logical.height;
        }
        if (orientation.isMirrored()) {
            LogManager.getLogger().debug("is mirrored");
            newX = logicalWidth - (logical.x + logical.width);
        }
        LogManager.getLogger().debug("{} degrees", orientation.getRotationDegree());
        switch (orientation.getRotationDegree()) {
            case 0 -> {
            }
            case 90 -> {
                newY = logicalWidth - logical.width - newX;
                newX = logical.y;
            }
            case 180 -> {
                newX = logicalWidth - logical.width - newX;
                newY = logicalHeight - logical.height - logical.y;
            }
            case 270 -> {
                newY = newX;
                newX = logicalHeight - logical.height - logical.y;
            }
            default -> throw new IllegalStateException("Unsupported rotation: " + orientation.getRotationDegree());
        }
        Rectangle physicalBounds = new Rectangle(newX, newY, newWidth, newHeight);
        LogManager.getLogger().debug(physicalBounds);
        return physicalBounds;
    }

    /**
     * Converts the physical image area to a logical image area based on the given
     * orientation, width, and height.
     *
     * @param orientation The orientation of the image.
     * @param width       The width of the physical image.
     * @param height      The height of the physical image.
     * @return An Optional containing the AffineTransform to transform the physical image
     * area to the logical image area. Returns an empty Optional if no transformation is needed.
     * @throws IllegalStateException If the exif orientation is not supported.
     */
    public static Optional<AffineTransform> getPhysicalToLogicalTransformation(MCROrientation orientation, int width,
        int height) {
        AffineTransform affineTransform = new AffineTransform();

        switch (orientation) {
            case TOP_LEFT -> {
                return Optional.empty();
            }
            case TOP_RIGHT -> {
                affineTransform.scale(-1.0, 1.0);
                affineTransform.translate(-width, 0);
            }
            case BOTTOM_RIGHT -> {
                affineTransform.translate(width, height);
                affineTransform.rotate(Math.PI);
            }
            case BOTTOM_LEFT -> {
                affineTransform.scale(1.0, -1.0);
                affineTransform.translate(0, -height);
            }
            case LEFT_TOP -> {
                affineTransform.rotate(Math.toRadians(-90));
                affineTransform.scale(-1.0, 1.0);
            }
            case RIGHT_TOP -> {
                affineTransform.translate(height, 0);
                affineTransform.rotate(Math.toRadians(90));
            }
            case RIGHT_BOTTOM -> {
                affineTransform.scale(-1.0, 1.0);
                affineTransform.translate(-height, 0);
                affineTransform.translate(0, width);
                affineTransform.rotate(Math.toRadians(270));
            }
            case LEFT_BOTTOM -> {
                affineTransform.translate(0, width);
                affineTransform.rotate(Math.toRadians(270));
            }
            default ->
                throw new IllegalStateException("Unsupported exif orientation: " + orientation.getExifOrientation());
        }
        return Optional.of(affineTransform);
    }
}
