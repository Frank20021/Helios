package io.helios.shard;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import io.helios.hashring.HashRing;
import io.helios.index.InvertedIndex;
import io.helios.repository.Store;
import io.helios.telemetry.Metrics;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class HeliosShardApplication {
    public static void main(String[] args) throws Exception {
        HeliosConfig cfg = ConfigLoader.load(io.helios.api.HeliosApiApplication.flagValue(args, "-config"));
        if (cfg.shard().id() == null || cfg.shard().id().isBlank()) {
            throw new IllegalStateException("HELIOS_SHARD_ID is required");
        }
        String[] userPass = ConfigLoader.userPassword(cfg.database().url());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(ConfigLoader.toJdbcUrl(cfg.database().url()));
        ds.setUsername(userPass[0]);
        ds.setPassword(userPass[1]);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("0").load().migrate();
        Store store = new Store(new NamedParameterJdbcTemplate(ds));
        InvertedIndex idx = new InvertedIndex(cfg.tokenizerOptions(), cfg.index().bm25().k1(), cfg.index().bm25().b());
        List<HashRing.Node> nodes = new ArrayList<>();
        for (HeliosConfig.ClusterNode n : cfg.cluster().shards()) {
            nodes.add(new HashRing.Node(n.id(), n.addr()));
        }
        if (nodes.isEmpty()) {
            nodes.add(new HashRing.Node(cfg.shard().id(), cfg.shard().grpcAddr()));
        }
        HashRing ring = new HashRing(nodes, cfg.cluster().vnodeCount());
        ShardService svc = new ShardService(cfg.shard().id(), idx, ring, store, cfg.index().snapshotPath())
                .setReplication(cfg.cluster().replicationFactor());
        svc.recover();
        ShardGrpcServer grpc = new ShardGrpcServer(svc);
        grpc.start(cfg.shard().grpcPort());
        Metrics metrics = new Metrics();
        metrics.setPassages(svc.stats().passageCount());
        HttpServer http = HttpServer.create(new InetSocketAddress(cfg.shard().metricsHost(), cfg.shard().metricsPort()), 0);
        http.createContext("/metrics", exch -> {
            byte[] body = metrics.prometheus().getBytes(StandardCharsets.UTF_8);
            exch.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exch.sendResponseHeaders(200, body.length);
            exch.getResponseBody().write(body);
            exch.close();
        });
        http.createContext("/health/live", exch -> {
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exch.getResponseHeaders().add("Content-Type", "application/json");
            exch.sendResponseHeaders(200, body.length);
            exch.getResponseBody().write(body);
            exch.close();
        });
        http.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            grpc.stop();
            http.stop(0);
            ds.close();
        }));
        Thread.currentThread().join();
    }
}
