package com.myproject.mytools.sharding;

import com.myproject.mytools.config.ShardConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用来拦截service上事务的注解用来判断是否存在事务
 */
@Aspect
@Order(-2) // 该AOP在@Transactional和@ShardSetDataSource之前执行
@Component
@Slf4j
public class ShardTransactionalAspect {
    /**
     * 线程ThreadLocal 用来保存上一个注解的信息
     */
    private static ThreadLocal<Boolean> transactionalAnnotationHolder = new ThreadLocal<>();

    /**
     * 获取当前线程 是否在事务注解之中
     */
    protected static Boolean getTransactionalAnnotation() {
        Boolean flag = transactionalAnnotationHolder.get();
        if (flag == null) {
            return false;
        } else {
            return flag;
        }
    }

    //设置切点，这里的切入点就是打了@Transactional的方法就进行切入
    @Pointcut(value = "@annotation(org.springframework.transaction.annotation.Transactional)")
    public void pointcut() {
    }

    //AOP前置方法
    @Before(value = "pointcut()&&@annotation(transactional)")
    public void before(JoinPoint joinPoint, Transactional transactional) {
        transactionalAnnotationHolder.set(true);
        //如果当前还没有指定则给默认给主数据源（不能直接给主数据源，因为可能在非事务的方法中调用事务方法）
        if(StringUtils.isEmpty(ShardDataSource.getThreadDataSourceName())) {
            ShardDataSource.setThreadDataSourceName(ShardConfig.primary);
        }
    }

    @After(value = ("pointcut()"), argNames = "joinPoint")
    public void doAfterAdvice(JoinPoint joinPoint) {
        //事务结束
        transactionalAnnotationHolder.set(false);
    }

}
