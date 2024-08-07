/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.mycore.imagetiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.ByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 * Provides a good test case for {@link MCRImage}.
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRImageTest {
    private final Map<String, Path> pics = new LinkedHashMap<>();

    private Path tileDir;

    private FileSystem landscapeZipFS;

    private static boolean deleteDirectory(final Path path) {
        if (Files.exists(path)) {
            try (Stream<Path> pathStream = Files.walk(path)) {
                pathStream
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                //ignore
            }
        }
        return !Files.exists(path);
    }

    /**
     * Sets up test.
     * <p>
     * A list of images is initialized which provides various testcases for the tiler.
     */
    @Before
    public void setUp() throws IOException {
        Path testFilesDir = Path.of("src", "test", "resources");
        final Path landscapeZip = testFilesDir.resolve("landscape.zip");
        landscapeZipFS = FileSystems.newFileSystem(landscapeZip, this.getClass().getClassLoader());
        pics.put("small", testFilesDir.resolve("Bay_of_Noboto.jpg"));
        pics.put("stripes", testFilesDir.resolve("stripes.png"));
        pics.put("wide", testFilesDir.resolve("labirynth_panorama_010.jpg"));
        pics.put("1 pixel mega tile rest", testFilesDir.resolve("BE_0681_0397.jpg"));
        pics.put("extra small", testFilesDir.resolve("5x5.jpg"));
        pics.put("tiff 48 bit", testFilesDir.resolve("tiff48.tif"));
        pics.put("tiff 16 bit", testFilesDir.resolve("tiff16.tif"));
        pics.put("tiff ICC", testFilesDir.resolve("rgb-to-gbr.tif"));
        pics.put("exif-orientation", landscapeZipFS.getPath("landscape_2.jpg"));
        pics.put("tiff-orientation", landscapeZipFS.getPath("landscape_7.tiff"));
        tileDir = Paths.get("target/tileDir");
        //System.setProperty("java.awt.headless", "true");
    }

    /**
     * Tears down the testcase and removes temporary directories.
     */
    @After
    public void tearDown() throws IOException {
        deleteDirectory(tileDir);
        landscapeZipFS.close();
    }

    /**
     * Tests {@link MCRImage#tile()} with various images provided by {@link #setUp()}.
     * @throws Exception if tiling process fails
     */
    @Test
    public void testTiling() throws Exception {
        final Path targetDir = tileDir.getParent().resolve("test-classes/thumbs");
        if (!Files.isDirectory(targetDir)) {
            Files.createDirectory(targetDir);
        }
        for (final Map.Entry<String, Path> entry : pics.entrySet()) {
            final String derivateID = "derivateID";
            final String imagePath = "imagePath/" + entry.getValue().getFileName();
            final MCRImage image = MCRImage.getInstance(entry.getValue(), derivateID, imagePath);
            image.setTileDir(tileDir);
            final BitSet events = new BitSet(2);//pre- and post-event
            image.tile(new MCRTileEventHandler() {

                @Override
                public void preImageReaderCreated() {
                    events.flip(0);
                }

                @Override
                public void postImageReaderCreated() {
                    events.flip(1);
                }
            });
            assertTrue("preImageReaderCreated() was not called", events.get(0));
            assertTrue("postImageReaderCreated() was not called", events.get(1));
            assertTrue("Tile directory is not created.", Files.exists(tileDir));
            final Path iviewFile = MCRImage.getTiledFile(tileDir, derivateID, imagePath);
            assertTrue("IView File is not created:" + iviewFile, Files.exists(iviewFile));
            final MCRTiledPictureProps props = MCRTiledPictureProps.getInstanceFromFile(iviewFile);
            final int tilesCount;
            try (final ZipFile iviewImage = new ZipFile(iviewFile.toFile())) {
                tilesCount = iviewImage.size() - 1;
                ZipEntry imageInfoXML = iviewImage.getEntry(MCRTiledPictureProps.IMAGEINFO_XML);
                DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document imageInfo = documentBuilder.parse(iviewImage.getInputStream(imageInfoXML));
                String hAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("height"));
                String wAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("width"));
                String zAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("zoomLevel"));
                String tAttr = Objects.requireNonNull(imageInfo.getDocumentElement().getAttribute("tiles"));
                assertTrue("height must be positive: " + hAttr, Integer.parseInt(hAttr) > 0);
                assertTrue("width must be positive: " + wAttr, Integer.parseInt(wAttr) > 0);
                assertTrue("zoomLevel must be zero or positive: " + zAttr, Integer.parseInt(zAttr) >= 0);
                int iTiles = Integer.parseInt(tAttr);
                assertEquals(tilesCount, iTiles);
                ZipEntry tileEntry = new ZipEntry("0/0/0.jpg");
                try (InputStream is = iviewImage.getInputStream(tileEntry)) {
                    Path thumbnail = targetDir.resolve(entry.getKey() + "-thumb.jpg");
                    System.out.println("Writing thumbnail to " + thumbnail);
                    Files.copy(is, thumbnail,
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
            assertEquals(entry.getKey() + ": Metadata tile count does not match stored tile count.",
                props.getTilesCount(), tilesCount);
            final int x = props.width;
            final int y = props.height;
            assertEquals(entry.getKey() + ": Calculated tile count does not match stored tile count.",
                MCRImage.getTileCount(x, y), tilesCount);
        }
    }

    @Test
    public void testStripes() throws IOException {
        BufferedImage stripes = getStripesImage();
        final ImageWriter pngWriter = ImageIO.getImageWritersByMIMEType("image/png").next();
        final String stripesImagePath = "target/simple-stripes.png";
        try (FileOutputStream fout = new FileOutputStream(stripesImagePath);
            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(fout)) {
            pngWriter.setOutput(imageOutputStream);
            final IIOImage iioImage = new IIOImage(stripes, null, null);
            ImageWriteParam imageWriteParam = pngWriter.getDefaultWriteParam();
            imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            imageWriteParam.setCompressionQuality(0f);
            pngWriter.write(null, iioImage, imageWriteParam);
        }
        final Path file = Path.of(stripesImagePath);
        final String derivateID = "derivateID";
        final String imagePath = "imagePath/" + file.getFileName();
        final MCRImage image = MCRImage.getInstance(file, derivateID, imagePath);
        image.setTileDir(tileDir);
        image.tile();
        assertTrue("Tile directory is not created.", Files.exists(tileDir));
        final Path iviewFile = MCRImage.getTiledFile(tileDir, derivateID, imagePath);
        try (final ZipFile iviewImage = new ZipFile(iviewFile.toFile())) {
            ZipEntry tileEntry = new ZipEntry("0/0/0.jpg");
            try (InputStream is = iviewImage.getInputStream(tileEntry)) {
                final Path targetDir = tileDir.getParent();
                final Path target = targetDir.resolve("simple-stripes-thumb.jpg");
                System.out.println("Copy tile " + tileEntry.getName() + " to " + target);
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static BufferedImage getStripesImage() {
        BufferedImage stripes = new BufferedImage(3000, 3000, BufferedImage.TYPE_INT_RGB);
        Color top = new Color(134, 49, 68);
        Color middle = new Color(255, 255, 255);
        Color bottom = new Color(36, 52, 83);
        for (int y = 0; y < 2366; y++) {
            for (int x = 0; x < stripes.getWidth(); x++) {
                stripes.setRGB(x, y, top.getRGB());
            }
        }
        for (int y = 2366; y < 2376; y++) {
            for (int x = 0; x < stripes.getWidth(); x++) {
                stripes.setRGB(x, y, middle.getRGB());
            }
        }
        for (int y = 2376; y < stripes.getHeight(); y++) {
            for (int x = 0; x < stripes.getWidth(); x++) {
                stripes.setRGB(x, y, bottom.getRGB());
            }
        }
        return stripes;
    }

    @Test
    public void testGamma() throws IOException, InterruptedException {
        Path testFilesDir = Path.of("src", "test", "resources");
        final Path file = testFilesDir.resolve("Gray Gamma 2.2.tif").toAbsolutePath();
        final MCRImage image = MCRImage.getInstance(testFilesDir, file, tileDir);
        image.tile();
        try (FileSystem iviewFS = getFileSystem(tileDir.resolve("Gray Gamma 2.2.iview2"))) {
            Path root = iviewFS.getRootDirectories().iterator().next();
            Path thumbnailPath = root.resolve("0/0/0.jpg");
            BufferedImage thumbnail = getBufferedImage(thumbnailPath, false);
            int averageBrightness = getAverageBrightness(thumbnail);
            BufferedImage original = getBufferedImage(file, false);
            int averageBrightnessOrig = getAverageBrightness(original);
            int averageBrightnessThumb = (int) (averageBrightness
                * Math.pow(2, original.getColorModel().getPixelSize() - thumbnail.getColorModel().getPixelSize()));
            //pixel count does not match between original and thumbnail
            //also brightness resolution is not the same, average error should be below 2%
            int marginOfError = (int) (Math.pow(2, original.getColorModel().getPixelSize()) * 0.02);
            assertTrue("Brightness after gamma correction does not match.",
                areNearlyEqual(averageBrightnessOrig, averageBrightnessThumb, marginOfError));
        }
    }

    @Test
    public void testGamma50() throws IOException, InterruptedException {
        Path testFilesDir = Path.of("src", "test", "resources");
        final Path file = testFilesDir.resolve("50 Gray Gamma 2.2.tif").toAbsolutePath();
        final MCRImage image = MCRImage.getInstance(testFilesDir, file, tileDir);
        image.tile();
        try (FileSystem iviewFS = getFileSystem(tileDir.resolve("50 Gray Gamma 2.2.iview2"))) {
            Path root = iviewFS.getRootDirectories().iterator().next();
            Path thumbnailPath = root.resolve("0/0/0.jpg");
            BufferedImage thumbnail = getBufferedImage(thumbnailPath, false);
            Path topTilePath = root.resolve("5/0/0.jpg");
            BufferedImage topTile = getBufferedImage(topTilePath, false);
            BufferedImage original = getBufferedImage(file, true);
            int averageBrightnessOrig = getAverageBrightness(original);
            int averageBrightnessThumb = getAverageBrightness(thumbnail);
            int averageBrightnessTop = getAverageBrightness(topTile);
            int averageBrightnessThumbAdj = (int) (averageBrightnessThumb
                * Math.pow(2, original.getColorModel().getPixelSize() - thumbnail.getColorModel().getPixelSize()));
            assertEquals(averageBrightnessThumb,averageBrightnessTop);
            //pixel count does not match between original and thumbnail
            //also brightness resolution is not the same, average error should be below 2%
            assertEquals(((int) Math.pow(2, thumbnail.getColorModel().getPixelSize()) / 2), averageBrightnessThumb);
            //marginOfError=2 due to rounding errors in color conversion
            assertTrue(areNearlyEqual((int) (Math.pow(2, original.getColorModel().getPixelSize()) / 2),
                averageBrightnessOrig, 2));
            int marginOfError = (int) (Math.pow(2, original.getColorModel().getPixelSize()) * 0.02);
            assertTrue("Brightness after gamma correction does not match.",
                areNearlyEqual(averageBrightnessOrig, averageBrightnessThumbAdj, marginOfError));
        }
    }

    public static boolean areNearlyEqual(int value1, int value2, int marginOfError) {
        return Math.abs(value1 - value2) <= marginOfError;
    }

    private BufferedImage getBufferedImage(Path imagePath, boolean normalize) throws IOException {
        try (ByteChannel bc = Files.newByteChannel(imagePath, StandardOpenOption.READ);
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(bc)) {
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IOException("Could not read image, no image reader available");
            }
            final ImageReader reader = readers.next();
            reader.setInput(imageInputStream, false, true);
            try {
                BufferedImage image = reader.read(0);
                if (normalize) {
                    // getRGB() actually means "getSRGB()"
                    // color value:
                    // Integer.toHexString(image.getRGB(0, 0))
                    // raw value:
                    // Integer.toHexString(image.getData().getSample(0, 0, 0))

                    // Color conversion to sRGB with 48 Bit
                    // Copy Color Raster R=G=B -> Gray
                    RenderingHints hints = new RenderingHints(Map.of(
                        RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY,
                        RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE));
                    ColorConvertOp colorConvertOp = new ColorConvertOp(hints);
                    ColorSpace sRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                    ColorModel cm
                        = new ComponentColorModel(sRGB, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT);
                    WritableRaster colorRaster
                        = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, image.getWidth(),
                            image.getHeight(), 3, null);
                    BufferedImage copyColor = new BufferedImage(cm, colorRaster, cm.isAlphaPremultiplied(), null);
                    colorConvertOp.filter(image, copyColor);
                    ColorSpace sGray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                    ColorModel cmGray
                        = new ComponentColorModel(sGray, false, false, Transparency.OPAQUE,
                            image.getData().getTransferType());
                    WritableRaster rasterGray
                        = Raster.createInterleavedRaster(image.getData().getTransferType(), image.getWidth(),
                            image.getHeight(), 1, null);
                    for (int i = 0; i < image.getWidth() * image.getHeight(); i++) {
                        int colorElement = colorRaster.getDataBuffer().getElem(0, i);
                        rasterGray.getDataBuffer().setElem(i, colorElement);
                    }
                    return new BufferedImage(cmGray, rasterGray, cmGray.isAlphaPremultiplied(), null);
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    public static int getAverageBrightness(BufferedImage image) {
        return (int) IntStream
            .of(image.getSampleModel().getSamples(0, 0, image.getWidth(), image.getHeight(), 0, (int[]) null,
                image.getRaster().getDataBuffer()))
            .average()
            .orElse(0);
    }

    public static FileSystem getFileSystem(Path iviewFile) throws IOException {
        URI uri = URI.create("jar:" + iviewFile.toUri());
        try {
            return FileSystems.newFileSystem(uri, Collections.emptyMap(), MCRImageTest.class.getClassLoader());
        } catch (FileSystemAlreadyExistsException exc) {
            // block until file system is closed
            try {
                FileSystem fileSystem = FileSystems.getFileSystem(uri);
                while (fileSystem.isOpen()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                        // get out of here
                        throw new IOException(ie);
                    }
                }
            } catch (FileSystemNotFoundException fsnfe) {
                // seems closed now -> do nothing and try to return the file system again
                LogManager.getLogger().debug("Filesystem not found", fsnfe);
            }
            return getFileSystem(iviewFile);
        }
    }

    @Test
    public void testgetTiledFile() {
        String final1 = "00";
        String final2 = "01";
        String derivateID = "junit_derivate_0000" + final1 + final2;
        Path pExpected = tileDir.resolve("junit/derivate/" + final1 + "/"
            + final2 + '/' + derivateID + "/foo/bar.iview2");
        Path tiledFile = MCRImage.getTiledFile(tileDir, derivateID, "foo/bar.tif");
        assertEquals("Path to file is not es axpected.", pExpected, tiledFile);
        tiledFile = MCRImage.getTiledFile(tileDir, derivateID, "/foo/bar.tif");
        assertEquals("Path to file is not es axpected.", pExpected, tiledFile);
    }

    @Test
    public void testGetTiledFileWithoutMCR() throws IOException {
        Path firstImage = pics.values().stream().findFirst().get().toAbsolutePath();
        Path baseDir = firstImage.getParent().getParent().toAbsolutePath();
        Path targetDir = Path.of("target");
        Path imageFile = baseDir.relativize(firstImage);
        MCRImage image = MCRImage.getInstance(baseDir, imageFile, targetDir);
        Path tiledFile = MCRImage.getTiledFile(targetDir, null, imageFile.toString());
        image.tile();
        assertTrue("Tiled file is not present " + tiledFile, Files.exists(tiledFile));
    }

}
