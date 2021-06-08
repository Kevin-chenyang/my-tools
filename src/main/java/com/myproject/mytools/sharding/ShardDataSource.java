package com.myproject.mytools.sharding;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;


/**
 * 重新Spring获取数据源名称方法，用来动态设置mybatis数据源
 */
public class ShardDataSource extends AbstractRoutingDataSource {
    /**
     * 线程ThreadLocal
     */
    private static ThreadLocal<String> contextHolder = new ThreadLocal<>();

    /**
     * 获取当前线程的数据源名称
     *
     * @return String 数据源名称
     */
    protected static String getThreadDataSourceName() {
        return contextHolder.get();
    }

    /**
     * 设置当前线程的数据源名称
     *
     * @param dataSourceName 数据源名称
     */
    protected static void setThreadDataSourceName(String dataSourceName) {
        contextHolder.set(dataSourceName);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String datasourceName = contextHolder.get();
        return datasourceName;
    }
}
