package io.helios.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigLoader {
    public static final String ENV_CONFIG = "HELIOS_CONFIG";

    private ConfigLoader() {
    }

    public static HeliosConfig load(String path) throws IOException {
        HeliosConfig cfg = HeliosConfig.defaults();
        String resolved = resolvePath(path);
        if (resolved != null && !resolved.isBlank()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
            mapper.readerForUpdating(cfg).readValue(Path.of(resolved).toFile());
        }
        applyEnv(cfg);
        cfg.validate();
        return cfg;
    }

    public static String resolvePath(String path) {
        if (path != null && !path.isBlank()) {
            return path;
        }
        String env = System.getenv(ENV_CONFIG);
        if (env != null && !env.isBlank()) {
            return env;
        }
        if (Files.exists(Path.of("config.yaml"))) {
            return "config.yaml";
        }
        return "";
    }

    static void applyEnv(HeliosConfig cfg) {
        set(cfg.server()::setHost, "HELIOS_SERVER_HOST");
        setInt(cfg.server()::setPort, "HELIOS_SERVER_PORT");
        setDuration(cfg.server()::setReadTimeout, "HELIOS_SERVER_READ_TIMEOUT");
        setDuration(cfg.server()::setWriteTimeout, "HELIOS_SERVER_WRITE_TIMEOUT");
        set(cfg.database()::setUrl, "HELIOS_DATABASE_URL");
        set(cfg.logging()::setLevel, "HELIOS_LOGGING_LEVEL");
        set(cfg.logging()::setFormat, "HELIOS_LOGGING_FORMAT");
        setDouble(cfg.index().bm25()::setK1, "HELIOS_INDEX_BM25_K1");
        setDouble(cfg.index().bm25()::setB, "HELIOS_INDEX_BM25_B");
        set(cfg.index()::setSnapshotPath, "HELIOS_INDEX_SNAPSHOT_PATH");
        setInt(cfg.jobs()::setWorkerCount, "HELIOS_JOBS_WORKER_COUNT");
        setBool(cfg.cluster()::setEnabled, "HELIOS_CLUSTER_ENABLED");
        String shards = System.getenv("HELIOS_CLUSTER_SHARDS");
        if (shards != null && !shards.isBlank()) {
            cfg.cluster().setShards(parseShards(shards));
        }
        setDuration(cfg.cluster()::setQueryTimeout, "HELIOS_CLUSTER_QUERY_TIMEOUT");
        setInt(cfg.cluster()::setVnodeCount, "HELIOS_CLUSTER_VNODE_COUNT");
        setInt(cfg.cluster()::setReplicationFactor, "HELIOS_CLUSTER_REPLICATION_FACTOR");
        setInt(cfg.cluster()::setRetryMax, "HELIOS_CLUSTER_RETRY_MAX");
        setDuration(cfg.cluster()::setRetryBase, "HELIOS_CLUSTER_RETRY_BASE");
        setInt(cfg.cluster()::setHealthFailAfter, "HELIOS_CLUSTER_HEALTH_FAIL_AFTER");
        set(cfg.shard()::setId, "HELIOS_SHARD_ID");
        set(cfg.shard()::setGrpcHost, "HELIOS_SHARD_GRPC_HOST");
        set(cfg.search()::setDefaultMode, "HELIOS_SEARCH_DEFAULT_MODE");
        setDouble(cfg.search()::setAlpha, "HELIOS_SEARCH_ALPHA");
        setInt(cfg.search()::setEmbedDim, "HELIOS_SEARCH_EMBED_DIM");
        setInt(cfg.search()::setSemanticCandidates, "HELIOS_SEARCH_SEMANTIC_CANDIDATES");
        setInt(cfg.shard()::setGrpcPort, "HELIOS_SHARD_GRPC_PORT");
        setBool(cfg.cache()::setEnabled, "HELIOS_CACHE_ENABLED");
        set(cfg.cache()::setBackend, "HELIOS_CACHE_BACKEND");
        setDuration(cfg.cache()::setTtl, "HELIOS_CACHE_TTL");
        set(cfg.cache()::setRedisUrl, "HELIOS_CACHE_REDIS_URL");
        set(cfg.observability()::setOtlpEndpoint, "HELIOS_OTLP_ENDPOINT");
        set(cfg.shard()::setMetricsHost, "HELIOS_SHARD_METRICS_HOST");
        setInt(cfg.shard()::setMetricsPort, "HELIOS_SHARD_METRICS_PORT");
    }

    public static List<HeliosConfig.ClusterNode> parseShards(String raw) {
        List<HeliosConfig.ClusterNode> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            part = part.trim();
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String id = part.substring(0, eq).trim();
            String addr = part.substring(eq + 1).trim();
            if (!id.isEmpty() && !addr.isEmpty()) {
                out.add(new HeliosConfig.ClusterNode(id, addr));
            }
        }
        return out;
    }

    public static String toJdbcUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.startsWith("jdbc:")) {
            return stripUserinfoFromJdbc(url);
        }
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            String rest = url.replaceFirst("^postgres(ql)?://", "");
            int at = rest.lastIndexOf('@');
            if (at >= 0) {
                rest = rest.substring(at + 1);
            }
            return "jdbc:postgresql://" + rest;
        }
        return url;
    }

    private static String stripUserinfoFromJdbc(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        String rest = url.substring(scheme + 3);
        int at = rest.lastIndexOf('@');
        if (at < 0) {
            return url;
        }
        return url.substring(0, scheme + 3) + rest.substring(at + 1);
    }

    public static String[] userPassword(String url) {
        String raw = url == null ? "" : url;
        int scheme = raw.indexOf("://");
        if (scheme < 0) {
            return new String[]{"helios", "helios"};
        }
        String rest = raw.substring(scheme + 3);
        int at = rest.lastIndexOf('@');
        if (at < 0) {
            return new String[]{"helios", "helios"};
        }
        String userinfo = rest.substring(0, at);
        int colon = userinfo.indexOf(':');
        if (colon < 0) {
            return new String[]{userinfo, ""};
        }
        return new String[]{userinfo.substring(0, colon), userinfo.substring(colon + 1)};
    }

    private static void set(java.util.function.Consumer<String> setter, String env) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) {
            setter.accept(v);
        }
    }

    private static void setInt(java.util.function.IntConsumer setter, String env) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) {
            setter.accept(Integer.parseInt(v));
        }
    }

    private static void setDouble(java.util.function.DoubleConsumer setter, String env) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) {
            setter.accept(Double.parseDouble(v));
        }
    }

    private static void setBool(java.util.function.Consumer<Boolean> setter, String env) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) {
            setter.accept("1".equals(v) || "true".equalsIgnoreCase(v));
        }
    }

    private static void setDuration(java.util.function.Consumer<DurationSetting> setter, String env) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) {
            setter.accept(DurationSetting.parse(v));
        }
    }
}
