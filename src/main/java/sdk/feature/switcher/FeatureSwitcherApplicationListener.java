package sdk.feature.switcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import sdk.feature.switcher.annotations.EnableFeatureSwitcher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

@Configuration
public class FeatureSwitcherApplicationListener  implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    @Qualifier("sdk.feature.switcher.featureSwitcher.dataSourceBean")
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    private static final String BASE_TABLE = "feature_flags";

    private static final String createTableSQL = "CREATE TABLE " + BASE_TABLE + " ("
            + "name varchar(255) PRIMARY KEY,"
            + "value varchar(255) NOT NULL,"
            + "created_at varchar(100),"
            + "created_by varchar(100)"
            + ")";


    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        Map<String,Object> beans = context.getBeansWithAnnotation(Configuration.class);
        boolean featureEnabled = false;
        for (Map.Entry entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> originalClass = getOriginalClass(bean);
            if (originalClass.isAnnotationPresent(EnableFeatureSwitcher.class)) {
                featureEnabled = true;
                break;
            }
        }
        if (!featureEnabled) {
            System.out.println("Feature Switcher is disabled");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            createBaseTable(connection);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private String getFeatureCurrentValue(Connection connection, String flagName) {
        try {
            String sql = "SELECT * FROM " + BASE_TABLE + " WHERE name = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, flagName);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String value = resultSet.getString("value");
                return value;
            } else {
                return null;
            }

        } catch (Exception exception) {
            exception.printStackTrace();
            throw new RuntimeException(exception.getMessage());
        }
    }

    private Class<?> getOriginalClass(Object bean) {
        String className = bean.getClass().getName();
        String originalClassName = className;

        int dollarIndex = className.indexOf("$$");
        if (dollarIndex != -1) {
            originalClassName = className.substring(0, dollarIndex);
        }

        try {
            return Class.forName(originalClassName);
        } catch (ClassNotFoundException e) {
            return bean.getClass();
        }
    }

    private void createBaseTable(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, BASE_TABLE, null);
            if (!tables.next()) {
                PreparedStatement statement = connection.prepareStatement(createTableSQL);
                statement.execute();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new RuntimeException(exception.getMessage());
        }
    }


}
