package hu.detox.szexpartnerek;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class Db implements AutoCloseable {
    public static final int MAX_BATCH = 100;
    private final HikariDataSource dataSource;

    public Db(File dbFile) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(10);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("sqlite-pool");
        // Optional: config.addDataSourceProperty("cachePrepStmts", "true");
        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
