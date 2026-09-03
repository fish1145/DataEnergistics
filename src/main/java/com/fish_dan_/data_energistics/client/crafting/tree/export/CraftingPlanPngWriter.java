package com.fish_dan_.data_energistics.client.crafting.tree.export;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Streams non-interlaced RGBA8 PNG rows on one IO worker without allocating an image or scanline.
 * The caller owns the output stream and must call {@link #finish()} before closing it. Closing this
 * writer only releases compression resources; an aborted image is never given a success trailer.
 */
final class CraftingPlanPngWriter implements AutoCloseable {

    private static final byte[] SIGNATURE = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };
    private static final byte[] IHDR = { 73, 72, 68, 82 };
    private static final byte[] IDAT = { 73, 68, 65, 84 };
    private static final byte[] IEND = { 73, 69, 78, 68 };
    private static final byte[] NO_FILTER = { 0 };
    private static final byte[] EMPTY = {};
    private static final int BYTES_PER_PIXEL = 4;

    private final DataOutputStream output;
    private final int width;
    private final int height;
    private final Deflater deflater;
    private final CRC32 crc = new CRC32();
    private final byte[] compressed = new byte[32 * 1024];
    private int compressedLength;
    private int rowPixels;
    private int completedRows;
    private boolean finished;
    private boolean closed;

    /** Starts a PNG with positive dimensions; neither construction nor close takes stream ownership. */
    CraftingPlanPngWriter(OutputStream output, int width, int height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("PNG dimensions must be positive");
        }
        this.output = new DataOutputStream(output);
        this.width = width;
        this.height = height;
        byte[] header = ByteBuffer.allocate(13)
                .putInt(width).putInt(height)
                .put((byte) 8).put((byte) 6)
                .put((byte) 0).put((byte) 0).put((byte) 0).array();
        this.output.write(SIGNATURE);
        writeChunk(IHDR, header, header.length);
        // Allocate native compression resources only after construction-time IO has succeeded.
        deflater = new Deflater(Deflater.BEST_SPEED);
    }

    /**
     * Consumes a positive number of unassociated RGBA pixels from a byte offset. Segments must arrive
     * left-to-right and top-to-bottom and must not cross a row. The array may be reused on return.
     * Invalid bounds leave the writer unchanged; an IO failure aborts it and is propagated.
     */
    void writeRowSegment(byte[] rgba, int offset, int pixelCount) throws IOException {
        ensureOpen();
        if (completedRows == height) {
            throw new IllegalStateException("All PNG pixels have already been written");
        }
        if (pixelCount <= 0 || pixelCount > width - rowPixels) {
            throw new IllegalArgumentException("PNG segment must contain pixels within the current row");
        }
        long byteCount = (long) pixelCount * BYTES_PER_PIXEL;
        Objects.checkFromIndexSize(offset, byteCount, rgba.length);
        try {
            if (rowPixels == 0) {
                writeCompressed(NO_FILTER, 0, NO_FILTER.length);
            }
            writeCompressed(rgba, offset, (int) byteCount);
            rowPixels += pixelCount;
            if (rowPixels == width) {
                rowPixels = 0;
                completedRows++;
            }
        } catch (IOException | RuntimeException exception) {
            close();
            throw exception;
        }
    }

    /**
     * Completes the zlib stream and writes IEND only after every declared pixel has arrived.
     * Repeated calls after success do nothing. Incomplete input is rejected without ending the
     * image, allowing the caller to supply the missing pixels. Does not flush or close the output.
     */
    void finish() throws IOException {
        ensureOpen();
        if (finished) {
            return;
        }
        if (completedRows != height) {
            throw new IllegalStateException("PNG image is incomplete: " + completedRows + " of " + height + " rows, " + rowPixels + " pixels in the current row");
        }
        try {
            deflater.finish();
            while (!deflater.finished()) {
                drainCompressed();
            }
            if (compressedLength > 0) {
                writeChunk(IDAT, compressed, compressedLength);
                compressedLength = 0;
            }
            writeChunk(IEND, EMPTY, 0);
            finished = true;
        } catch (IOException | RuntimeException exception) {
            close();
            throw exception;
        }
    }

    /** Releases native compression resources without completing, flushing, or closing the output. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            deflater.end();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PNG writer is closed");
        }
    }

    private void writeCompressed(byte[] bytes, int offset, int length) throws IOException {
        deflater.setInput(bytes, offset, length);
        while (!deflater.needsInput()) {
            drainCompressed();
        }
    }

    private void drainCompressed() throws IOException {
        compressedLength += deflater.deflate(compressed, compressedLength, compressed.length - compressedLength);
        if (compressedLength == compressed.length) {
            writeChunk(IDAT, compressed, compressedLength);
            compressedLength = 0;
        }
    }

    private void writeChunk(byte[] type, byte[] bytes, int length) throws IOException {
        output.writeInt(length);
        output.write(type);
        output.write(bytes, 0, length);
        crc.reset();
        crc.update(type);
        crc.update(bytes, 0, length);
        output.writeInt((int) crc.getValue());
    }
}
