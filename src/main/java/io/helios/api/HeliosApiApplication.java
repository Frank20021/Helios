package io.helios.api;

import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import io.helios.shard.HeliosShardApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication(scanBasePackages = "io.helios")
public class HeliosApiApplication {
    public static void main(String[] args) throws Exception {
        if ("shard".equalsIgnoreCase(System.getenv().getOrDefault("HELIOS_ROLE", ""))) {
            HeliosShardApplication.main(args);
            return;
        }
        HeliosConfig cfg = ConfigLoader.load(flagValue(args, "-config"));
        String[] userPass = ConfigLoader.userPassword(cfg.database().url());
        SpringApplication app = new SpringApplication(HeliosApiApplication.class);
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", cfg.server().port());
        props.put("server.address", cfg.server().host());
        props.put("spring.datasource.url", ConfigLoader.toJdbcUrl(cfg.database().url()));
        props.put("spring.datasource.username", userPass[0]);
        props.put("spring.datasource.password", userPass[1]);
        props.put("spring.flyway.enabled", true);
        app.setDefaultProperties(props);
        app.addInitializers(ctx -> ctx.getBeanFactory().registerSingleton("heliosConfig", cfg));
        app.run(args);
    }

    public static String flagValue(String[] args, String name) {
        if (args == null) {
            return "";
        }
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return "";
    }
}
