package com.mia_princz.wowmarket.collector;

import static com.mia_princz.wowmarket.collector.TestcontainersConfiguration.BI_READER_PASSWORD;
import static com.mia_princz.wowmarket.collector.TestcontainersConfiguration.COLLECTOR_PASSWORD;
import static com.mia_princz.wowmarket.collector.TestcontainersConfiguration.TRANSFORMER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Each role can do exactly what its layer needs, after Flyway has run as the collector. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WarehouseRolesIT {

    @Autowired
    PostgreSQLContainer postgres;

    @Test
    void flywayRecordsItsHistoryInOps() throws SQLException {
        try (Connection collector = connect("collector", COLLECTOR_PASSWORD);
                Statement sql = collector.createStatement()) {
            var result = sql.executeQuery("SELECT count(*) FROM ops.flyway_schema_history WHERE success");
            result.next();
            assertThat(result.getInt(1)).isPositive();
        }
    }

    @Test
    void transformerReadsButCannotWriteWhatTheCollectorLoads() throws SQLException {
        try (Connection collector = connect("collector", COLLECTOR_PASSWORD);
                Statement sql = collector.createStatement()) {
            sql.execute("CREATE TABLE raw.grant_probe (id int)");
            sql.execute("INSERT INTO raw.grant_probe VALUES (1)");
        }

        try (Connection transformer = connect("transformer", TRANSFORMER_PASSWORD);
                Statement sql = transformer.createStatement()) {
            var result = sql.executeQuery("SELECT count(*) FROM raw.grant_probe");
            result.next();
            assertThat(result.getInt(1)).isEqualTo(1);
            assertThatThrownBy(() -> sql.execute("INSERT INTO raw.grant_probe VALUES (2)"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.execute("CREATE TABLE ops.not_mine (id int)"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void biReaderSeesMartsOnly() throws SQLException {
        try (Connection transformer = connect("transformer", TRANSFORMER_PASSWORD);
                Statement sql = transformer.createStatement()) {
            sql.execute("CREATE VIEW marts.grant_probe AS SELECT 1 AS id");
            sql.execute("CREATE VIEW core.grant_probe AS SELECT 1 AS id");
        }

        try (Connection biReader = connect("bi_reader", BI_READER_PASSWORD);
                Statement sql = biReader.createStatement()) {
            var result = sql.executeQuery("SELECT id FROM marts.grant_probe");
            result.next();
            assertThat(result.getInt(1)).isEqualTo(1);
            assertThatThrownBy(() -> sql.executeQuery("SELECT * FROM core.grant_probe"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.executeQuery("SELECT * FROM ops.flyway_schema_history"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.execute("CREATE VIEW marts.mine AS SELECT 1"))
                    .hasMessageContaining("permission denied");
        }
    }

    private Connection connect(String role, String password) throws SQLException {
        return DriverManager.getConnection(TestcontainersConfiguration.warehouseUrl(postgres), role, password);
    }
}
