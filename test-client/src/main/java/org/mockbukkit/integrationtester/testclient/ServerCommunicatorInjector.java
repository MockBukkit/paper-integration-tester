package org.mockbukkit.integrationtester.testclient;

import java.lang.annotation.*;

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ServerCommunicatorInjector {
}
