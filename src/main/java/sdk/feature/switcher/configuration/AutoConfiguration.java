package sdk.feature.switcher.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@Configuration
@ComponentScan("sdk.feature.switcher")
public class AutoConfiguration {
    @Autowired
    private Environment environment;

    @Bean(name = "sdk.feature.switcher.featureSwitcher.dataSourceBean")
    public DataSource dataSource() {
        try {
            String username = environment.getProperty("spring.datasource.username");
            String password = environment.getProperty("spring.datasource.password");
            String url = environment.getProperty("spring.datasource.url");
            String maxPoolSizeString = environment.getProperty("spring.datasource.hikari.maximum-pool-size");
            String driver = environment.getProperty("spring.datasource.driver-class-name");
            int maxPoolSize = 10;
            if (Strings.isEmpty(url)) {
                return new HikariDataSource();
            }
            if (Strings.isNotEmpty(maxPoolSizeString)) {
                maxPoolSize = Integer.parseInt(maxPoolSizeString);
            }
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setDriverClassName(driver);
            config.setAutoCommit(true);
            config.setUsername(username);
            if (Strings.isNotEmpty(password)) {
                config.setPassword(password);
            }
            config.setMaximumPoolSize(maxPoolSize);
            HikariDataSource hikariDataSource = new HikariDataSource(config);
            return hikariDataSource;
        } catch (Exception e) {
            e.printStackTrace();
            return new HikariDataSource();
        }
    }

}
