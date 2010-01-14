package org.mycore.services.iview2;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import junit.framework.TestCase;

import org.apache.commons.io.FilenameUtils;

public class MCRImageTest extends TestCase {
    private HashMap<String, String> pics = new HashMap<String, String>();

    File tileDir;

    @Override
    protected void setUp() throws Exception {
        pics.put("small", "src/test/resources/Bay_of_Noboto.jpg");
        pics.put("wide", "src/test/resources/labirynth_panorama_010.jpg");
        tileDir = new File("target/tileDir");
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        deleteDirectory(tileDir);
    }

    private static boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (path.delete());
    }

    public void testTiling() throws Exception {
        for (Map.Entry<String, String> entry : pics.entrySet()) {
            File file = new File(entry.getValue());
            String derivateID = "derivateID";
            String imagePath = "imagePath/" + FilenameUtils.getName(entry.getValue());
            MCRImage image = new MCRMemSaveImage(file, derivateID, imagePath);
            image.setTileDir(tileDir);
            image.tile();
            assertTrue("Tile directory is not created.", tileDir.exists());
            File iviewFile = MCRImage.getTiledFile(tileDir, derivateID, imagePath);
            assertTrue("IView File is not created:" + iviewFile.getAbsolutePath(), iviewFile.exists());
            MCRTiledPictureProps props = MCRTiledPictureProps.getInstance(iviewFile);
            ZipFile iviewImage = new ZipFile(iviewFile);
            int tilesCount = iviewImage.size() - 1;
            assertEquals(entry.getKey() + ": Metadata tile count does not match stored tile count.", props.countTiles, tilesCount);
            int x = props.width;
            int y = props.height;
            int tiles = 1;
            while (x >= MCRImage.TILE_SIZE || y >= MCRImage.TILE_SIZE) {
                tiles += Math.ceil(x / (double) MCRImage.TILE_SIZE) * Math.ceil(y / (double) MCRImage.TILE_SIZE);
                x = (int) Math.ceil(x / 2d);
                y = (int) Math.ceil(y / 2d);
            }
            assertEquals(entry.getKey() + ": Calculated tile count does not match stored tile count.", tiles, tilesCount);
        }
    }
}
