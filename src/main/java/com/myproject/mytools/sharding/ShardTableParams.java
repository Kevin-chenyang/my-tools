package com.myproject.mytools.sharding;

import lombok.Data;

/**
 * 自定义分表的接口类的参数
 */
@Data
public class ShardTableParams extends ShardBaseParams {
    //确定的数据源
    private String datasource;

    public ShardTableParams(String tableName, String datasource, Object params, String mapperId, Integer sqlType, String updateTableName, String sql) {
        this.tableName = tableName;
        this.datasource = datasource;
        this.params = params;
        this.mapperId = mapperId;
        this.sqlType = sqlType;
        this.updateTableName = updateTableName;
        this.sql = sql;
    }

}
