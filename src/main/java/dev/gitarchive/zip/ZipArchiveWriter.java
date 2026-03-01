package dev.gitarchive.zip;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.utils.IOUtils;

public final class ZipArchiveWriter implements Closeable {
    private final ZipArchiveOutputStream outputStream;

    public ZipArchiveWriter(Path outputPath) throws IOException {
        this.outputStream = new ZipArchiveOutputStream(Files.newOutputStream(outputPath));
        this.outputStream.setEncoding(StandardCharsets.UTF_8.name());
        this.outputStream.setUseZip64(Zip64Mode.AsNeeded);
    }

    public void writeFile(String entryName, InputStreamSupplier inputStreamSupplier, long size, int permissions, Instant modifiedAt)
            throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(normalize(entryName));
        entry.setMethod(ZipArchiveEntry.DEFLATED);
        entry.setSize(size);
        entry.setTime(modifiedAt.toEpochMilli());
        entry.setUnixMode(UnixStat.FILE_FLAG | (permissions & 0777));
        outputStream.putArchiveEntry(entry);
        try (InputStream inputStream = inputStreamSupplier.openStream()) {
            IOUtils.copy(inputStream, outputStream);
        }
        outputStream.closeArchiveEntry();
    }

    public void writeTextFile(String entryName, String content, int permissions, Instant modifiedAt) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        writeFile(entryName, () -> new ByteArrayInputStream(bytes), bytes.length, permissions, modifiedAt);
    }

    public void writeSymlink(String entryName, String target, int permissions, Instant modifiedAt) throws IOException {
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        ZipArchiveEntry entry = new ZipArchiveEntry(normalize(entryName));
        entry.setMethod(ZipArchiveEntry.DEFLATED);
        entry.setSize(bytes.length);
        entry.setTime(modifiedAt.toEpochMilli());
        entry.setUnixMode(UnixStat.LINK_FLAG | (permissions & 0777));
        outputStream.putArchiveEntry(entry);
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            IOUtils.copy(inputStream, outputStream);
        }
        outputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        outputStream.finish();
        outputStream.close();
    }

    private static String normalize(String entryName) {
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("ZIP entry name must not be blank.");
        }
        return normalized;
    }

    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream openStream() throws IOException;
    }
}
