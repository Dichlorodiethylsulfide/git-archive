package dev.gitarchive.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class VersionInfo {
    private static final String VERSION = loadVersion();

    private VersionInfo() {
    }

    public static String version() {
        return VERSION;
    }

    private static String loadVersion() {
        Properties properties = new Properties();
        try (InputStream inputStream = VersionInfo.class.getClassLoader().getResourceAsStream("git-archive.properties")) {
            if (inputStream == null) {
                return "dev";
            }
            properties.load(inputStream);
            return properties.getProperty("version", "dev").trim();
        } catch (IOException exception) {
            return "dev";
        }
    }
}
