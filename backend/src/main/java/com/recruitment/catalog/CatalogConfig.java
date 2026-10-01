package com.recruitment.catalog;

import com.recruitment.catalog.CatalogJdbcLoader.Catalog;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CatalogConfig {

    // @DependsOnDatabaseInitialization: bean nay doc bang cua V8 ngay luc dung, nen phai dung SAU khi
    // Flyway migrate xong (annotation co that o spring-boot-sql-4.1.0, package
    // org.springframework.boot.sql.init.dependency). Nap MOT lan, dung chung cho tra nhan, kiem ma,
    // bo khop.
    @Bean
    @DependsOnDatabaseInitialization
    CatalogRegistry catalogRegistry(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return new CatalogRegistry(
                    CatalogJdbcLoader.load(connection, Catalog.INDUSTRIES),
                    CatalogJdbcLoader.load(connection, Catalog.PROVINCES));
        }
    }
}
