package com.myproject.mytools.sharding;

import lombok.Data;

/**
 * 自定义分表分库的接口类的参数
 */
@Data
public class ShardBaseParams {
    //参数，Map或者对象
    protected Object params;
    //1 select 2 update/insert/delete
    protected Integer sqlType;
    //sqlType 2时 更新的表名
    protected String updateTableName;
    //原表名
    protected String tableName;
    //sql语句
    protected String sql;
    //如 com.gymbomate.basic.dao.findById
    protected String mapperId;

}
