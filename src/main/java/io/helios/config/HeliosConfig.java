package io.helios.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.helios.tokenizer.StopWords;
import io.helios.tokenizer.TokenizerOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HeliosConfig {
    private ServerSettings server = new ServerSettings();
    private DatabaseSettings database = new DatabaseSettings();
    private LoggingSettings logging = new LoggingSettings();
    private IndexSettings index = new IndexSettings();
    private CrawlerSettings crawler = new CrawlerSettings();
    private JobsSettings jobs = new JobsSettings();
    private ClusterSettings cluster = new ClusterSettings();
    private ShardSettings shard = new ShardSettings();
    private SearchSettings search = new SearchSettings();
    private CacheSettings cache = new CacheSettings();
    private ObservabilitySettings observability = new ObservabilitySettings();

    public static HeliosConfig defaults() {
        HeliosConfig cfg = new HeliosConfig();
        cfg.crawler.setAllowedHosts(new ArrayList<>(List.of("github.com")));
        cfg.crawler.setIgnoredDirectories(new ArrayList<>(List.of(
                ".git", "node_modules", "vendor", "dist", "build", "target",
                "coverage", "__pycache__", ".idea", ".vscode"
        )));
        cfg.crawler.setSupportedExtensions(new ArrayList<>(List.of(
                ".go", ".py", ".java", ".js", ".ts", ".tsx", ".cpp", ".h", ".md", ".txt"
        )));
        cfg.index.tokenizer().setStopWords(new ArrayList<>(StopWords.defaultStopWords()));
        return cfg;
    }

    public ServerSettings server() {
        return server;
    }

    public void setServer(ServerSettings server) {
        this.server = server;
    }

    public DatabaseSettings database() {
        return database;
    }

    public void setDatabase(DatabaseSettings database) {
        this.database = database;
    }

    public LoggingSettings logging() {
        return logging;
    }

    public void setLogging(LoggingSettings logging) {
        this.logging = logging;
    }

    public IndexSettings index() {
        return index;
    }

    public void setIndex(IndexSettings index) {
        this.index = index;
    }

    public CrawlerSettings crawler() {
        return crawler;
    }

    public void setCrawler(CrawlerSettings crawler) {
        this.crawler = crawler;
    }

    public JobsSettings jobs() {
        return jobs;
    }

    public void setJobs(JobsSettings jobs) {
        this.jobs = jobs;
    }

    public ClusterSettings cluster() {
        return cluster;
    }

    public void setCluster(ClusterSettings cluster) {
        this.cluster = cluster;
    }

    public ShardSettings shard() {
        return shard;
    }

    public void setShard(ShardSettings shard) {
        this.shard = shard;
    }

    public SearchSettings search() {
        return search;
    }

    public void setSearch(SearchSettings search) {
        this.search = search;
    }

    public CacheSettings cache() {
        return cache;
    }

    public void setCache(CacheSettings cache) {
        this.cache = cache;
    }

    public ObservabilitySettings observability() {
        return observability;
    }

    public void setObservability(ObservabilitySettings observability) {
        this.observability = observability;
    }

    public void validate() {
        if (blank(server.host())) {
            throw new IllegalArgumentException("server.host is required");
        }
        if (server.port() < 1 || server.port() > 65535) {
            throw new IllegalArgumentException("server.port must be between 1 and 65535");
        }
        if (server.readTimeout().value().isZero() || server.readTimeout().value().isNegative()) {
            throw new IllegalArgumentException("server.read_timeout must be positive");
        }
        if (server.writeTimeout().value().isZero() || server.writeTimeout().value().isNegative()) {
            throw new IllegalArgumentException("server.write_timeout must be positive");
        }
        if (blank(database.url())) {
            throw new IllegalArgumentException("database.url is required");
        }
        if (!validLogLevel(logging.level())) {
            throw new IllegalArgumentException("logging.level must be debug, info, warn, or error");
        }
        if (!validLogFormat(logging.format())) {
            throw new IllegalArgumentException("logging.format must be json or text");
        }
        if (index.bm25().k1() <= 0) {
            throw new IllegalArgumentException("index.bm25.k1 must be greater than 0");
        }
        if (index.bm25().b() < 0 || index.bm25().b() > 1) {
            throw new IllegalArgumentException("index.bm25.b must be between 0 and 1");
        }
        if (index.tokenizer().minTokenLength() < 1) {
            throw new IllegalArgumentException("index.tokenizer.min_token_length must be at least 1");
        }
        if (crawler.allowedHosts().isEmpty()) {
            throw new IllegalArgumentException("crawler.allowed_hosts must contain at least one host");
        }
        if (crawler.cloneTimeout().value().isZero() || crawler.cloneTimeout().value().isNegative()) {
            throw new IllegalArgumentException("crawler.clone_timeout must be positive");
        }
        if (crawler.maxFileSizeBytes() <= 0) {
            throw new IllegalArgumentException("crawler.max_file_size_bytes must be positive");
        }
        if (crawler.maxRepoSizeBytes() <= 0) {
            throw new IllegalArgumentException("crawler.max_repo_size_bytes must be positive");
        }
        if (crawler.maxFiles() < 1) {
            throw new IllegalArgumentException("crawler.max_files must be at least 1");
        }
        if (crawler.supportedExtensions().isEmpty()) {
            throw new IllegalArgumentException("crawler.supported_extensions must contain at least one extension");
        }
        if (jobs.workerCount() < 1) {
            throw new IllegalArgumentException("jobs.worker_count must be at least 1");
        }
        if (cluster.queryTimeout().value().isZero() || cluster.queryTimeout().value().isNegative()) {
            throw new IllegalArgumentException("cluster.query_timeout must be positive");
        }
        if (cluster.vnodeCount() < 1) {
            throw new IllegalArgumentException("cluster.vnode_count must be at least 1");
        }
        if (cluster.replicationFactor() < 1) {
            throw new IllegalArgumentException("cluster.replication_factor must be at least 1");
        }
        if (cluster.retryMax() < 0) {
            throw new IllegalArgumentException("cluster.retry_max must be at least 0");
        }
        if (cluster.retryBase().value().isZero() || cluster.retryBase().value().isNegative()) {
            throw new IllegalArgumentException("cluster.retry_base must be positive");
        }
        if (cluster.healthFailAfter() < 1) {
            throw new IllegalArgumentException("cluster.health_fail_after must be at least 1");
        }
        if (cluster.enabled()) {
            if (cluster.shards().isEmpty()) {
                throw new IllegalArgumentException("cluster.shards is required when cluster.enabled is true");
            }
            for (ClusterNode node : cluster.shards()) {
                if (blank(node.id()) || blank(node.addr())) {
                    throw new IllegalArgumentException("cluster.shards entries need id and addr");
                }
            }
            if (cluster.replicationFactor() > cluster.shards().size()) {
                throw new IllegalArgumentException("cluster.replication_factor cannot exceed the number of shards");
            }
        }
        if (shard.grpcPort() < 1 || shard.grpcPort() > 65535) {
            throw new IllegalArgumentException("shard.grpc_port must be between 1 and 65535");
        }
        if (!validSearchMode(search.defaultMode())) {
            throw new IllegalArgumentException("search.default_mode must be lexical, semantic, or hybrid");
        }
        if (search.alpha() < 0 || search.alpha() > 1) {
            throw new IllegalArgumentException("search.alpha must be between 0 and 1");
        }
        if (search.embedDim() < 8) {
            throw new IllegalArgumentException("search.embed_dim must be at least 8");
        }
        if (search.semanticCandidates() < 1) {
            throw new IllegalArgumentException("search.semantic_candidates must be at least 1");
        }
        if (cache.enabled()) {
            String backend = cache.backend() == null ? "" : cache.backend().trim().toLowerCase();
            if (!backend.equals("memory") && !backend.equals("redis")) {
                throw new IllegalArgumentException("cache.backend must be memory or redis");
            }
            if (cache.ttl().value().isZero() || cache.ttl().value().isNegative()) {
                throw new IllegalArgumentException("cache.ttl must be positive");
            }
            if (backend.equals("redis") && blank(cache.redisUrl())) {
                throw new IllegalArgumentException("cache.redis_url is required when cache.backend is redis");
            }
        }
        if (shard.metricsPort() < 1 || shard.metricsPort() > 65535) {
            throw new IllegalArgumentException("shard.metrics_port must be between 1 and 65535");
        }
    }

    public TokenizerOptions tokenizerOptions() {
        List<String> words = index.tokenizer().stopWords();
        if (words == null || words.isEmpty()) {
            words = StopWords.defaultStopWords();
        }
        return TokenizerOptions.of(index.tokenizer().minTokenLength(), words);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean validLogLevel(String level) {
        return switch (level == null ? "" : level.trim().toLowerCase()) {
            case "debug", "info", "warn", "error" -> true;
            default -> false;
        };
    }

    private static boolean validLogFormat(String format) {
        return switch (format == null ? "" : format.trim().toLowerCase()) {
            case "json", "text" -> true;
            default -> false;
        };
    }

    private static boolean validSearchMode(String mode) {
        return switch (mode == null ? "" : mode.trim().toLowerCase()) {
            case "lexical", "semantic", "hybrid" -> true;
            default -> false;
        };
    }

    public static class ServerSettings {
        private String host = "0.0.0.0";
        private int port = 8080;
        @JsonProperty("read_timeout")
        private DurationSetting readTimeout = DurationSetting.of(Duration.ofSeconds(15));
        @JsonProperty("write_timeout")
        private DurationSetting writeTimeout = DurationSetting.of(Duration.ofSeconds(15));

        public String host() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int port() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public DurationSetting readTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(DurationSetting readTimeout) {
            this.readTimeout = readTimeout;
        }

        public DurationSetting writeTimeout() {
            return writeTimeout;
        }

        public void setWriteTimeout(DurationSetting writeTimeout) {
            this.writeTimeout = writeTimeout;
        }

        public String addr() {
            return host + ":" + port;
        }
    }

    public static class DatabaseSettings {
        private String url = "postgres://helios:helios@localhost:5432/helios?sslmode=disable";

        public String url() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class LoggingSettings {
        private String level = "info";
        private String format = "json";

        public String level() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String format() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    public static class IndexSettings {
        @JsonProperty("snapshot_path")
        private String snapshotPath = "data/index.json";
        private Bm25Settings bm25 = new Bm25Settings();
        private TokenizerSettings tokenizer = new TokenizerSettings();

        public String snapshotPath() {
            return snapshotPath;
        }

        public void setSnapshotPath(String snapshotPath) {
            this.snapshotPath = snapshotPath;
        }

        public Bm25Settings bm25() {
            return bm25;
        }

        public void setBm25(Bm25Settings bm25) {
            this.bm25 = bm25;
        }

        public TokenizerSettings tokenizer() {
            return tokenizer;
        }

        public void setTokenizer(TokenizerSettings tokenizer) {
            this.tokenizer = tokenizer;
        }
    }

    public static class Bm25Settings {
        private double k1 = 1.2;
        private double b = 0.75;

        public double k1() {
            return k1;
        }

        public void setK1(double k1) {
            this.k1 = k1;
        }

        public double b() {
            return b;
        }

        public void setB(double b) {
            this.b = b;
        }
    }

    public static class TokenizerSettings {
        @JsonProperty("min_token_length")
        private int minTokenLength = 2;
        @JsonProperty("stop_words")
        private List<String> stopWords = new ArrayList<>();

        public int minTokenLength() {
            return minTokenLength;
        }

        public void setMinTokenLength(int minTokenLength) {
            this.minTokenLength = minTokenLength;
        }

        public List<String> stopWords() {
            return stopWords;
        }

        public void setStopWords(List<String> stopWords) {
            this.stopWords = stopWords == null ? new ArrayList<>() : stopWords;
        }
    }

    public static class JobsSettings {
        @JsonProperty("worker_count")
        private int workerCount = 2;

        public int workerCount() {
            return workerCount;
        }

        public void setWorkerCount(int workerCount) {
            this.workerCount = workerCount;
        }
    }

    public static class ClusterSettings {
        private boolean enabled;
        @JsonProperty("query_timeout")
        private DurationSetting queryTimeout = DurationSetting.of(Duration.ofSeconds(2));
        @JsonProperty("vnode_count")
        private int vnodeCount = 128;
        @JsonProperty("replication_factor")
        private int replicationFactor = 1;
        @JsonProperty("retry_max")
        private int retryMax = 2;
        @JsonProperty("retry_base")
        private DurationSetting retryBase = DurationSetting.of(Duration.ofMillis(20));
        @JsonProperty("health_fail_after")
        private int healthFailAfter = 2;
        private List<ClusterNode> shards = new ArrayList<>();

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public DurationSetting queryTimeout() {
            return queryTimeout;
        }

        public void setQueryTimeout(DurationSetting queryTimeout) {
            this.queryTimeout = queryTimeout;
        }

        public int vnodeCount() {
            return vnodeCount;
        }

        public void setVnodeCount(int vnodeCount) {
            this.vnodeCount = vnodeCount;
        }

        public int replicationFactor() {
            return replicationFactor;
        }

        public void setReplicationFactor(int replicationFactor) {
            this.replicationFactor = replicationFactor;
        }

        public int retryMax() {
            return retryMax;
        }

        public void setRetryMax(int retryMax) {
            this.retryMax = retryMax;
        }

        public DurationSetting retryBase() {
            return retryBase;
        }

        public void setRetryBase(DurationSetting retryBase) {
            this.retryBase = retryBase;
        }

        public int healthFailAfter() {
            return healthFailAfter;
        }

        public void setHealthFailAfter(int healthFailAfter) {
            this.healthFailAfter = healthFailAfter;
        }

        public List<ClusterNode> shards() {
            return shards;
        }

        public void setShards(List<ClusterNode> shards) {
            this.shards = shards == null ? new ArrayList<>() : shards;
        }
    }

    public static class ClusterNode {
        private String id = "";
        private String addr = "";

        public ClusterNode() {
        }

        public ClusterNode(String id, String addr) {
            this.id = id;
            this.addr = addr;
        }

        public String id() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String addr() {
            return addr;
        }

        public void setAddr(String addr) {
            this.addr = addr;
        }
    }

    public static class SearchSettings {
        @JsonProperty("default_mode")
        private String defaultMode = "lexical";
        private double alpha = 0.5;
        @JsonProperty("embed_dim")
        private int embedDim = 384;
        @JsonProperty("semantic_candidates")
        private int semanticCandidates = 200;

        public String defaultMode() {
            return defaultMode;
        }

        public void setDefaultMode(String defaultMode) {
            this.defaultMode = defaultMode;
        }

        public double alpha() {
            return alpha;
        }

        public void setAlpha(double alpha) {
            this.alpha = alpha;
        }

        public int embedDim() {
            return embedDim;
        }

        public void setEmbedDim(int embedDim) {
            this.embedDim = embedDim;
        }

        public int semanticCandidates() {
            return semanticCandidates;
        }

        public void setSemanticCandidates(int semanticCandidates) {
            this.semanticCandidates = semanticCandidates;
        }
    }

    public static class CacheSettings {
        private boolean enabled = true;
        private String backend = "memory";
        private DurationSetting ttl = DurationSetting.of(Duration.ofSeconds(60));
        @JsonProperty("redis_url")
        private String redisUrl = "";

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String backend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public DurationSetting ttl() {
            return ttl;
        }

        public void setTtl(DurationSetting ttl) {
            this.ttl = ttl;
        }

        public String redisUrl() {
            return redisUrl;
        }

        public void setRedisUrl(String redisUrl) {
            this.redisUrl = redisUrl;
        }
    }

    public static class ObservabilitySettings {
        @JsonProperty("otlp_endpoint")
        private String otlpEndpoint = "";

        public String otlpEndpoint() {
            return otlpEndpoint;
        }

        public void setOtlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
        }
    }

    public static class ShardSettings {
        private String id = "";
        @JsonProperty("grpc_host")
        private String grpcHost = "0.0.0.0";
        @JsonProperty("grpc_port")
        private int grpcPort = 9000;
        @JsonProperty("metrics_host")
        private String metricsHost = "0.0.0.0";
        @JsonProperty("metrics_port")
        private int metricsPort = 9100;

        public String id() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String grpcHost() {
            return grpcHost;
        }

        public void setGrpcHost(String grpcHost) {
            this.grpcHost = grpcHost;
        }

        public int grpcPort() {
            return grpcPort;
        }

        public void setGrpcPort(int grpcPort) {
            this.grpcPort = grpcPort;
        }

        public String grpcAddr() {
            return grpcHost + ":" + grpcPort;
        }

        public String metricsHost() {
            return metricsHost;
        }

        public void setMetricsHost(String metricsHost) {
            this.metricsHost = metricsHost;
        }

        public int metricsPort() {
            return metricsPort;
        }

        public void setMetricsPort(int metricsPort) {
            this.metricsPort = metricsPort;
        }

        public String metricsAddr() {
            String host = blank(metricsHost) ? "0.0.0.0" : metricsHost;
            int port = metricsPort < 1 ? 9100 : metricsPort;
            return host + ":" + port;
        }
    }
}
