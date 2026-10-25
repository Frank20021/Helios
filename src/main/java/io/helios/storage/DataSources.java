package io.helios.storage;

import com.zaxxer.hikari.HikariDataSource;
import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public final class DataSources {
    private DataSources() {
    }

    public static DataSource open(HeliosConfig cfg) {
        String[] userPass = ConfigLoader.userPassword(cfg.database().url());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(ConfigLoader.toJdbcUrl(cfg.database().url()));
        ds.setUsername(userPass[0]);
        ds.setPassword(userPass[1]);
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        return ds;
    }
}
