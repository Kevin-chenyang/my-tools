package com.myproject.mytools.sharding;

import java.util.Set;

/**
 * 自定义分表的接口类
 */
public interface Shard2Strategy {

    /**
     * 返回目标表名
     *
     * @param shardTableParams 分表分库参数
     * @return String 目标表
     */
    String getTargetTable(ShardTableParams shardTableParams);

    /**
     * 返回目标数据源名集合
     *
     * @param shardStrategyParams 分表分库参数
     * @return Set<String> 目标数据源
     */
    Set<String> getTargetDatasource(ShardDatasourceParams shardStrategyParams);
}
