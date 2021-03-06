package com.github.mustfun.mybatis.plugin.service;

import com.github.mustfun.mybatis.plugin.model.DbSourcePo;
import com.github.mustfun.mybatis.plugin.model.LocalColumn;
import com.github.mustfun.mybatis.plugin.model.LocalTable;
import com.github.mustfun.mybatis.plugin.model.enums.VmTypeEnums;
import com.github.mustfun.mybatis.plugin.provider.FileProviderFactory;
import com.github.mustfun.mybatis.plugin.setting.ConnectDbSetting;
import com.github.mustfun.mybatis.plugin.util.ConnectionHolder;
import com.github.mustfun.mybatis.plugin.util.DbUtil;
import com.github.mustfun.mybatis.plugin.util.JavaUtils;
import com.github.mustfun.mybatis.plugin.util.MybatisConstants;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.http.client.utils.DateUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author itar
 * @version 1.0
 * @date 2018/6/13
 * @since 1.0
 */
public class MysqlService {
    private static final  Logger logger = LoggerFactory.getLogger(MysqlService.class);

    public final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_PACKAGE_PATH = "com.github.mustfun";

    private static ConcurrentHashMap<Integer, Integer> templateGenerateTimeMap = new ConcurrentHashMap<>(10);
    /**
     * ??????PackageName???????????????
     */
    private static ConcurrentHashMap<Integer, String> fileHashMap = new ConcurrentHashMap<>(10);

    private  Project project;

    public MysqlService(Project project) {
        this.project = project;
    }

    public Connection getConnection(DbSourcePo configPo) {
        DbUtil dbUtil;
        if (configPo.getPort() == null) {
            dbUtil = new DbUtil(configPo.getDbAddress(), configPo.getDbName(), configPo.getUserName(),
                configPo.getPassword());
        } else {
            //if configured
            dbUtil = new DbUtil(configPo.getDbAddress(), configPo.getDbName(), configPo.getUserName(),
                configPo.getPassword(), configPo.getPort());
        }
        return dbUtil.getConnection(project, MybatisConstants.MYSQL_DB_CONNECTION);
    }

    public Connection getNewConnection(DbSourcePo dbSourcePo) {
        ConnectionHolder.getInstance(project).remove();
        return getConnection(dbSourcePo);
    }

    public List<LocalTable> getTables(Connection connection) {
        DatabaseMetaData dbMetData;
        List<LocalTable> localTables = new ArrayList<>();
        try {
            dbMetData = connection.getMetaData();
            String[] types = {"TABLE"};
            ResultSet rs = dbMetData.getTables(connection.getCatalog(), null, "%", types);
            while (rs.next()) {
                LocalTable localTable = initLocalTable(connection, rs);
                localTables.add(localTable);
            }
        } catch (SQLException e) {
            System.out.println("table?????? e = " + e);
        }
        return localTables;
    }

    public LocalTable initLocalTable(Connection connection, ResultSet rs) throws SQLException {
        LocalTable localTable = new LocalTable();
        String tableName = rs.getString("TABLE_NAME");
        System.out.println("tableName = " + tableName);
        logger.debug("????????????tableName = " + tableName);
        String tableType = rs.getString("TABLE_TYPE");
        String remarks = rs.getString("REMARKS");
        localTable.setComment(remarks);
        localTable.setTableType(tableType);
        localTable.setTableName(tableName);
        getColumns(connection, tableName, localTable);
        return localTable;
    }

