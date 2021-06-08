package com.myproject.mytools.sharding;

import com.myproject.mytools.annotation.ShardSetDataSource;
import com.myproject.mytools.commonEnum.DataSourceEnum;
import com.myproject.mytools.config.ShardConfig;
import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 分表分库处理类
 */
@Slf4j
public class ShardHandler {

    /**
     * 对数据源进行处理
     *
     * @param sql
     * @param params 参数
     * @param mapper
     * @return String datasource名称
     */
    protected static String handleDatasource(String sql, Object params, String mapper) {
        //需要处理表的列表
        List<String> tableHaveList = new ArrayList<>();
        //sql类型 1 select 2 update
        Integer sqlType = ShardUtils.getTypeSqlType(sql);
        //如果是更新时更新的表名
        String updateTableName = null;
        //对注解以及mapper中设置的特殊数据源进行处理，返回可用数据源列表
        Set<String> datasourceNameSet = handleAnnotationAndMapperDatasource(mapper);
        //更新操作时
        if (sqlType == 2) {
            //读取出来update对应的表名
            updateTableName = ShardUtils.getUpdateTableName(sql);
            ShardTableCustomize tableCustomize = ShardConfig.tableCustomizeMap.get(updateTableName);
            //更新时如果注解sql中没有设置自定义数据源，注解mapper中也没有设置数据源，则使用主数据源
            if (tableCustomize == null && datasourceNameSet.size() > 1) {
                datasourceNameSet = new HashSet<>();
                datasourceNameSet.add(ShardConfig.primary);
            }
            //需要自定义分表分库
            if (tableCustomize != null) {
                tableHaveList.add(tableCustomize.getTableName());
            }
        }

        //过滤出来包含的表名
        for (ShardTableCustomize tableCustomize : ShardConfig.tableCustomizeList) {
            //有update时有可能已经存在了
            if (tableHaveList.contains(tableCustomize.getTableName())) {
                continue;
            }
            if (ShardUtils.containTableName(sql, tableCustomize.getTableName())) {
                tableHaveList.add(tableCustomize.getTableName());
            }
        }

        //分别调用自定义分表类获取对应表名和库名
        for (String tableName : tableHaveList) {
            ShardTableCustomize tableCustomize = ShardConfig.tableCustomizeMap.get(tableName);
            Shard2Strategy shardStrategy = tableCustomize.getShardStrategy();
            //调用用户实现的自定义分表分库类以获取替换的库名和表名
            ShardDatasourceParams shardStrategyParams = new ShardDatasourceParams(tableName, datasourceNameSet, params, mapper, sqlType, updateTableName, sql);
            Set<String> datasourceSetOne = shardStrategy.getTargetDatasource(shardStrategyParams);
            if (CollectionUtils.isEmpty(datasourceSetOne)) {
                throw new RuntimeException("表" + tableName + "的ShardStrategy-getTargetDatasource不能返回空");
            }
            //首先判断返回的数据源是否在传入的数据源中
            for (String datasource : datasourceSetOne) {
                if (!datasourceNameSet.contains(datasource)) {
                    throw new RuntimeException("数据源" + datasource + "不在传入数据源" + datasourceNameSet + "中,sql:" + sql);
                }
            }
            //将本次返回的数据源列表给最终的数据源列表
            datasourceNameSet = datasourceSetOne;
        }

        //没有任何分表实现类并且注解中没有设置数据源时，设置为默认数据源
        if (CollectionUtils.isEmpty(tableHaveList) && datasourceNameSet.size() > 1) {
            datasourceNameSet = new HashSet<>();
            datasourceNameSet.add(ShardConfig.primary);
        }

        //从可用数据源列表中挑选一个数据源
        return getDatabaseNameFinal(datasourceNameSet);
    }

