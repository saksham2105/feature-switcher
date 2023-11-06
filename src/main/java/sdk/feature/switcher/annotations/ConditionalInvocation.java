package sdk.feature.switcher.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConditionalInvocation {
    String flag() default "";
    String state() default  "";
    String fallbackClass() default "";
    String fallbackMethod() default "";
}
