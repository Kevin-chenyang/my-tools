package com.myproject.mytools.sharding;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.myproject.mytools.commonEnum.DataSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 分表分库工具类
 */
@Slf4j
public class ShardUtils {
    //用来查找表名时前后的字符
    private static String PATTERN_FOR_TABLE_NAME_AROUND = "(\\s|\\.|$)";

    /**
     * 获取通配符表达式对应的正则表达式<br>
     * "*" 表示零个或多个字符,"?"表示零个或1个字符
     *
     * @param wildcard 通配符表达式
     * @return 通配符表达式对应的正则表达式
     */
    public static String regex4Wildcard(String wildcard) {
        //需要处理的字符，不包含* ？要进行特殊处理
        char[] escapeChars = new char[]{'\\', '+', '|', '{', '[', '(', ')', '^', '$', '.', '#'};
        // 获取需要取消转义的字符数组
        StringBuilder sb = new StringBuilder();
        for (char ch : wildcard.toCharArray()) {
            // 需要转义的字符
            if (ArrayUtils.contains(escapeChars, ch)) {
                sb.append('\\');
            }
            sb.append(ch);
        }
        //加上开头和结尾
        if (sb.indexOf("^") != 0) {
            sb.insert(0, "^");
        }
        if (sb.lastIndexOf("$") != sb.length() - 1) {
            sb.append("$");
        }
        // 取消除通配符?,*之外的所有转义字符后再对?,*进行转义
        return sb.toString().replaceAll("\\?", ".?").replaceAll("\\*", ".*");
    }

    /**
     * 判断数据源名称是否在枚举中存在
     *
     * @param datasourceName 数据源名称
     * @return Boolean
     */
    public static Boolean checkDatasourceExist(String datasourceName) {
        //判断数据源是否在枚举中存在
        boolean exist = false;
        for (DataSourceEnum dataSourceEnum : DataSourceEnum.values()) {
            if (dataSourceEnum.name().equals(datasourceName)) {
                exist = true;
                break;
            }
        }
        return exist;
    }

    /**
     * 获取更新sql对应的表名
     *
     * @param sql
     * @return String
     */
    public static String getUpdateTableName(String sql) {
        //TODO: 获取insert|update|delete的表名
        String tableName = null;
        String lowerCase = sql.toLowerCase().trim();
        if (lowerCase.startsWith("insert")) {
            String[] s = lowerCase.replaceAll("insert\\s+into\\s+", "").split(" ");
            tableName = s[0];
        }
        if (lowerCase.startsWith("delete")) {
            String[] s = lowerCase.replaceAll("delete\\s+from\\s+", "").split(" ");
            tableName = s[0];
        }
        if (lowerCase.startsWith("update")) {
            String[] s = lowerCase.replaceAll("update\\s+", "").split(" ");
            tableName = s[0];
        }
        //要去掉前后的空格以及换行符
        return tableName.replaceAll("\\s+|\t|\r|\n", "");
    }

    /**
     * 获取sql类型
     *
     * @param sql SQL语句
     * @return Integer 1 select ,2 insert|update|delete
     */
    public static Integer getTypeSqlType(String sql) {
        sql = sql.toLowerCase();
        //用来匹配操作命令及后面的空格|换行|分页|Tab中的一个。
        String patternStr = "(insert|update|delete)\\s";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher dateMatcher = pattern.matcher(sql);
        return dateMatcher.find() ? 2 : 1;
    }

    /**
     * 是否包含某个表名
     *
     * @param sql       源SQL
     * @param tableName 表名
     * @return Set<String>
     */
    public static Boolean containTableName(String sql, String tableName) {
        Pattern pattern = Pattern.compile(PATTERN_FOR_TABLE_NAME_AROUND + tableName + PATTERN_FOR_TABLE_NAME_AROUND);
        Matcher dateMatcher = pattern.matcher(sql);
        return dateMatcher.find();
    }

    /**
     * 查询是否包含表名，并且返回带有前后符号的表名。
     *
     * @param sql       源SQL
     * @param tableName 表名
     * @return Set<String>
     */
    public static Set<String> listContainTableName(String sql, String tableName) {
        Pattern pattern = Pattern.compile(PATTERN_FOR_TABLE_NAME_AROUND + tableName + PATTERN_FOR_TABLE_NAME_AROUND);
        Matcher dateMatcher = pattern.matcher(sql);
        Set<String> set = new HashSet<>();
        while (dateMatcher.find()) {
            set.add(dateMatcher.group());
        }
        return set;
    }