    /**
     * 对注解以及mapper中设置的特殊数据源进行处理
     *
     * @param mapper mapper
     * @return Set<String> 返回可用数据源
     */
    private static Set<String> handleAnnotationAndMapperDatasource(String mapper) {
        //获取注解设置的数据源
        ShardSetDataSource shardSetDataSource = ShardDataSourceAspect.getShardAnnotationHolderValue();
        Set<String> datasourceNameSet = new HashSet<>();
        String datasourceMapper = null;
        String mapperRegx = null;
        for (String mapperRegxTmp : ShardConfig.mapperDatasourceMap.keySet()) {
            String datasourceCurrent = null;
            if (mapperRegxTmp.startsWith("^")) {
                Pattern pattern = Pattern.compile(mapperRegxTmp);
                boolean matches = pattern.matcher(mapper).matches();
                if (matches) {
                    datasourceCurrent = ShardConfig.mapperDatasourceMap.get(mapperRegxTmp).name();
                }
            } else if (mapperRegxTmp.equals(mapper)) {
                datasourceCurrent = ShardConfig.mapperDatasourceMap.get(mapperRegxTmp).name();
            }
            //如果有匹配的配置时
            if (datasourceCurrent != null) {
                if (datasourceMapper != null && !datasourceMapper.equals(datasourceCurrent)) {
                    throw new RuntimeException("迷路了,我不知道如何去选择!" + mapperRegx + "和" + mapperRegxTmp + "均满足mapper:" + mapper);
                }
                datasourceMapper = datasourceCurrent;
                mapperRegx = mapperRegxTmp;
            }
        }

        //获取手工设置的数据源
        DataSourceEnum datasourceChange = ShardDataSourceAspect.getChangeDatasource();
        //事务开启时的判断
        if (ShardTransactionalAspect.getTransactionalAnnotation()) {
            //事务开启，注解上没数据源后续只能使用主数据源，当前线程主数据源设置在注解处理类。
            if (shardSetDataSource == null) {
                datasourceNameSet.add(ShardConfig.primary);
            }else{
                datasourceNameSet.add(shardSetDataSource.name());
            }
        } else {
            //优先级 mapper > change > annotation
            if (datasourceMapper != null) {
                datasourceNameSet.add(datasourceMapper);
            } else if (datasourceChange != null) {
                datasourceNameSet.add(datasourceChange.name());
            } else if (shardSetDataSource != null) {
                datasourceNameSet.add(shardSetDataSource.name());
            }
        }
        //若注解 mapper update都没有限制数据源，则提供全部数据源
        if (datasourceNameSet.isEmpty()) {
            for (DataSourceEnum dataSourceEnum : DataSourceEnum.values()) {
                datasourceNameSet.add(dataSourceEnum.name());
            }
        }
        return datasourceNameSet;
    }

    /**
     * 对SQL进行处理
     *
     * @param sql
     * @param params 参数
     * @param mapper
     * @return String sql对应处理后的sql
     */
    protected static String handleSql(String sql, Object params, String mapper) {
        //需要处理表的列表
        List<String> tableHaveList = new ArrayList<>();
        //sql类型 1 select 2 update
        Integer sqlType = ShardUtils.getTypeSqlType(sql);
        //老表及新表map
        Map<String, String> tableReplaceMap = new HashMap<>();
        //如果是更新时更新的表名
        String updateTableName = null;

        //更新操作时，读取出来update对应的表名
        if (sqlType == 2) {
            updateTableName = ShardUtils.getUpdateTableName(sql);
        }
        //过滤出来包含的表名
        for (ShardTableCustomize tableCustomize : ShardConfig.tableCustomizeList) {
            if (ShardUtils.containTableName(sql, tableCustomize.getTableName())) {
                tableHaveList.add(tableCustomize.getTableName());
            }
        }

        //分别调用自定义分表类获取对应表名和库名
        for (String tableName : tableHaveList) {
            ShardTableCustomize tableCustomize = ShardConfig.tableCustomizeMap.get(tableName);
            Shard2Strategy shardStrategy = tableCustomize.getShardStrategy();
            //调用用户实现的自定义分表分库类以获取替换的库名和表名
            String targetDatasource = ShardDataSource.getThreadDataSourceName();
            ShardTableParams shardTableParams = new ShardTableParams(tableName, targetDatasource, params, mapper, sqlType, updateTableName, sql);
            String targetTableName = shardStrategy.getTargetTable(shardTableParams);
            if (StringUtils.isEmpty(targetTableName)) {
                throw new RuntimeException("表" + tableName + "的ShardStrategy-getTargetTableName不能返回空");
            }
            tableReplaceMap.put(tableName, targetTableName);
        }
        //最终执行的SQL
        return replaceTableName(sql, tableReplaceMap);
    }

    /**
     * replace table name for sql
     *
     * @param sql
     * @param tableTargetMapFinal
     * @return String
     */
    private static String replaceTableName(String sql, Map<String, String> tableTargetMapFinal) {
        //循环替换表名
        for (String originTableName : tableTargetMapFinal.keySet()) {
            //从SQL中找出来所有此表名（含前后字符）防止替换错误
            Set<String> tableNameOriginSet = ShardUtils.listContainTableName(sql, originTableName);
            for (String tableNamePlus : tableNameOriginSet) {
                //将找出来的表名前后片段中的表名替换了
                String tableNamePlusTarget = tableNamePlus.replace(originTableName, tableTargetMapFinal.get(originTableName));
                sql = sql.replaceAll(tableNamePlus, tableNamePlusTarget);
            }
        }
        return sql;
    }

    /**
     * 获取最终的数据源名称
     *
     * @param datasourceSet
     * @return String 数据源名称
     */
    private static String getDatabaseNameFinal(Set<String> datasourceSet) {
        List<Pair> list = new ArrayList();
        String databaseNameFinal = null;
        if (datasourceSet.size() == 1) {
            databaseNameFinal = (String) datasourceSet.toArray()[0];
        } else {
            //多个可用数据源时
            for (String datasourceTmp : datasourceSet) {
                Integer weight = ShardConfig.dataSourceWeightMap.get(datasourceTmp);
                list.add(new Pair(datasourceTmp, weight == null ? 1 : weight));
            }
            WeightRandom weightRandom = new WeightRandom(list);
            databaseNameFinal = (String) weightRandom.random();
        }
        return databaseNameFinal;
    }
}