    private LocalTable getColumns(Connection connection, String tableName, LocalTable localTable) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<LocalColumn> localColumns = new ArrayList<>();
        ResultSet primaryKeys = meta.getPrimaryKeys(connection.getCatalog(), null, tableName);
        String pkColumnName = null;
        while (primaryKeys.next()) {
            pkColumnName = primaryKeys.getString("COLUMN_NAME");
        }
        LocalColumn pkColumn = new LocalColumn();
        ResultSet survey = meta.getColumns(connection.getCatalog(), null, tableName, null);
        while (survey.next()) {
            LocalColumn localColumn = new LocalColumn();
            String columnName = survey.getString("COLUMN_NAME");
            localColumn.setColumnName(columnName);
            String columnType = survey.getString("TYPE_NAME");
            localColumn.setDataType(columnType);
            int size = survey.getInt("COLUMN_SIZE");
            localColumn.setSize(size);
            int nullable = survey.getInt("NULLABLE");
            if (nullable == DatabaseMetaData.columnNullable) {
                localColumn.setNullable(true);
            } else {
                localColumn.setNullable(false);
            }
            int position = survey.getInt("ORDINAL_POSITION");
            localColumn.setPosition(position);
            localColumn.setColumnComment(survey.getString("REMARKS"));
            if (columnName.equalsIgnoreCase(pkColumnName)) {
                pkColumn = localColumn;
            }
            localColumns.add(localColumn);
        }
        localTable.setPk(pkColumn);
        localTable.setTableName(tableName);
        localTable.setColumnList(localColumns);
        return localTable;
    }


    public void generateCodeUseTemplate(ConnectDbSetting connectDbSetting, Connection connection, LocalTable columns,
        String tablePrefix, List<com.github.mustfun.mybatis.plugin.model.Template> vmList) {
        generatorCode(connectDbSetting, connection, columns, columns.getColumnList(), tablePrefix, vmList);
    }


    public void generatorCode(ConnectDbSetting connectDbSetting, Connection connection, LocalTable table,
        List<LocalColumn> columns, String tablePrefix, List<com.github.mustfun.mybatis.plugin.model.Template> vmList) {
        fileHashMap.clear();
        SqlLiteService sqlLiteService = DbServiceFactory.getInstance(project).createSqlLiteService();

        boolean maxModelFlag = connectDbSetting.getPoStyle().isSelected();
        boolean hasBigDecimal = false;
        //???????????????Java??????
        //PluginConfig tablePrefix = sqlLiteService.queryPluginConfigByKey("tablePrefix");
        String className = tableToJava(table.getTableName(), tablePrefix);
        table.setClassName(className);
        table.setClassLittleName(StringUtils.uncapitalize(className));

        //?????????
        List<LocalColumn> columnsList = new ArrayList<>();
        for (LocalColumn column : columns) {

            //???????????????Java?????????
            String attrName = columnToJava(column.getColumnName());
            column.setAttrName(attrName);
            column.setAttrLittleName(StringUtils.uncapitalize(attrName));

            //??????????????????????????????Java??????
            String attrType = sqlLiteService.queryPluginConfigByKey(column.getDataType().toUpperCase()).getValue();
            column.setAttrType(attrType);
            transAttrTypePath(attrType, column);
            if (!hasBigDecimal && attrType.equals("BigDecimal")) {
                hasBigDecimal = true;
            }
            transSpecialDataType(column);
            columnsList.add(column);
        }
        table.setColumnList(columnsList);

        //???????????????????????????????????????
        if (table.getPk() == null) {
            table.setPk(table.getColumnList().get(0));
        }

        //??????velocity???????????????
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.Log4JLogChute");
        engine.setProperty(Velocity.RESOURCE_LOADER, "string");
        engine.addProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        engine.addProperty("string.resource.loader.repository.static", "false");

        engine.init();
        StringResourceRepository repo = (StringResourceRepository) engine
                .getApplicationAttribute(StringResourceLoader.REPOSITORY_NAME_DEFAULT);

        String mainPath = sqlLiteService.queryPluginConfigByKey("mainPath").getValue();
        mainPath = StringUtils.isBlank(mainPath) ? "com.generator" : mainPath;
        //??????????????????
        Map<String, Object> map = new HashMap<>();
        map.put("tableName", table.getTableName());
        map.put("comment", table.getComment());
        map.put("pk", table.getPk());
        map.put("className", table.getClassName());
        map.put("classLittleName", table.getClassLittleName());
        map.put("pathName", table.getClassLittleName().toLowerCase());
        map.put("columns", table.getColumnList());
        map.put("hasBigDecimal", hasBigDecimal);
        map.put("mainPath", mainPath);
        map.put("author", sqlLiteService.queryPluginConfigByKey("author").getValue());
        map.put("email", sqlLiteService.queryPluginConfigByKey("email").getValue());
        map.put("datetime", DateUtils.formatDate(new Date(), MysqlService.DATE_TIME_PATTERN));
        VelocityContext context = new VelocityContext(map);

        //vmList??????
        vmList.sort(Comparator.comparing(o -> sqlLiteService.queryTemplateById(o.getId()).getVmType()));

        //??????????????????????????????po/dao/service/result??????????????????=============================
        String poFileName = getFileName(VmTypeEnums.MODEL_PO.getCode(), className, maxModelFlag);
        //VirtualFile poFile = JavaUtils.getExistFilePathByName(projectDir, poFileName);
        PsiFile[] poFiles = FilenameIndex.getFilesByName(project, Objects.requireNonNull(poFileName), GlobalSearchScope.allScope(project));
        if (poFiles.length > 0) {
            String poPackageName = JavaUtils.getFullClassPath(poFiles[0], poFileName);
            fileHashMap.put(VmTypeEnums.MODEL_PO.getCode(), poPackageName);
        }
        String daoFileName = getFileName(VmTypeEnums.DAO.getCode(), className, maxModelFlag);
        //VirtualFile daoFile = JavaUtils.getExistFilePathByName(projectDir,daoFileName );
        PsiFile[] daoFiles = FilenameIndex.getFilesByName(project, Objects.requireNonNull(daoFileName), GlobalSearchScope.allScope(project));
        if (daoFiles.length>0) {
            String daoPackageName = JavaUtils.getFullClassPath(daoFiles[0], daoFileName);
            fileHashMap.put(VmTypeEnums.DAO.getCode(), daoPackageName);
        }
        //??????????????????????????????po/dao/service/result??????????????????=============================

        //??????????????????
        for (com.github.mustfun.mybatis.plugin.model.Template template : vmList) {
            //????????????
            //com.github.mustfun.mybatis.plugin.model.Template template = sqlLiteService.queryTemplateById(templateId);
            if (!checkNeedGenerate(template.getVmType())) {
                continue;
            }

            //????????????
            try (StringWriter sw = new StringWriter()) {

                //???????????????????????????????????????
                replaceContent(template,table.getClassName(),maxModelFlag);
                String fileName = getFileName(template.getVmType(), table.getClassName(),maxModelFlag);
                String outPath = getRealPath(template.getVmType(), connectDbSetting);

                String realPackageName = DEFAULT_PACKAGE_PATH;
                if (!template.getVmType().equals(VmTypeEnums.MAPPER.getCode())) {
                    VirtualFile vFile = WriteAction.computeAndWait(() -> VfsUtil.createDirectoryIfMissing(outPath));
                    //???????????????????????????
                    realPackageName = JavaUtils.getNotExistPackageNameFromDirectory(vFile);
                }

                assert fileName != null;
                fileHashMap.put(template.getVmType(), realPackageName==null? fileName.split("\\.")[0] : realPackageName + "." + fileName.split("\\.")[0]);
                //???VM??????
                importNeedClass(context, template.getVmType(), table.getClassName());

                //merge ??????
                repo.putStringResource(template.getTepName() + "_" + template.getId(), template.getTepContent());
                Template tpl = engine.getTemplate(template.getTepName() + "_" + template.getId(), "UTF-8");
                tpl.merge(context, sw);

                FileProviderFactory fileFactory = new FileProviderFactory(project, outPath);
                if (template.getVmType().equals(VmTypeEnums.MAPPER.getCode())) {
                    fileFactory.getInstance("xml").create(sw.toString(), fileName);
                } else {
                    fileFactory.getInstance("java").create(sw.toString(), fileName);
                }

            } catch (IOException e) {
                System.out.println("????????????????????????{}e = " + e);
                throw new RuntimeException("??????????????????????????????" + table.getTableName(), e);
            }
        }
    }

    private static void transSpecialDataType(LocalColumn column) {
        //BIGINT????????????
        if ("BITINT UNSIGNED".equalsIgnoreCase(column.getDataType().toUpperCase()) || "BITINT SIGNED"
            .equalsIgnoreCase(column.getDataType().toUpperCase())) {
            column.setDataType("BIGINT");
        }
        if ("INT UNSIGNED".equalsIgnoreCase(column.getDataType().toUpperCase()) || "INT SIGNED"
            .equalsIgnoreCase(column.getDataType().toUpperCase()) || column.getDataType().toUpperCase().equalsIgnoreCase("INT")) {
            column.setDataType("INTEGER");
        }
        if ("DATETIME".equalsIgnoreCase(column.getDataType().toUpperCase()) || "DATE"
            .equalsIgnoreCase(column.getDataType().toUpperCase())) {
            column.setDataType("TIMESTAMP");
        }
    }

    private static void transAttrTypePath(String attrType, LocalColumn column) {
        if ("Integer".equals(attrType)) {
            column.setAttrTypePath("java.lang.Integer");
        }
        if ("Long".equals(attrType)) {
            column.setAttrTypePath("java.lang.Long");
        }
    }


    /**
     * ?????????
     */
    private  void importNeedClass(VelocityContext context, Integer vmType, String className) {
        ArrayList<String> arrayList = new ArrayList<>();
        String poImport = fileHashMap.get(VmTypeEnums.MODEL_PO.getCode());
        String daoImport = fileHashMap.get(VmTypeEnums.DAO.getCode());
        String serverImport = fileHashMap.get(VmTypeEnums.SERVICE.getCode());
        String resultImport = fileHashMap.get(VmTypeEnums.RESULT.getCode());
        if (StringUtils.isEmpty(poImport)) {
            VirtualFile filePattenPath = JavaUtils
                .getFileByPattenName(Objects.requireNonNull(ProjectUtil.guessProjectDir(project)), className + "po.java", className + ".java");
            poImport = filePattenPath == null ? null : JavaUtils.getFullClassPath(project, filePattenPath, filePattenPath.getName());
        }
        if (StringUtils.isEmpty(daoImport)) {
            VirtualFile filePattenPath = JavaUtils.getFileByPattenName(Objects.requireNonNull(ProjectUtil.guessProjectDir(project)), className + "Dao.java");
            daoImport = filePattenPath == null ? null : JavaUtils.getFullClassPath(project, filePattenPath, filePattenPath.getName());
        }
        if (StringUtils.isEmpty(serverImport)) {
            VirtualFile filePattenPath = JavaUtils.getFileByPattenName(Objects.requireNonNull(ProjectUtil.guessProjectDir(project)), className + "Service.java");
            serverImport = filePattenPath == null ? null : JavaUtils.getFullClassPath(project, filePattenPath, filePattenPath.getName());
        }
        if (StringUtils.isEmpty(resultImport)) {
            VirtualFile filePattenPath = JavaUtils
                .getFileByPattenName(Objects.requireNonNull(ProjectUtil.guessProjectDir(project)), "Result.java", "BaseResult.java", "BaseResponse.java",
                    "Response.java");
            resultImport = filePattenPath == null ? null : JavaUtils.getFullClassPath(project, filePattenPath, filePattenPath.getName());
        }
        if (vmType.equals(VmTypeEnums.SERVICE.getCode())) {
            arrayList.add(poImport);
            arrayList.add(daoImport);
            context.internalPut("needImports", arrayList);
        }
        if (vmType.equals(VmTypeEnums.DAO.getCode())) {
            arrayList.add(poImport);
            context.internalPut("needImports", arrayList);
        }
        if (vmType.equals(VmTypeEnums.CONTROLLER.getCode())) {
            arrayList.add(poImport);
            arrayList.add(serverImport);
            arrayList.add(resultImport);
            context.internalPut("needImports", arrayList);
        }
        if (vmType.equals(VmTypeEnums.SERVICE_IMPL.getCode())) {
            arrayList.add(daoImport);
            arrayList.add(poImport);
            arrayList.add(serverImport);
            context.internalPut("needImports", arrayList);
        }
        if (vmType.equals(VmTypeEnums.MAPPER.getCode())) {
            context.internalPut("daoImport", daoImport);
            context.internalPut("poImport", poImport);
        }

    }

    private static String getRealPath(Integer template, ConnectDbSetting connectDbSetting) {
        if (template.equals(VmTypeEnums.RESULT.getCode())) {
            return connectDbSetting.getPoInput().getText();
        }
        if (template.equals(VmTypeEnums.MODEL_PO.getCode())) {
            return connectDbSetting.getPoInput().getText() + "/po";
        }

        if (template.equals(VmTypeEnums.MODEL_BO.getCode())) {
            return connectDbSetting.getPoInput().getText() + "/bo";
        }

        if (template.equals(VmTypeEnums.MODEL_REQ.getCode())) {
            return connectDbSetting.getPoInput().getText() + "/req";
        }

        if (template.equals(VmTypeEnums.MODEL_RESP.getCode())) {
            return connectDbSetting.getPoInput().getText() + "/resp";
        }
        if (template.equals(VmTypeEnums.DAO.getCode())) {
            return connectDbSetting.getDaoInput().getText();
        }

        if (template.equals(VmTypeEnums.SERVICE.getCode())) {
            return connectDbSetting.getServiceInput().getText();
        }

        if (template.equals(VmTypeEnums.SERVICE_IMPL.getCode())) {
            return connectDbSetting.getServiceInput().getText() + "/impl";
        }

        if (template.equals(VmTypeEnums.CONTROLLER.getCode())) {
            return connectDbSetting.getControllerInput().getText();
        }

        if (template.equals(VmTypeEnums.CONTROLLER_IMPL.getCode())) {
            return connectDbSetting.getControllerInput().getText() + "/impl";
        }

        if (template.equals(VmTypeEnums.MAPPER.getCode())) {
            return connectDbSetting.getMapperInput().getText();
        }
        return null;
    }

    /**
     * ???????????????Java??????
     */
    public static String tableToJava(String tableName, String tablePrefix) {
        if (StringUtils.isNotBlank(tablePrefix)) {
            if (!tablePrefix.contains("_")) {
                tablePrefix += "_";
            }
            tableName = tableName.replaceFirst(tablePrefix, "");
        }
        return columnToJava(tableName);
    }

    /**
     * ???????????????Java?????????
     */
    public static String columnToJava(String columnName) {
        return WordUtils.capitalizeFully(columnName, new char[]{'_'}).replace("_", "");
    }

    private static boolean checkNeedGenerate(Integer template) {
        if (template.equals(VmTypeEnums.RESULT.getCode())) {
            Integer integer = templateGenerateTimeMap.get(template);
            if (integer == null) {
                templateGenerateTimeMap.put(template, 1);
            } else if (integer > 0) {
                return false;
            }
        }
        return true;
    }


    /**
     * ???????????????
     */
    public static String getFileName(Integer template, String className, boolean maxModelFlag) {
        if (template.equals(VmTypeEnums.RESULT.getCode())) {
            return "Result.java";
        }
        if (template.equals(VmTypeEnums.MODEL_PO.getCode())) {
            return className + (maxModelFlag?"PO.java":"Po.java");
        }
        if (template.equals(VmTypeEnums.MODEL_BO.getCode())) {
            return className + (maxModelFlag?"BO.java":"Bo.java");
        }
        if (template.equals(VmTypeEnums.MODEL_REQ.getCode())) {
            return className + (maxModelFlag?"REQ.java":"Req.java");
        }
        if (template.equals(VmTypeEnums.MODEL_RESP.getCode())) {
            return className + (maxModelFlag?"RESP.java":"Resp.java");
        }
        if (template.equals(VmTypeEnums.DAO.getCode())) {
            return className + "Dao.java";
        }
        if (template.equals(VmTypeEnums.SERVICE.getCode())) {
            return className + "Service.java";
        }
        if (template.equals(VmTypeEnums.SERVICE_IMPL.getCode())) {
            return className + "ServiceImpl.java";
        }
        if (template.equals(VmTypeEnums.CONTROLLER.getCode())) {
            return className + "Controller.java";
        }
        if (template.equals(VmTypeEnums.MAPPER.getCode())) {
            return className + "Dao.xml";
        }
        return null;
    }



    /**
     * ??????????????????????????????????????????????????????????????????
     */
    public static void replaceContent(com.github.mustfun.mybatis.plugin.model.Template tmp, String className, boolean maxModelFlag) {
        Integer template = tmp.getVmType();
        if (template.equals(VmTypeEnums.MODEL_PO.getCode())) {
            if (maxModelFlag) {
                tmp.setTepContent(tmp.getTepContent().replace("${className}Po", "${className}PO"));
            }
            return ;
        }
        if (template.equals(VmTypeEnums.MODEL_BO.getCode())) {
            if (maxModelFlag) {
                tmp.setTepContent(tmp.getTepContent().replace("${className}Bo", "${className}BO"));
            }
            return ;
        }
        if (template.equals(VmTypeEnums.MODEL_REQ.getCode())) {
            if (maxModelFlag) {
                tmp.setTepContent(tmp.getTepContent().replace("${className}Req", "${className}REQ"));
            }
            return ;
        }
        if (template.equals(VmTypeEnums.MODEL_RESP.getCode())) {
            if (maxModelFlag) {
                tmp.setTepContent(tmp.getTepContent().replace("${className}Resp", "${className}RESP"));
            }
        }
        if (template.equals(VmTypeEnums.DAO.getCode())) {
            if (maxModelFlag) {
                tmp.setTepContent(tmp.getTepContent().replace("${className}Po", "${className}PO"));
            }
        }
    }


}
