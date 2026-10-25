package io.helios.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class CrawlerSettings {
    @JsonProperty("allowed_hosts")
    private List<String> allowedHosts = new ArrayList<>();
    @JsonProperty("clone_timeout")
    private DurationSetting cloneTimeout = DurationSetting.of(java.time.Duration.ofSeconds(120));
    @JsonProperty("max_file_size_bytes")
    private long maxFileSizeBytes = 1 << 20;
    @JsonProperty("max_repo_size_bytes")
    private long maxRepoSizeBytes = 100L << 20;
    @JsonProperty("max_files")
    private int maxFiles = 5000;
    @JsonProperty("temp_dir")
    private String tempDir = "";
    @JsonProperty("ignored_directories")
    private List<String> ignoredDirectories = new ArrayList<>();
    @JsonProperty("supported_extensions")
    private List<String> supportedExtensions = new ArrayList<>();

    public List<String> allowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? new ArrayList<>() : allowedHosts;
    }

    public DurationSetting cloneTimeout() {
        return cloneTimeout;
    }

    public void setCloneTimeout(DurationSetting cloneTimeout) {
        this.cloneTimeout = cloneTimeout;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public long maxRepoSizeBytes() {
        return maxRepoSizeBytes;
    }

    public void setMaxRepoSizeBytes(long maxRepoSizeBytes) {
        this.maxRepoSizeBytes = maxRepoSizeBytes;
    }

    public int maxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public String tempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public List<String> ignoredDirectories() {
        return ignoredDirectories;
    }

    public void setIgnoredDirectories(List<String> ignoredDirectories) {
        this.ignoredDirectories = ignoredDirectories == null ? new ArrayList<>() : ignoredDirectories;
    }

    public List<String> supportedExtensions() {
        return supportedExtensions;
    }

    public void setSupportedExtensions(List<String> supportedExtensions) {
        this.supportedExtensions = supportedExtensions == null ? new ArrayList<>() : supportedExtensions;
    }
}
