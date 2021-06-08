package com.myproject.mytools.annotation;

import java.lang.annotation.*;

/**
 * 设定数据源注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ShardSetDataSource {
    //datasource name
    String name() default "";
    //默认不可用覆盖
    boolean override() default false;
}
