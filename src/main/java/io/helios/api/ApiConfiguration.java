package io.helios.api;

import com.zaxxer.hikari.HikariDataSource;
import io.helios.bm25.Bm25;
import io.helios.cache.Caches;
import io.helios.cache.QueryCache;
import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import io.helios.coordinator.CoordinatorBackend;
import io.helios.coordinator.GrpcShardClient;
import io.helios.coordinator.ShardClient;
import io.helios.hashring.HashRing;
import io.helios.index.InvertedIndex;
import io.helios.jobs.JobService;
import io.helios.repository.Store;
import io.helios.tracing.Otlp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ApiConfiguration implements WebMvcConfigurer {
    @Bean
    @ConditionalOnMissingBean
    HeliosConfig heliosConfig() throws Exception {
        return ConfigLoader.load("");
    }

    @Bean
    DataSource dataSource(HeliosConfig cfg) {
        String[] userPass = ConfigLoader.userPassword(cfg.database().url());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(ConfigLoader.toJdbcUrl(cfg.database().url()));
        ds.setUsername(userPass[0]);
        ds.setPassword(userPass[1]);
        return ds;
    }

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean
    Store store(NamedParameterJdbcTemplate jdbc) {
        return new Store(jdbc);
    }

    @Bean
    InvertedIndex invertedIndex(HeliosConfig cfg) {
        return new InvertedIndex(cfg.tokenizerOptions(), cfg.index().bm25().k1(), cfg.index().bm25().b());
    }

    @Bean(destroyMethod = "stop")
    JobService jobService(HeliosConfig cfg, Store store, InvertedIndex index) {
        JobService svc = new JobService(cfg, store, index);
        if (cfg.cache().enabled()) {
            QueryCache cache = Caches.open(cfg.cache().backend(), cfg.cache().redisUrl(), cfg.cache().ttl().value());
            svc.setCache(cache);
        }
        if (cfg.cluster().enabled()) {
            List<HashRing.Node> nodes = new ArrayList<>();
            for (HeliosConfig.ClusterNode n : cfg.cluster().shards()) {
                nodes.add(new HashRing.Node(n.id(), n.addr()));
            }
            HashRing ring = new HashRing(nodes, cfg.cluster().vnodeCount());
            List<ShardClient> clients = new ArrayList<>();
            for (HashRing.Node node : ring.nodes()) {
                clients.add(new GrpcShardClient(node));
            }
            CoordinatorBackend backend = new CoordinatorBackend(ring, clients, cfg.cluster().queryTimeout().value(),
                    Bm25.DEFAULT_K1, Bm25.DEFAULT_B, cfg.tokenizerOptions(), svc.traces())
                    .setReplication(cfg.cluster().replicationFactor())
                    .setRetry(cfg.cluster().retryMax(), cfg.cluster().retryBase().value())
                    .setHealthThreshold(cfg.cluster().healthFailAfter());
            svc.setIndexer(backend);
        }
        if (cfg.observability().otlpEndpoint() != null && !cfg.observability().otlpEndpoint().isBlank()) {
            svc.setExporter(new Otlp.Exporter(cfg.observability().otlpEndpoint()));
        }
        svc.start();
        return svc;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/ui/**").addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/ui/index.html");
    }
}
