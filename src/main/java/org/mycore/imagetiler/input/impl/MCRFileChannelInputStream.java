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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import javax.imageio.stream.ImageInputStreamImpl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Cannot copy FileChannel!")
public final class MCRFileChannelInputStream extends ImageInputStreamImpl {
    /**
     * Size of the file window held in memory at a time.
     */
    private static final int WINDOW_SIZE = 64 * 1024;

    private FileChannel input;

    private final long size;

    private long windowPos, windowUpperBound;

    private ByteBuffer window;

    public MCRFileChannelInputStream(FileChannel input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input is null");
        }
        if (!input.isOpen()) {
            throw new IllegalArgumentException("image FileChannel is not open");
        }
        this.input = input;
        long inputPos = input.position();
        this.streamPos = this.flushedPos = inputPos;
        this.size = input.size();
        this.window = ByteBuffer.allocate(WINDOW_SIZE);
        fill(inputPos, 0);
    }

    /**
     * Loads a window starting at <code>pos</code> that holds at least <code>minLength</code> bytes.
     * <p>
     * This used to be a {@link FileChannel#map(FileChannel.MapMode, long, long)} of the whole file, which was
     * faster to set up but could not be released: a mapping only goes away once its buffer is unreachable
     * <em>and</em> the garbage collector gets around to it. Mapped memory is not on the heap, so heap pressure
     * never triggers that collection - a few concurrently tiled large images could pin several GB that no heap
     * profiler would show. Copying into a fixed window keeps the footprint bounded and deterministic.
     */
    private void fill(long pos, int minLength) throws IOException {
        //align windows to fixed boundaries so that scattered reads within one region keep hitting the same window
        long start = pos - pos % WINDOW_SIZE;
        int capacity = Math.max(WINDOW_SIZE, (int) (pos - start) + minLength);
        if (capacity > window.capacity()) {
            window = ByteBuffer.allocate(capacity);
        }
        window.clear();
        window.limit((int) Math.min(window.capacity(), Math.max(0, size - start)));
        long readPos = start;
        while (window.hasRemaining() && input.read(window, readPos) > 0) {
            readPos = start + window.position();
        }
        this.windowPos = start;
        this.windowUpperBound = start + window.position();
        window.flip();
        window.position((int) Math.min(pos - start, window.limit()));
        window.order(super.getByteOrder());
    }

    @Override
    public int read() throws IOException {
        bitOffset = 0;
        prepareRead(1);

        if (window.remaining() < 1) {
            return -1; //EOF
        }

        int value = window.get() & 0xff; //convert signed byte to int (0-255)
        streamPos++;
        return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException(
                "off=" + off + " and len=" + len + " do not work with array length: " + b.length);
        }
        if (len == 0) {
            return 0;
        }

        bitOffset = 0;
        prepareRead(len);
        int bytesLeft = window.remaining();

        if (bytesLeft < 1) {
            return -1; //EOF
        } else if (len > bytesLeft) {
            len = bytesLeft; //do not read over end of file
        }

        window.get(b, off, len);
        streamPos += len;
        return len;
    }

    /**
     * Checks if boundaries are sufficient to read len bytes.
     * Will move the window if needed.
     */
    private void prepareRead(int len) throws IOException {
        checkClosed();
        if (streamPos >= windowPos && streamPos + len <= windowUpperBound) {
            window.position((int) (streamPos - windowPos));
        } else if (streamPos >= windowPos && windowUpperBound == size) {
            //window already reaches the end of the file, there is nothing left to load
            window.position((int) Math.min(streamPos - windowPos, window.limit()));
        } else {
            fill(streamPos, len);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        input = null;
        window = null;
    }

    @Override
    public void setByteOrder(ByteOrder byteOrder) {
        super.setByteOrder(byteOrder);
        if (window != null) {
            window.order(byteOrder);
        }
    }

    @Override
    public long length() {
        return size;
    }

    //--overwrite methods for better performance

    @Override
    public void seek(long pos) throws IOException {
        super.seek(pos);
        if (pos >= windowPos && pos < windowUpperBound) {
            //Seek within buffer -> change position
            window.position((int) (pos - windowPos));
        } else {
            //Seek outside buffer -> move the window
            fill(pos, 0);
        }
    }

    @Override
    public void readFully(short[] s, int off, int len) throws IOException {
        readFully(off, len, s.length, Short.BYTES, () -> window.asShortBuffer().get(s, off, len));
    }

    @Override
    public void readFully(char[] c, int off, int len) throws IOException {
        readFully(off, len, c.length, Character.BYTES, () -> window.asCharBuffer().get(c, off, len));
    }

    @Override
    public void readFully(int[] i, int off, int len) throws IOException {
        readFully(off, len, i.length, Integer.BYTES, () -> window.asIntBuffer().get(i, off, len));
    }

    @Override
    public void readFully(long[] l, int off, int len) throws IOException {
        readFully(off, len, l.length, Long.BYTES, () -> window.asLongBuffer().get(l, off, len));
    }

    @Override
    public void readFully(float[] f, int off, int len) throws IOException {
        readFully(off, len, f.length, Float.BYTES, () -> window.asFloatBuffer().get(f, off, len));
    }

    @Override
    public void readFully(double[] d, int off, int len) throws IOException {
        readFully(off, len, d.length, Double.BYTES, () -> window.asDoubleBuffer().get(d, off, len));
    }

    private void readFully(int off, int len, int arrLength, int byteSize, ReadOperation op) throws IOException {
        if (off < 0 || len < 0 || off + len > arrLength) {
            throw new IndexOutOfBoundsException(
                "off=" + off + " and len=" + len + " do not work with array length: " + arrLength);
        } else if (len == 0) {
            return;
        }
        int byteLen = byteSize * len;
        prepareRead(byteLen);
        if (window.remaining() < byteLen) {
            throw new EOFException();
        }
        op.read();
        seek(streamPos + byteLen); //update position
    }

    private interface ReadOperation {
        void read() throws IOException;
    }
}
