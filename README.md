# Feature Switcher Library

The Feature Switcher Library allows you to dynamically enable or disable specific functions or methods in your application based on flag values stored in a database. It provides a flexible way to control the behavior of your application without the need for code changes.

## Features

- Dynamic feature enablement based on flag values.
- Fallback mechanism for disabled features.
- Customizable configuration.

## Installation

To use the Feature Switcher Library, you can include it as a dependency in your project. Add the following Maven or Gradle dependency:

**Maven**

```xml

		<dependency>
			<groupId>sdk.feature.switcher</groupId>
			<artifactId>feature-switcher</artifactId>
			<version>1.0-SNAPSHOT</version>
		</dependency>
```

#### application.properties (Based on your RDBMS)
```
spring.datasource.url=jdbc:postgresql://localhost:5432/db
spring.datasource.username=<username>
spring.datasource.password=<password>
spring.datasource.driver-class-name=org.postgresql.Driver
```

#### Enable Feature
```
@SpringBootApplication
@EnableFeatureSwitcher
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
```

#### Usage
On successful initialization there would be a table created in database named as `feature_flags`

You have to create an entry against a flag in the table like below

````
db > select * from feature_flags;
  name  | value |     created_at      | created_by 
--------+-------+---------------------+------------
 myFlag | T1    | 2023-11-02 14:30:00 | dummyUser
````

and use it like this way
```
    @GetMapping("/testMapping")
    @ConditionalInvocation(flag = "myFlag", state = "T1")
    public String testMapping() {
        return "Test Data";
    }

```

* Explanation :- Whenever the value of `myFlag` will be T1 then Above function will successfully return `Test Data`
* If value of `myFlag` will be other than T1 then it would return `{message : 'Feature is disabled'}` as default value

You can also specify fallback class and method, In case you don't want to rely on Default response

````
    @GetMapping("/testMapping")
    @ConditionalInvocation(flag = "myFlag", state = "T1", fallbackClass = "com.example.demo.responses.ErrorResponse", fallbackMethod = "errorResponse(Feature is disabled, 403, true)")
    public String testMapping() {
        return "Test Data";
    }

    @GetMapping("/testMapping/v2")
    @ConditionalInvocation(flag = "myFlag", state = "T1", fallbackClass = "com.example.demo.responses.ErrorResponse", fallbackMethod = "error()")
    public String testMapping() {
        return "Test Data";
    }


@NoArgsConstructor
@Setter
@Getter
public class ErrorResponse {

    public ResponseEntity<?> errorResponse(String message, Integer code, Boolean testFlag) {
        if (testFlag) {
            return new ResponseEntity<>(Map.of("message", "Test Flag true message"),HttpStatus.BAD_REQUEST);
        } else {
            return new ResponseEntity<>(Map.of("message", message),HttpStatus.valueOf(code));
        }
    }

    public String error() {
        return "Simple Error";
    }

````

In the fallback example it will invoke the fallbackMethod and return the response from fallbackMethod
