package com.myproject.mytools.sharding;

import com.myproject.mytools.annotation.ShardSetDataSource;
import com.myproject.mytools.commonEnum.DataSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;

/**
 * 用来拦截service上数据源名的注解以设置对应数据源
 */
@Aspect
@Order(-1) // 该AOP在@Transactional之前执行
@Component
@Slf4j
public class ShardDataSourceAspect {
    /**
     * 线程ThreadLocal 用来保存上一个注解的信息
     */
    private static ThreadLocal<ShardSetDataSource> shardAnnotationHolder = new ThreadLocal<>();
    /**
     * 线程ThreadLocal 用来保存service中手工切换的数据源
     */
    private static ThreadLocal<DataSourceEnum> shardChangeHolder = new ThreadLocal<>();


    /**
     * 获取当前线程 shardAnnotationHolder 中的值
     */
    protected static ShardSetDataSource getShardAnnotationHolderValue() {
        return shardAnnotationHolder.get();
    }

    /**
     * 用于在service层直接设置数据源
     *
     * @param datasourceName 数据源名称
     */
    public static void changeDatasource(DataSourceEnum datasourceName) {
        HashMap dbSource = ShardUtils.getDBSource();
        if (ShardTransactionalAspect.getTransactionalAnnotation()) {
            throw new RuntimeException("在事务注解下不能直接设置数据源。" + dbSource);
        }
        //获取上一个注解的值
        ShardSetDataSource dataSourcePre = shardAnnotationHolder.get();
        if (dataSourcePre != null && !dataSourcePre.override()) {
            // 获取数据源情况
            throw new RuntimeException("Service上注解数据源override==false，无法在里面再设置数据源。" + dbSource);
        }
        shardChangeHolder.set(datasourceName);
    }

    /**
     * 获取service层直接设置的数据源
     */
    protected static DataSourceEnum getChangeDatasource() {
        return shardChangeHolder.get();
    }

    /**
     * 获取此时注解的数据源
     */
    protected static ShardSetDataSource getAnnoDatasource() {
        return shardAnnotationHolder.get();
    }

    //设置切点，这里的切入点就是打了@ShardDatasource的方法就进行切入
    @Pointcut(value = "@annotation(com.myproject.mytools.annotation.ShardSetDataSource)")
    public void pointcut() {
    }

    //AOP前置方法
    @Before(value = "pointcut()&&@annotation(shardSetDataSource)")
    public void before(JoinPoint joinPoint, ShardSetDataSource shardSetDataSource) {
        //被注解的方法名
        Object methodName = joinPoint.getSignature().getName();
        //被注解的类名
        Object className = joinPoint.getSignature().getDeclaringTypeName();
        //不能在dao上设置此注解
        if (joinPoint.getSignature().getDeclaringType().toString().contains("interface")) {
            throw new RuntimeException("不能在接口" + className + "." + methodName + "上设置ShardSetDataSource注解。" + com.myproject.mytools.sharding.ShardUtils.getDBSource());
        }
        //获得注解上的值，也就是数据源的key值
        String datasourceName = shardSetDataSource.name();
        if (StringUtils.isEmpty(datasourceName)) {
            throw new RuntimeException(className + "." + methodName + "注解数据源不能为空！" + com.myproject.mytools.sharding.ShardUtils.getDBSource());
        }
        //判断数据源是否在枚举中存在
        if (!com.myproject.mytools.sharding.ShardUtils.checkDatasourceExist(datasourceName)) {
            throw new RuntimeException(className + "." + methodName + "注解数据源：" + datasourceName + "不在枚举DataSourceEnum:" + DataSourceEnum.values() + "中。");
        }
        shardAnnotationHolder.set(shardSetDataSource);
        //当有事务的时候只能通过注解设置数据源
        com.myproject.mytools.sharding.ShardDataSource.setThreadDataSourceName(shardSetDataSource.name());
    }

    /**
     * 注解方法被调用结束后
     * @param joinPoint
     */
    @After(value = ("pointcut()"), argNames = "joinPoint")
    public void doAfterAdvice(JoinPoint joinPoint) {
        shardAnnotationHolder.set(null);
    }

}
