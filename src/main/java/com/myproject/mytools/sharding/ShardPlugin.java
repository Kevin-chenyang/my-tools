package com.myproject.mytools.sharding;

import com.myproject.mytools.config.ShardConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Properties;

/**
 * mybaits Executor 插件用于实现分表分库。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
@Slf4j
public class ShardPlugin implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //没有开启新分表分库，直接跳过这个拦截器
        if (StringUtils.isEmpty(ShardConfig.primary)) {
            return invocation.proceed();
        }

        if ("prepare".equals(invocation.getMethod().getName())) {
            //进行分表操作
            // mybatis的拦截器配置两个，按照顺序生成代理，此拦截器第二个，所以此代理的是第一个拦截器的代理
            Field h = invocation.getTarget().getClass().getSuperclass().getDeclaredField("h");
            h.setAccessible(true);
            Plugin plugin = (Plugin) h.get(invocation.getTarget());
            Field target = plugin.getClass().getDeclaredField("target");
            target.setAccessible(true);

            StatementHandler statementHandler = ((StatementHandler) target.get(plugin));

            String sql = statementHandler.getBoundSql().getSql();
            Object params = statementHandler.getBoundSql().getParameterObject();
            //获取mapperid
            MappedStatement mappedStatement = null;
            if (statementHandler instanceof RoutingStatementHandler) {
                StatementHandler delegate = (StatementHandler) ReflectionUtils
                        .getFieldValue(statementHandler, "delegate");
                mappedStatement = (MappedStatement) ReflectionUtils.getFieldValue(
                        delegate, "mappedStatement");
            } else {
                mappedStatement = (MappedStatement) ReflectionUtils.getFieldValue(
                        statementHandler, "mappedStatement");
            }
            String mapperId = mappedStatement.getId();

            sql = ShardHandler.handleSql(sql, params, mapperId);
            ReflectionUtils.setFieldValue(statementHandler.getBoundSql(), "sql", sql);
        } else {
            //自定义数据源操作
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            //获取参数
            Object argsObj = null;
            if (invocation.getArgs().length > 1) {
                argsObj = invocation.getArgs()[1];
            }
            String mapperId = mappedStatement.getId();
            //获取sql语句
            BoundSql boundSql = mappedStatement.getSqlSource().getBoundSql(argsObj);
            //获取数据源
            String datasourceName = ShardHandler.handleDatasource(boundSql.getSql(), argsObj, mapperId);
            //设置当前线程数据源
            ShardDataSource.setThreadDataSourceName(datasourceName);
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
