package org.mycore.imagetiler;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MCRMCROrientationUtilsTest {
    private final Rectangle logical;

    private final Rectangle physical;

    private final MCROrientation orientation;

    final int physicalWidth;

    final int physicalHeight;

    @Parameterized.Parameters(name = "{index}: Exif orientation {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            //The 0th row is at the visual top of the image, and the 0th column is the visual left-hand side.
            { MCROrientation.fromExifOrientation(1),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(2, 1, 6, 4), 9, 7 },
            //The 0th row is at the visual top of the image, and the 0th column is the visual right-hand side.
            { MCROrientation.fromExifOrientation(2),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(1, 1, 6, 4), 9, 7 },
            //The 0th row is at the visual bottom of the image, and the 0th column is the visual right-hand side
            { MCROrientation.fromExifOrientation(3),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(1, 2, 6, 4), 9, 7 },
            //The 0th row is at the visual bottom of the image, and the 0th column is the visual left-hand side.
            { MCROrientation.fromExifOrientation(4),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(2, 2, 6, 4), 9, 7 },
            //The 0th row is the visual left-hand side of the image, and the 0th column is the visual top.
            { MCROrientation.fromExifOrientation(5),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(1, 2, 4, 6), 9, 7 },
            //The 0th row is the visual right-hand side of the image, and the 0th column is the visual top.
            { MCROrientation.fromExifOrientation(6),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(1, 1, 4, 6), 9, 7 },
            //The 0th row is the visual right-hand side of the image, and the 0th column is the visual bottom.
            { MCROrientation.fromExifOrientation(7),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(2, 1, 4, 6), 9, 7 },
            //The 0th row is the visual left-hand side of the image, and the 0th column is the visual bottom.
            { MCROrientation.fromExifOrientation(8),
                new Rectangle(2, 1, 6, 4),
                new Rectangle(2, 2, 4, 6), 9, 7 }
        });
    }

    public MCRMCROrientationUtilsTest(MCROrientation orientation, Rectangle logical, Rectangle physical, int pw,
        int ph) {
        this.logical = logical;
        this.physical = physical;
        this.orientation = orientation;
        physicalWidth = pw;
        physicalHeight = ph;
    }

    @Test
    public void testExifX() {
        Rectangle physical = MCROrientationUtils.toPhysical(physicalWidth, physicalHeight, logical, orientation);
        Assert.assertEquals("x coordinate does not match for orientation " + orientation.getExifOrientation(),
            this.physical.x, physical.x);
    }

    @Test
    public void testExifY() {
        Rectangle physical = MCROrientationUtils.toPhysical(physicalWidth, physicalHeight, logical, orientation);
        Assert.assertEquals("y coordinate does not match for orientation " + orientation.getExifOrientation(),
            this.physical.y, physical.y);
    }

    @Test
    public void testExifWidth() {
        Rectangle physical = MCROrientationUtils.toPhysical(physicalWidth, physicalHeight, logical, orientation);
        Assert.assertEquals("area width does not match for orientation " + orientation.getExifOrientation(),
            this.physical.width, physical.width);
    }

    @Test
    public void testExifHeight() {
        Rectangle physical = MCROrientationUtils.toPhysical(physicalWidth, physicalHeight, logical, orientation);
        Assert.assertEquals("area height does not match for orientation " + orientation.getExifOrientation(),
            this.physical.height, physical.height);
    }
}
