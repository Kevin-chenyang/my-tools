package com.myproject.mytools.sharding;

import lombok.Data;

import java.util.Set;

/**
 * 自定义分库的接口类的参数
 */
@Data
public class ShardDatasourceParams extends com.gymbomate.common.generic.shard2.ShardBaseParams {
    //不为null时必须从这里选择数据源
    private Set<String> fixedDatasource;

    public ShardDatasourceParams(String tableName, Set<String> fixedDatasource, Object params, String mapperId, Integer sqlType, String updateTableName, String sql) {
        this.tableName = tableName;
        this.fixedDatasource = fixedDatasource;
        this.params = params;
        this.mapperId = mapperId;
        this.sqlType = sqlType;
        this.updateTableName = updateTableName;
        this.sql = sql;
    }

}
