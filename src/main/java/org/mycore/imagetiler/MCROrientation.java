package org.mycore.imagetiler;

/**
 * An enumeration representing different orientations.
 * <p>
 * Each orientation has an associated Exif orientation value, rotation degree, and a flag indicating whether the image is mirrored or not.
 */
public enum MCROrientation {

    /**
     * Normal case with an image not mirrored and not rotated.
     * The 0th row is at the visual top of the image, and the 0th column is the visual left-hand side.
     */
    TOP_LEFT(1, 0, false),
    /**
     * Image is mirrored horizontally.
     * The 0th row is at the visual top of the image, and the 0th column is the visual right-hand side.
     */
    TOP_RIGHT(2, 0, true),
    /**
     * Image is rotated by 180 degrees.
     * The 0th row is at the visual bottom of the image, and the 0th column is the visual right-hand side.
     */
    BOTTOM_RIGHT(3, 180, false),
    /**
     * Image is mirrored horizontally and rotated by 180 degrees.
     * The 0th row is at the visual bottom of the image, and the 0th column is the visual left-hand side.
     */
    BOTTOM_LEFT(4, 180, true),
    /**
     * Image is mirrored horizontally and clock wise rotated by 90 degrees.
     * The 0th row is the visual left-hand side of the image, and the 0th column is the visual top.
     */
    LEFT_TOP(5, 90, true),
    /**
     * Image is clock wise rotated by 90 degrees.
     * The 0th row is the visual right-hand side of the image, and the 0th column is the visual top.
     */
    RIGHT_TOP(6, 90, false),
    /**
     * Image is mirrored horizontally and is clock wise rotated by 270 degrees.
     * The 0th row is the visual right-hand side of the image, and the 0th column is the visual bottom.
     */
    RIGHT_BOTTOM(7, 270, true),
    /**
     * Image is clock wise rotated by 270 degrees.
     * The 0th row is the visual left-hand side of the image, and the 0th column is the visual bottom.
     */
    LEFT_BOTTOM(8, 270, false);

    private final int exifOrientation;

    private final int rotationDegree;

    private final boolean mirrored;

    MCROrientation(int exifValue, int deg, boolean mirrored) {
        this.exifOrientation = exifValue;
        this.rotationDegree = deg;
        this.mirrored = mirrored;
    }

    /**
     * Retrieves the rotation degree.
     *
     * @return The rotation degree value.
     */
    public int getRotationDegree() {
        return rotationDegree;
    }

    /**
     * Checks if the object is mirrored.
     *
     * @return true if the object is mirrored, false otherwise.
     */
    public boolean isMirrored() {
        return mirrored;
    }

    /**
     * Gets the Exif orientation of the object.
     *
     * @return The Exif orientation of the object as an integer.
     */
    public int getExifOrientation() {
        return exifOrientation;
    }

    /**
     * Converts an Exif orientation value to an Orientation object.
     *
     * @param exifOrientation The Exif orientation value to convert.
     * @return The corresponding Orientation object.
     * @throws IllegalArgumentException if the exifOrientation value is not between 1 and 8 (inclusive).
     * @throws IllegalStateException    if the Orientation values are not in order.
     */
    public static MCROrientation fromExifOrientation(int exifOrientation) {
        if (exifOrientation < 1 || exifOrientation > 8) {
            throw new IllegalArgumentException("Invalid value " + exifOrientation);
        }
        MCROrientation value = values()[exifOrientation - 1];
        if (value.exifOrientation != exifOrientation) {
            throw new IllegalStateException("Values are not in order");
        }
        return value;
    }

    @Override
    public String toString() {
        return name() + "(" + exifOrientation + ")";
    }
}
