package sdk.feature.switcher.aspects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import sdk.feature.switcher.annotations.EnableFeatureSwitcher;
import sdk.feature.switcher.annotations.ConditionalInvocation;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Aspect
@Component
public class ToggleFeatureAspect {

    private static final String BASE_TABLE = "feature_flags";

    @Autowired
    @Qualifier("sdk.feature.switcher.featureSwitcher.dataSourceBean")
    private DataSource dataSource;

    @Autowired
    private ApplicationContext applicationContext;

    @Around(value = "@annotation(sdk.feature.switcher.annotations.ConditionalInvocation)")
    public Object toggleFeatureResponse(ProceedingJoinPoint joinPoint) throws Throwable {

        Map<String,Object> beans = applicationContext.getBeansWithAnnotation(Configuration.class);
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
            return joinPoint.proceed();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        Signature methodSignature = joinPoint.getSignature();
        MethodSignature signature = (MethodSignature) methodSignature;
        Method method = joinPoint.getTarget().getClass()
                .getMethod(signature.getMethod().getName(), signature.getMethod().getParameterTypes());
        ConditionalInvocation conditionalInvocation = method.getAnnotation(ConditionalInvocation.class);
        String flag = conditionalInvocation.flag();
        String state = conditionalInvocation.state();
        Object[] methodArguments = joinPoint.getArgs();

        try (Connection connection = dataSource.getConnection()) {
            String value = getFeatureCurrentValue(connection, flag);
            if (value == null) {
                throw new RuntimeException("Invalid state");
            }
            if (!value.equals(state)) {
                Method fallbackMethod = getFallbackMethod(conditionalInvocation.fallbackClass(), conditionalInvocation.fallbackMethod());
                if (fallbackMethod != null) {
                    if (!fallbackMethod.getClass().isPrimitive()) {
                        Object response = getMethodResponse(conditionalInvocation.fallbackClass(), conditionalInvocation.fallbackMethod());
                        String json = objectMapper.writeValueAsString(response);
                        return json;
                    } else {
                        return getMethodResponse(conditionalInvocation.fallbackClass(), conditionalInvocation.fallbackMethod());
                    }
                }
                String json = objectMapper.writeValueAsString(Map.of("message", "Feature is disabled"));
                return json;
            }
            return joinPoint.proceed();
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

    private Object getMethodResponse(String fallbackClass, String fallbackMethod) {
        try {
            if (fallbackMethod.isEmpty() || fallbackMethod.isEmpty()) {
                return null;
            }
            List<String> arguments = new ArrayList<>();
            if (fallbackMethod.indexOf("(") != -1) {
                String[] argumentsStrings = fallbackMethod.substring(fallbackMethod.indexOf("(") + 1, fallbackMethod.indexOf(")")).split(",");
                for (String arg : argumentsStrings) {
                    if (!arg.isEmpty()) arguments.add(arg.trim());
                }
            }
            fallbackMethod = fallbackMethod.substring(0, fallbackMethod.indexOf("("));
            Class<?> clazz = Class.forName(fallbackClass);
            Object instance = clazz.getDeclaredConstructor().newInstance(); // Create an instance of the class
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (fallbackMethod != null && method.getName().equals(fallbackMethod)) {
                    if (!arguments.isEmpty()) {
                        Object[] finalArguments = new Object[arguments.size()];
                        int i = 0;
                        for (Parameter parameter : method.getParameters()) {
                            finalArguments[i] = convertToPrimitive(arguments.get(i), parameter.getType());
                            i++;
                        }
                        Object result = method.invoke(instance, finalArguments);
                        return result;
                    }
                    Object result = method.invoke(instance);
                    return result;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
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

    public Method getFallbackMethod(String fallbackClass, String fallbackMethod) {
        if (fallbackMethod.isEmpty() || fallbackClass.isEmpty()) {
            return null;
        }
        fallbackMethod = fallbackMethod.substring(0, fallbackMethod.indexOf("("));
        try {
            Class<?> clazz = Class.forName(fallbackClass);
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (fallbackMethod != null && method.getName().equals(fallbackMethod)) {
                    return method;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
           return null;
        }
        return null;
    }

    private static Object convertToPrimitive(String value, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(value);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(value);
        } else if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(value);
        } else if (targetType == char.class || targetType == Character.class) {
            if (value.length() == 1) {
                return value.charAt(0);
            }
        } else if (targetType == String.class) {
            return value;
        }

        throw new IllegalArgumentException("Unsupported type: " + targetType);
    }


}
