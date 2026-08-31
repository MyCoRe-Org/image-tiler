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

package org.mycore.imagetiler.input.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Verifies that reads are served correctly no matter where they fall relative to the internal window, which is
 * smaller than the files used here and therefore has to be moved around repeatedly.
 */
public class MCRFileChannelInputStreamTest {

    /**
     * Bigger than the window inside {@link MCRFileChannelInputStream}, so that all tests cross window boundaries.
     */
    private static final int FILE_SIZE = 200 * 1024;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private byte[] content;

    private Path writeTestFile() throws IOException {
        content = new byte[FILE_SIZE];
        new Random(4711).nextBytes(content);
        Path file = folder.newFile("content.bin").toPath();
        Files.write(file, content);
        return file;
    }

    private MCRFileChannelInputStream open() throws IOException {
        return new MCRFileChannelInputStream(FileChannel.open(writeTestFile(), StandardOpenOption.READ));
    }

    @Test
    public void readsWholeFileSequentially() throws IOException {
        try (MCRFileChannelInputStream stream = open()) {
            assertEquals(FILE_SIZE, stream.length());
            byte[] read = new byte[FILE_SIZE];
            stream.readFully(read);
            assertArrayEquals(content, read);
            assertEquals(-1, stream.read());
        }
    }

    @Test
    public void readsSingleBytesAcrossWindowBorders() throws IOException {
        try (MCRFileChannelInputStream stream = open()) {
            for (int pos : new int[] { 0, 65_535, 65_536, 65_537, 131_071, 131_072, FILE_SIZE - 1 }) {
                stream.seek(pos);
                assertEquals("byte at " + pos, content[pos] & 0xff, stream.read());
                assertEquals("position after reading at " + pos, pos + 1L, stream.getStreamPosition());
            }
        }
    }

    @Test
    public void readsByteBlockSpanningManyWindows() throws IOException {
        try (MCRFileChannelInputStream stream = open()) {
            int offset = 1234;
            int length = 150 * 1024;
            stream.seek(offset);
            byte[] read = new byte[length];
            stream.readFully(read);
            byte[] expected = new byte[length];
            System.arraycopy(content, offset, expected, 0, length);
            assertArrayEquals(expected, read);
        }
    }

    /**
     * The typed <code>readFully</code> overrides have to hold the whole array in the window at once, unlike
     * {@link javax.imageio.stream.ImageInputStreamImpl#readFully(byte[], int, int)} which loops over
     * {@link MCRFileChannelInputStream#read(byte[], int, int)}. This is the only path that grows the window
     * beyond its default size.
     */
    @Test
    public void readsTypedArrayLargerThanTheWindow() throws IOException {
        try (MCRFileChannelInputStream stream = open()) {
            int offset = 1000;
            int count = (FILE_SIZE - offset) / Short.BYTES;
            stream.seek(offset);
            short[] read = new short[count];
            stream.readFully(read, 0, count);
            short[] expected = new short[count];
            ByteBuffer.wrap(content, offset, count * Short.BYTES).asShortBuffer().get(expected);
            assertArrayEquals(expected, read);
            assertEquals(offset + (long) count * Short.BYTES, stream.getStreamPosition());
        }
    }

    @Test
    public void readsTypedValuesAfterSeekingBackAndForth() throws IOException {
        try (MCRFileChannelInputStream stream = open()) {
            stream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
            //once forward, then the same positions backwards, so windows have to be reloaded in both directions
            int[] positions = { 10, 70_000, 140_000, 70_000, 10 };
            for (int pos : positions) {
                stream.seek(pos);
                short[] read = new short[8];
                stream.readFully(read, 0, read.length);
                for (int i = 0; i < read.length; i++) {
                    int lo = content[pos + 2 * i] & 0xff;
                    int hi = content[pos + 2 * i + 1] & 0xff;
                    assertEquals("short " + i + " at " + pos, (short) (hi << 8 | lo), read[i]);
                }
            }
        }
    }

    @Test
    public void reportsEndOfFile() throws IOException {
        try (MCRFileChannelInputStream stream = open()) {
            stream.seek(FILE_SIZE);
            assertEquals(-1, stream.read());
            assertEquals(-1, stream.read(new byte[16], 0, 16));
            //repeated probing past the end must stay consistent
            assertEquals(FILE_SIZE, stream.getStreamPosition());
        }
    }

    @Test(expected = EOFException.class)
    public void failsOnTypedReadPastEndOfFile() throws IOException {
        try (MCRFileChannelInputStream stream = open()) {
            stream.seek(FILE_SIZE - 3);
            stream.readFully(new short[8], 0, 8);
        }
    }

    @Test(expected = IOException.class)
    public void failsAfterClose() throws IOException {
        MCRFileChannelInputStream stream = open();
        stream.close();
        stream.read();
    }

    @Test
    public void reportsLengthAfterClose() throws IOException {
        MCRFileChannelInputStream stream = open();
        stream.close();
        assertEquals(FILE_SIZE, stream.length());
    }
}