    /**
     * 获取SQL注释中的数据源名称。如：{"shardSetDataSource":"dataSourceSlave1"},根据
     *
     * @param sql
     * @return String
     */
    public static String getSqlDatasourceName(String sql) {
        //先简单判断下
        if (!sql.contains("/*") || !sql.contains("*/")) {
            return null;
        }
        //截取注释数据源的sql片段
        String datasourceSql = null;
        String regex = "\\/\\*.*\\{.+shardSetDataSource\".*:.*\".+\".*}.*\\*\\/";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(sql);
        //因为是默认贪婪模式，所以就算是多个也只会匹配上一个
        if (matcher.find()) {
            datasourceSql = matcher.group(0);
        }
        if (datasourceSql == null) {
            return null;
        }
        // 判断是否出现多次/*{"shardSetDataSource":"dataSourceSlave1"}*/
        if (datasourceSql.indexOf("shardSetDataSource") != datasourceSql.lastIndexOf("shardSetDataSource")) {
            throw new RuntimeException("核实是否配置多个shardSetDataSoure");
        }
        //根据上面正则的到的/*{"xxx":"xxxx"}*/格式获取shardSetDataSource的value值
        String regex1 = "\\/\\*";
        String s1 = datasourceSql.replaceAll(regex1, "");  //任意字符到/*" 替换成空
        String regex2 = "\\*\\/";
        String s2 = s1.replaceAll(regex2, "");// "*/ 到结束 替换成空
        JSONObject jsonObject = JSON.parseObject(s2);
        String shardSetDataSource = jsonObject.getString("shardSetDataSource");
        return shardSetDataSource;
    }

    /**
     * 获取当前的各个数据源
     *
     * @return
     */
    public static HashMap getDBSource() {
        HashMap<String, String> hashMap = new HashMap<>();
        String annoDBSource = ShardDataSourceAspect.getAnnoDatasource() == null ? null : ShardDataSourceAspect.getAnnoDatasource().name();
        hashMap.put("当前存储的注解数据源", annoDBSource);
        String changeDBSource = ShardDataSourceAspect.getChangeDatasource() == null ? null : ShardDataSourceAspect.getChangeDatasource().name();
        hashMap.put("当前存储的change设置的数据源", changeDBSource);
        String contextDBSource = ShardDataSource.getThreadDataSourceName();
        hashMap.put("当前spring的数据源", contextDBSource);
        return hashMap;
    }
    //    /**
//     * Executor分库时获取SQL和参数
//     * @param configuration
//     * @param boundSql
//     * @return String
//     */
//    public static String getSql4Executor(Configuration configuration, BoundSql boundSql) {
//        Object parameterObject = boundSql.getParameterObject();
//        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
//        String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
//        if (parameterMappings.size() > 0 && parameterObject != null) {
//            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
//            if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
//                sql = sql.replaceFirst("\\?", getParameterValue(parameterObject));
//            } else {
//                MetaObject metaObject = configuration.newMetaObject(parameterObject);
//                for (ParameterMapping parameterMapping : parameterMappings) {
//                    String propertyName = parameterMapping.getProperty();
//                    if (metaObject.hasGetter(propertyName)) {
//                        Object obj = metaObject.getValue(propertyName);
//                        sql = sql.replaceFirst("\\?", getParameterValue(obj));
//                    } else if (boundSql.hasAdditionalParameter(propertyName)) {
//                        Object obj = boundSql.getAdditionalParameter(propertyName);
//                        sql = sql.replaceFirst("\\?", getParameterValue(obj));
//                    }
//                }
//            }
//        }
//        return sql;
//    }
//
//    /**
//     * 分库时获取SQL时获取参数中的值
//     * @param obj
//     * @return String
//     */
//    private static String getParameterValue(Object obj) {
//        String value = null;
//        if (obj instanceof String) {
//            value = "'" + obj.toString() + "'";
//        } else if (obj instanceof Date) {
//            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.CHINA);
//            value = "'" + formatter.format(obj) + "'";
//        } else {
//            if (obj != null) {
//                value = obj.toString();
//            } else {
//                value = "";
//            }
//
//        }
//        return value;
//    }
}
