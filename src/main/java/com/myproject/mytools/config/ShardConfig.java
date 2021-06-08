package com.myproject.mytools.config;

import com.myproject.mytools.commonEnum.DataSourceEnum;
import com.myproject.mytools.sharding.Shard2Strategy;
import com.myproject.mytools.sharding.ShardTableCustomize;
import com.myproject.mytools.sharding.ShardUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 用来从配置中读取分表分库的相关配置
 *
 * @Classname ShardConfig
 * @Description TODO
 * @Date 2020-04-23 10:06
 * @Author grace.tang
 */
@Component
@Data
@ConfigurationProperties(prefix = "spring.datasource")
@Slf4j
public class ShardConfig {

    // 定义全局的ShardHandle需要的类型
    //主数据源
    public static String primary;
    //自定义分表规则(已按优先级进行倒序排列)
    public static ArrayList<ShardTableCustomize> tableCustomizeList = new ArrayList<>();
    //数据源名称集合（含权重例如1则出现一次，0不出现，2出现两次）
    public static HashMap<String, Integer> dataSourceWeightMap = new HashMap<>();
    //表名和对应自定义
    public static Map<String, ShardTableCustomize> tableCustomizeMap = new HashMap<>();
    //mapper和数据源的特殊设定
    public static Map<String, DataSourceEnum> mapperDatasourceMap = new HashMap<>();
    // 获取配置文件哪些需要缓存结果集的Mapper方法
    private Map<String, Integer> mapperCache;
    // 获取配置文件需要分表的表以及对应的分表策略
    private List<TableStrategyEntity> tableStrategy = new ArrayList<>();
    // 获取配置文件每个数据源的信息，获取其权重
    private Map<String, String> druid;
    //mapper配置的特殊数据源
    private Map<String, String[]> mapperDatasource = new HashMap<>();

    // 解决配置文件的信息不能注入到static变量
    @Value("${spring.datasource.primary}")
    public void setPrimary(String primary) {
        ShardConfig.primary = primary;
    }

    @PostConstruct
    public void init() {
        // 从配置文件读取的tableStrategy，转成handler所需要的格式
        for (TableStrategyEntity strategy : this.tableStrategy) {
            ShardTableCustomize tableCustomize = new ShardTableCustomize();
            tableCustomize.setPriority(strategy.getPriority());
            tableCustomize.setTableName(strategy.getTableName());
            // 根据配置文件的全路径获取对应的实现类
            try {
                Class<?> className = Class.forName(strategy.getStrategy());
                try {
                    Shard2Strategy shardStrategy = (Shard2Strategy) className.newInstance();
                    tableCustomize.setShardStrategy(shardStrategy);
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            tableCustomizeList.add(tableCustomize);
            tableCustomizeMap.put(strategy.getTableName(), tableCustomize);
        }

        // tableCustomizeList排序 按优先级进行倒序排列 321
        Collections.sort(tableCustomizeList);

        // 根据从配置文件读取的数据源信息，提取其权重信息,如果数据源没有配置权重，默认为1
        for (String dbName : this.druid.keySet()) {
            String[] split = dbName.split("\\.");
            if (!dataSourceWeightMap.containsKey(split[0])) {
                dataSourceWeightMap.put(split[0], 1);
            }
            if ("weight".equals(split[1])) {
                Integer weight = 10 < new Integer(druid.get(dbName)) ? 10 : new Integer(druid.get(dbName));
                dataSourceWeightMap.put(split[0], weight);
            }
        }
        //对mapperDatasource进行处理
        for (String datasource : mapperDatasource.keySet()) {
            String[] mappers = mapperDatasource.get(datasource);
            for (String mapper : mappers) {
                if (mapper.contains("*") || mapper.contains("?")) {
                    String mapperRegx = ShardUtils.regex4Wildcard(mapper);
                    mapperDatasourceMap.put(mapperRegx, DataSourceEnum.valueOf(datasource));
                } else {
                    mapperDatasourceMap.put(mapper, DataSourceEnum.valueOf(datasource));
                }
            }
        }
    }
}

/**
 * 用于从yml中映射分表数据
 */
@Data
class TableStrategyEntity {
    //表名
    private String tableName;
    //分表策略类的全路径
    private String strategy;
    //优先级
    private Integer priority = 1;
}
