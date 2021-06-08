package com.myproject.mytools.sharding;

import lombok.Data;

/**
 * 表自定义分表规则属性类
 */
@Data
public class ShardTableCustomize implements Comparable<ShardTableCustomize> {
    //分表类
    private String tableName;
    //分表类
    private Shard2Strategy shardStrategy;
    //优先级
    private Integer priority;

    public ShardTableCustomize() {
    }

    @Override
    public int compareTo(ShardTableCustomize shardTableCustomize) {
        return shardTableCustomize.priority.compareTo(this.priority);
    }
}
