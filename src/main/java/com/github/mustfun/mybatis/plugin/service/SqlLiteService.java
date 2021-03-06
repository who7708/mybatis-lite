package com.github.mustfun.mybatis.plugin.service;

import com.github.mustfun.mybatis.plugin.model.DbSourcePo;
import com.github.mustfun.mybatis.plugin.model.ModuleConfig;
import com.github.mustfun.mybatis.plugin.model.PluginConfig;
import com.github.mustfun.mybatis.plugin.model.Template;
import com.github.mustfun.mybatis.plugin.model.enums.VmTypeEnums;
import com.github.mustfun.mybatis.plugin.setting.ConnectDbSetting;
import com.github.mustfun.mybatis.plugin.util.ConnectionHolder;
import com.github.mustfun.mybatis.plugin.util.DbUtil;
import com.intellij.openapi.project.Project;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author itar
 * @version 1.0
 * @date 2018/6/13
 * @since 1.0
 */
public class SqlLiteService {

    public static final String ClASS_POSITION="_GENERATE_CLASS_POSITION_";
    private Connection connection;
    private Statement statement;
    private Project project=null;



    public Connection getSqlLiteConnection() {
        return this.connection;
    }


    public SqlLiteService(Project project) {
        this.project = project;
        DbUtil dbUtil = new DbUtil();
        this.connection = dbUtil.getSqlliteConnection(project);
        try {
            this.statement = connection.createStatement();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public SqlLiteService() {
        DbUtil dbUtil = new DbUtil();
        this.connection = dbUtil.getInnerSqlLiteConnection();
        try {
            this.statement = connection.createStatement();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public Template queryTemplateById(Integer id) {
        try {
            String sql = "select\n" +
                "        id, tep_name, tep_desc,create_by,tep_content,vm_type,db_type,create_time\n" +
                "        from template\n" +
                "        where id = " + id;
            ResultSet rs = statement.executeQuery(sql);
            Template template = new Template();
            while (rs.next()) {
                template.setId(rs.getInt("id"));
                template.setTepName(rs.getString("tep_name"));
                template.setTepDesc(rs.getString("tep_desc"));
                template.setTepContent(rs.getString("tep_content"));
                template.setDbType(rs.getInt("db_type"));
                template.setVmType(rs.getInt("vm_type"));
            }
            return template;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteTemplate(Integer id) {
        try {
            statement.executeUpdate("delete from template where id=" + id);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateTemplate(Template template) {
        try {
            String content = template.getTepContent().replace("'", "''");
            statement.executeUpdate(
                "update template set tep_content='" + content + "' where id=" + template.getId());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public List<Template> queryTemplateList() {
        try {
            String sql = "select\n" +
                "        id, tep_name, tep_desc,create_by,tep_content,vm_type,db_type,create_time\n" +
                "        from template";
            ResultSet rs = statement.executeQuery(sql);
            List<Template> list = new ArrayList<>();
            while (rs.next()) {
                Template template = new Template();
                template.setId(rs.getInt("id"));
                template.setTepName(rs.getString("tep_name"));
                template.setTepDesc(rs.getString("tep_desc"));
                template.setTepContent(rs.getString("tep_content"));
                template.setDbType(rs.getInt("db_type"));
                template.setCreateBy(rs.getString("create_by"));
                template.setVmType(rs.getInt("vm_type"));
                list.add(template);
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public List<PluginConfig> queryPluginConfigList() {
        try {
            String sql = "select\n" +
                "        id,key,value\n" +
                "        from plugin_config";
            List<PluginConfig> list = new ArrayList<>();

            ResultSet rs = statement.executeQuery(sql);
            while (rs.next()) {
                PluginConfig template = new PluginConfig();
                template.setId(rs.getInt("id"));
                template.setKey(rs.getString("key"));
                template.setValue(rs.getString("value"));
                list.add(template);
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public PluginConfig queryPluginConfigByKey(String key) {
        try {
            String sql = "select\n" +
                "        id,key,value\n" +
                "        from plugin_config where key='" + key + "'limit 1";

            ResultSet rs = statement.executeQuery(sql);
            PluginConfig template = new PluginConfig();
            while (rs.next()) {
                template.setId(rs.getInt("id"));
                template.setKey(rs.getString("key"));
                template.setValue(rs.getString("value"));
            }
            return template;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * ???????????????????????????
     * @return
     */
    public DbSourcePo queryLatestConnectLog(String key) {
        try {
            String sql = "select\n" +
                "        id,db_name,db_address,user_name,password\n" +
                "        from db_source where module_name='"+key+"' order by id desc limit 1";

            ResultSet rs = statement.executeQuery(sql);
            DbSourcePo dbSource = null;
            while (rs.next()) {
                dbSource = new DbSourcePo();
                dbSource.setId(rs.getInt("id"));
                dbSource.setDbName(rs.getString("db_name"));
                dbSource.setPort(Integer.valueOf(rs.getString("port")));
                dbSource.setDbAddress(rs.getString("db_address"));
                dbSource.setUserName(rs.getString("user_name"));
                dbSource.setPassword(rs.getString("password"));
                dbSource.setModuleName(rs.getString("module_name"));
            }
            return dbSource;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String,DbSourcePo> getModuleConfig() {
        try {
            String sql = "select * from db_source where id in (select max(id) from db_source group by db_name);";

            ResultSet rs = statement.executeQuery(sql);
            Map<String,DbSourcePo> list = new HashMap<>();
            while (rs.next()) {
                DbSourcePo dbSource = new DbSourcePo();
                dbSource.setId(rs.getInt("id"));
                dbSource.setDbName(rs.getString("db_name"));
                dbSource.setPort(Integer.valueOf(rs.getString("port")));
                dbSource.setDbAddress(rs.getString("db_address"));
                dbSource.setUserName(rs.getString("user_name"));
                dbSource.setPassword(rs.getString("password"));
                dbSource.setModuleName(rs.getString("module_name"));
                list.put(dbSource.getModuleName(),dbSource);
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * ??????????????????????????? , ??????????????????????????? - ?????????????????????id??????????????????.......
     */
    public boolean insertDbConnectionInfo(DbSourcePo dbSourcePo) {
        try {
            statement.executeUpdate(
                "insert into db_source(db_name,db_address,port,user_name,password,module_name,create_time) values('" + dbSourcePo
                    .getDbName() + "','" + dbSourcePo.getDbAddress() + "',"+dbSourcePo.getPort()+",'" + dbSourcePo.getUserName() + "','"
                    + dbSourcePo.getPassword() + "','"+dbSourcePo.getModuleName()+"',datetime())");
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * ??????????????????????????? dao /po ?????????
     * @param
     * @param project
     * @param connectDbSetting
     */
    public void saveUserPreferPath(Project project, ConnectDbSetting connectDbSetting) {
        boolean daoPositionSelect = connectDbSetting.getDaoPositionCheckBox().isSelected();
        boolean mapperPositionSelect = connectDbSetting.getMapperPositionCheckBox().isSelected();
        boolean modelPositionSelect = connectDbSetting.getModelPositionCheckBox().isSelected();
        boolean controllerPositionSelect = connectDbSetting.getControllerPositionCheckBox().isSelected();
        boolean servicePositionSelect = connectDbSetting.getServicePositionCheckBox().isSelected();
        Map<VmTypeEnums, String> map = new HashMap<>(5);
        Map<VmTypeEnums, String> deleteMap = new HashMap<>(5);
        if (daoPositionSelect){
            map.put(VmTypeEnums.DAO, connectDbSetting.getDaoInput().getText());
        }else{
            deleteMap.put(VmTypeEnums.DAO, null);
        }
        if (mapperPositionSelect){
            map.put(VmTypeEnums.MAPPER, connectDbSetting.getMapperInput().getText());
        }else{
            deleteMap.put(VmTypeEnums.MAPPER, null);
        }
        if (modelPositionSelect){
            map.put(VmTypeEnums.MODEL_PO, connectDbSetting.getPoInput().getText());
        }else{
            deleteMap.put(VmTypeEnums.MODEL_PO, null);
        }
        if (controllerPositionSelect){
            map.put(VmTypeEnums.CONTROLLER, connectDbSetting.getControllerInput().getText());
        }else{
            deleteMap.put(VmTypeEnums.CONTROLLER, null);
        }
        if (servicePositionSelect){
            map.put(VmTypeEnums.SERVICE, connectDbSetting.getServiceInput().getText());
        }else{
            deleteMap.put(VmTypeEnums.SERVICE, null);
        }
        try {
            for (VmTypeEnums vmTypeEnums : deleteMap.keySet()) {
                String deleteSql = "delete from user_preference where up_key='" + project.getName()+ClASS_POSITION + vmTypeEnums.getCode()+"'";
                statement.executeUpdate(deleteSql);
            }
            for (VmTypeEnums vmTypeEnums : map.keySet()) {
                String key = project.getName()+ClASS_POSITION + vmTypeEnums.getCode();
                String userPreferPath = getUserPreferPathByVmType(project.getName(), vmTypeEnums);
                if (userPreferPath!=null){
                    String deleteSql = "delete from user_preference where up_key='" +project.getName()+ ClASS_POSITION + vmTypeEnums.getCode()+"'";
                    statement.executeUpdate(deleteSql);
                }
                statement.executeUpdate(
                        "insert into user_preference(up_key, up_value, up_desc, create_time)" +
                                " values('"+key+"','"+map.get(vmTypeEnums)+"','????????????"+vmTypeEnums.getMgs()+"??????',current_date)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????????????? ?????????????????????  ???????????????????????????????????????key
     * @param groupName
     * @param vmTypeEnums
     * @return
     */
    public String getUserPreferPathByVmType(String groupName, VmTypeEnums vmTypeEnums){
        String key = groupName + ClASS_POSITION + vmTypeEnums.getCode();
        try {
            ResultSet resultSet = statement.executeQuery("select up_value from user_preference where up_key='" + key + "'");
            while (resultSet.next()){
                return resultSet.getString("up_value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
        }
        return null;
    }

    /**
     * ??????DB??????????????????
     */
    public void refreshFromDB() {
        if (project==null){
            return ;
        }
        Map<String, DbSourcePo> moduleConfig = getModuleConfig();
        ConnectionHolder instance = ConnectionHolder.getInstance(project);
        for (String s : moduleConfig.keySet()) {
            Object existConfig = instance.getConfig(s);
            DbSourcePo dbSourcePo = moduleConfig.get(s);
            ModuleConfig target = new ModuleConfig();
            if (existConfig!=null){
                ModuleConfig exist = (ModuleConfig) existConfig;
                target.setTypeAliasPackage(exist.getTypeAliasPackage());
            }
            target.setUrl(dbSourcePo.getUrl());
            target.setDbName(dbSourcePo.getDbName());
            target.setDbAddress(dbSourcePo.getDbAddress());
            target.setUserName(dbSourcePo.getUserName());
            target.setPassword(dbSourcePo.getPassword());
            target.setPort(dbSourcePo.getPort());
            instance.putConfig(s, target);
        }
    }
}
