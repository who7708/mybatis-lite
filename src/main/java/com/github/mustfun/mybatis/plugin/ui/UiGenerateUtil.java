package com.github.mustfun.mybatis.plugin.ui;

import com.github.mustfun.mybatis.plugin.listener.CheckMouseListener;
import com.github.mustfun.mybatis.plugin.model.DbSourcePo;
import com.github.mustfun.mybatis.plugin.model.LocalTable;
import com.github.mustfun.mybatis.plugin.model.ModuleConfig;
import com.github.mustfun.mybatis.plugin.model.Template;
import com.github.mustfun.mybatis.plugin.model.enums.VmTypeEnums;
import com.github.mustfun.mybatis.plugin.service.MysqlService;
import com.github.mustfun.mybatis.plugin.service.DbServiceFactory;
import com.github.mustfun.mybatis.plugin.service.SqlLiteService;
import com.github.mustfun.mybatis.plugin.setting.ConnectDbSetting;
import com.github.mustfun.mybatis.plugin.ui.custom.DialogWrapperPanel;
import com.github.mustfun.mybatis.plugin.util.ConnectionHolder;
import com.github.mustfun.mybatis.plugin.util.JavaUtils;
import com.github.mustfun.mybatis.plugin.util.OrderedProperties;
import com.github.mustfun.mybatis.plugin.util.crypto.ConfigTools;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.CheckBoxList;

import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author itar
 * @version 1.0
 * @date 2018/6/12
 * @since jdk1.8
 * List<Configurable> configurableGroups = ShowSettingsUtilImpl.getConfigurables(project, true);
 *                     SettingsDialogFactory.getInstance().create(project, "MybatisLiteSettings", configurableGroups.get(0), true, true).show();
 *                     if(true) {
 *                         return;
 *                     }
 *                     SearchConfigurableByNameHelper settings1 = new SearchConfigurableByNameHelper("settings", project);
 *                     ConfigurableGroup settings = settings1.getRootGroup();
 *                     Configurable configurable = settings1.searchByName();
 *                     SettingsDialogFactory.getInstance().create(project, Arrays.asList(settings), configurable, null).show();
 */
public final class UiGenerateUtil {

    /**
     * \w????????????????????????????????????????????????\????????????
     */
    public static final String NUMBER_PATTEN = "^(\\w_)";
    private Project project;
    public static final String FILE_PROTOCOL = "file://";

    private FileEditorManager fileEditorManager;

    private ConnectDbSetting connectDbSetting;

    private UiGenerateUtil(Project project) {
        this.project = project;
        this.fileEditorManager = FileEditorManager.getInstance(project);
    }

    public static UiGenerateUtil getInstance(@NotNull Project project) {
        return new UiGenerateUtil(project);
    }

    /**
     * ?????????module???????????????module???????????????????????????
     * @param module
     * @return
     */
    public DialogWrapperPanel getCommonDialog(Module module) {
        String moduleName = project.getName();
        if(module!=null){
            moduleName = module.getName();
        }
        UiComponentFacade uiComponentFacade = UiComponentFacade.getInstance(project);
        if (null == connectDbSetting) {
            this.connectDbSetting = new ConnectDbSetting();
        }
        //???????????????????????????
        connectDbSetting.getConnectButton().addActionListener(getActionListener(moduleName));

        SqlLiteService sqlLiteService = DbServiceFactory.getInstance(project).createSqlLiteService();
        //Project??????
        VirtualFile baseDir;
        if(module!=null){
            baseDir = module.getModuleFile()!=null?module.getModuleFile().getParent():module.getModuleFile();
        }else{
            baseDir = ProjectUtil.guessProjectDir(project);
        }

        //??????dao???????????????
        fillDaoPath(uiComponentFacade, sqlLiteService, baseDir);

        //??????Mapper???????????????
        fillMapperPath(uiComponentFacade, sqlLiteService, baseDir);

        //??????model???????????????
        fillModelPath(uiComponentFacade, sqlLiteService, baseDir);

        // ??????Service???????????????
        fillServicePath(uiComponentFacade, sqlLiteService, baseDir);

        // ??????controller???????????????
        fillControllerPath(uiComponentFacade, sqlLiteService, baseDir);

        //??????ymal??????property????????????
        fillPanelText(moduleName,connectDbSetting);

        return new DialogWrapperPanel(project, true, connectDbSetting);
    }

    private void fillControllerPath(UiComponentFacade uiComponentFacade, SqlLiteService sqlLiteService, VirtualFile baseDir) {
        VirtualFile controllerPath = null;
        String poUserPreferPath = sqlLiteService.getUserPreferPathByVmType(project.getName(),VmTypeEnums.CONTROLLER);
        if (poUserPreferPath != null) {
            connectDbSetting.getControllerPositionCheckBox().setSelected(true);
            controllerPath = LocalFileSystem.getInstance().findFileByIoFile(new File(poUserPreferPath));
        }else{
            VirtualFile findPath = JavaUtils
                .getFilePattenPath(Objects.requireNonNull(baseDir), "/facade/", "/controller/", "controller.java");
            if (findPath != null) {
                controllerPath = findPath;
            }
        }
        if (controllerPath==null) {
            controllerPath = VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath() + "/src/main/java");
            controllerPath = controllerPath==null?VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath()):controllerPath;
        }
        connectDbSetting.getControllerInput().setText(Objects.requireNonNull(controllerPath).getPath());

        VirtualFile finalControllerPath = controllerPath;
        connectDbSetting.getControllerButton().addActionListener(e -> {
            VirtualFile vf = uiComponentFacade
                .showSingleFolderSelectionDialog("?????????Controller???????????????", finalControllerPath, baseDir);
            //??????????????????????????????
            String path = vf.getPath();
            System.out.println("path = " + path);
            connectDbSetting.getControllerInput().setText(path);
        });
    }

    private void fillServicePath(UiComponentFacade uiComponentFacade, SqlLiteService sqlLiteService, VirtualFile baseDir) {
        VirtualFile servicePath = null;
        String poUserPreferPath = sqlLiteService.getUserPreferPathByVmType(project.getName(), VmTypeEnums.SERVICE);
        if (poUserPreferPath != null) {
            connectDbSetting.getServicePositionCheckBox().setSelected(true);
            servicePath = LocalFileSystem.getInstance().findFileByIoFile(new File(poUserPreferPath));
        }else{
            VirtualFile findPath = JavaUtils.getFilePattenPath(baseDir, "/service/", "service.java");
            if (findPath != null) {
                servicePath = findPath;
            }
        }
        if (servicePath==null) {
            servicePath = VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath() + "/src/main/java");
            servicePath = servicePath==null?VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath() ):servicePath;
        }
        connectDbSetting.getServiceInput().setText(Objects.requireNonNull(servicePath).getPath());

        VirtualFile finalServicePath = servicePath;
        connectDbSetting.getServiceButton().addActionListener(e -> {
            VirtualFile vf = uiComponentFacade
                .showSingleFolderSelectionDialog("?????????Service???????????????", finalServicePath, baseDir);
            if (vf == null) {
                return;
            }
            //??????????????????????????????
            String path = vf.getPath();
            System.out.println("path = " + path);
            connectDbSetting.getServiceInput().setText(path);
        });
    }

    private void fillModelPath(UiComponentFacade uiComponentFacade, SqlLiteService sqlLiteService, VirtualFile baseDir) {
        VirtualFile modelPath = null;
        String poUserPreferPath = sqlLiteService.getUserPreferPathByVmType(project.getName(), VmTypeEnums.MODEL_PO);
        if (poUserPreferPath != null) {
            connectDbSetting.getModelPositionCheckBox().setSelected(true);
            modelPath = LocalFileSystem.getInstance().findFileByIoFile(new File(poUserPreferPath));
        }else{
            VirtualFile findPath = JavaUtils.getFilePattenPath(baseDir, "/model/");
            if (findPath != null) {
                modelPath = findPath;
            }
        }
        if (modelPath==null) {
            modelPath = VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath() + "/src/main/java");
            modelPath =modelPath==null?VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath()):modelPath;
        }
        connectDbSetting.getPoInput().setText(Objects.requireNonNull(modelPath).getPath());

        VirtualFile finalModelPath = modelPath;
        connectDbSetting.getPoButton().addActionListener(e -> {
            VirtualFile vf = uiComponentFacade.showSingleFolderSelectionDialog("??????????????????????????????", finalModelPath, baseDir);
            if (vf == null) {
                return;
            }
            //??????????????????????????????
            String path = vf.getPath();
            System.out.println("path = " + path);
            connectDbSetting.getPoInput().setText(path);
        });
    }

    private void fillMapperPath(UiComponentFacade uiComponentFacade, SqlLiteService sqlLiteService, VirtualFile baseDir) {
        VirtualFile mapperPath = baseDir;
        String poUserPreferPath = sqlLiteService.getUserPreferPathByVmType(project.getName(), VmTypeEnums.MAPPER);
        if (poUserPreferPath != null) {
            connectDbSetting.getMapperPositionCheckBox().setSelected(true);
            mapperPath = LocalFileSystem.getInstance().findFileByIoFile(new File(poUserPreferPath));
        }else{
            VirtualFile findPath = JavaUtils.getFilePattenPath(baseDir,  "Mapper.xml", "Dao.xml","resources/mapper","resources/dao","resources/mybatis");
            if (findPath != null) {
                mapperPath = findPath;
            }
        }
        if (mapperPath==null) {
            mapperPath = VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath() + "/src/main/resources");
            mapperPath = mapperPath ==null?VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath()):mapperPath;
        }
        connectDbSetting.getMapperInput().setText(Objects.requireNonNull(mapperPath).getPath());

        JButton mapperButton = connectDbSetting.getMapperButton();
        VirtualFile finalMapperPath = mapperPath;
        mapperButton.addActionListener(e -> {
            VirtualFile vf = uiComponentFacade
                .showSingleFolderSelectionDialog("?????????Mapper???????????????", finalMapperPath, baseDir);
            if (vf == null) {
                return;
            }
            //??????????????????????????????
            String path = vf.getPath();
            System.out.println("path = " + path);
            connectDbSetting.getMapperInput().setText(path);
        });
    }

    /**
     * ModuleRootManagerImpl.getInstance(ModuleManagerImpl.getInstance(project).getModules()[2]).getSourceRoots()
     * ??????dao???????????????
     * @param uiComponentFacade
     * @param sqlLiteService
     * @param baseDir
     */
    private void fillDaoPath(UiComponentFacade uiComponentFacade, SqlLiteService sqlLiteService, VirtualFile baseDir) {
        VirtualFile daoPath = null;
        String poUserPreferPath = sqlLiteService.getUserPreferPathByVmType(project.getName(), VmTypeEnums.DAO);
        if (poUserPreferPath != null) {
            connectDbSetting.getDaoPositionCheckBox().setSelected(true);
            daoPath = LocalFileSystem.getInstance().findFileByIoFile(new File(poUserPreferPath));
        }else{
            VirtualFile findPath = JavaUtils.getFilePattenPath(Objects.requireNonNull(baseDir), "Mapper.java", "Dao.java", "/dao/", "/dal/");
            if (findPath != null) {
                daoPath = findPath;
            }
        }
        if (daoPath==null) {
            daoPath = VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath() + "/src/main/java");
            daoPath = daoPath==null? VirtualFileManager.getInstance().findFileByUrl(FILE_PROTOCOL+baseDir.getPath()):daoPath;
        }
        connectDbSetting.getDaoInput().setText(Objects.requireNonNull(daoPath).getPath());

        JButton daoPanel = connectDbSetting.getDaoButton();
        VirtualFile finalDaoPath = daoPath;
        daoPanel.addActionListener(e -> {
            VirtualFile vf = uiComponentFacade.showSingleFolderSelectionDialog("?????????dao???????????????", finalDaoPath, baseDir);
            if (vf == null) {
                return;
            }
            //??????????????????????????????
            String path = vf.getPath();
            System.out.println("path = " + path);
            connectDbSetting.getDaoInput().setText(path);
        });
    }

    @NotNull
    private ActionListener getActionListener(String moduleName) {
        return e -> {
            connectDbSetting.getTemplateCheckbox().clear();
            connectDbSetting.getTableCheckBox().clear();
            //????????????
            String address = connectDbSetting.getAddress().getText();
            String port = connectDbSetting.getPort().getText();
            String dbName = connectDbSetting.getDbName().getText();
            if (StringUtils.isEmpty(dbName)) {
                Messages.showMessageDialog("DB?????????????????????", "?????????????????????", Messages.getInformationIcon());
                return;
            }
            String userName = connectDbSetting.getUserName().getText();
            String password = connectDbSetting.getPassword().getText();
            Integer p = null;
            if (StringUtils.isNotBlank(port)) {
                try {
                    p = Integer.parseInt(port);
                } catch (NumberFormatException e1) {
                    Messages.showMessageDialog("?????????????????????", "?????????????????????", Messages.getInformationIcon());
                    return;
                }
            }
            DbSourcePo dbSourcePo = new DbSourcePo();
            dbSourcePo.setPort(p);
            dbSourcePo.setDbAddress(address);
            dbSourcePo.setDbName(dbName);
            dbSourcePo.setUserName(userName);
            dbSourcePo.setPassword(password);
            dbSourcePo.setModuleName(moduleName);
            //???????????????
            MysqlService mysqlService = DbServiceFactory.getInstance(project).createMysqlService();
            //???????????????????????????????????????????????????????????????????????????
            final Connection[] connection = new Connection[1];
            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                connection[0] = mysqlService.getNewConnection(dbSourcePo);
            }, "?????????:?????????????????????", false, project);
            if (connection[0] == null) {
                Messages.showMessageDialog("?????????????????????", "?????????????????????", Messages.getInformationIcon());
                return;
            }
            List<LocalTable> tables = mysqlService.getTables(connection[0]);
            CheckBoxList<String> tableCheckBox = connectDbSetting.getTableCheckBox();
            for (LocalTable table : tables) {
                tableCheckBox.addItem(table.getTableName(), table.getTableName(), false);
                Pattern compile = Pattern.compile(NUMBER_PATTEN);
                Matcher matcher = compile.matcher(table.getTableName());
                if (matcher.find()) {
                    String group = matcher.group(1);
                    connectDbSetting.getTablePrefixInput().setText(group);
                }

            }
            SqlLiteService sqlLiteService = DbServiceFactory.getInstance(project).createSqlLiteService();
            //??????????????????????????????
            sqlLiteService.insertDbConnectionInfo(dbSourcePo);
            List<Template> templates = sqlLiteService.queryTemplateList();
            CheckBoxList<Template> templateCheckbox = connectDbSetting.getTemplateCheckbox();
            for (Template template : templates) {
                if(VmTypeEnums.MAPPER.getCode().equals(template.getVmType())){
                    templateCheckbox.addItem(template, template.getTepName(), true);
                }else{
                    templateCheckbox.addItem(template, template.getTepName(), false);
                }
            }
            templateCheckbox.addMouseListener(new CheckMouseListener(project));
        };
    }

    /**
     *  @param module
     * @param connectDbSetting
     */
    private void fillPanelText(String module, ConnectDbSetting connectDbSetting) {
        //?????????????????????????????????
        if (readFromConnectLog(module,connectDbSetting)) {
            return;
        }
        Pair<Boolean, Object> pair = ConnectionHolder.getInstance(project).getConfigOrOne(module);
        if(pair==null){
            return ;
        }
        Object second = pair.getSecond();
        if(second == null){
            return ;
        }
        ModuleConfig config = (ModuleConfig) second;
        insertPanelUseConfig(connectDbSetting, config);
    }

    private void insertPanelUseConfig(ConnectDbSetting connectDbSetting, ModuleConfig config) {
        connectDbSetting.getUserName().setText(config.getUserName());
        String password = config.getPassword();
        connectDbSetting.getPassword().setText(password);
        connectDbSetting.getAddress().setText(config.getDbAddress());
        connectDbSetting.getPort().setText(config.getPort()+"");
        connectDbSetting.getDbName().setText(config.getDbName());
    }

    /**
     * ?????????????????????????????????????????????
     */
    private boolean readFromConnectLog(String module, ConnectDbSetting connectDbSetting) {
        SqlLiteService sqlLiteService = DbServiceFactory.getInstance(project).createSqlLiteService();
        DbSourcePo dbSourcePo = sqlLiteService.queryLatestConnectLog(module);
        if (dbSourcePo != null) {
            connectDbSetting.getDbName().setText(dbSourcePo.getDbName());
            connectDbSetting.getAddress().setText(dbSourcePo.getDbAddress());
            connectDbSetting.getUserName().setText(dbSourcePo.getUserName());
            connectDbSetting.getPassword().setText(dbSourcePo.getPassword());
            return true;
        } else {
            return false;
        }
    }

    private void insertPanelUseProperties(ConnectDbSetting connectDbSetting, File file) {
        //??????properties????????????
        try {
            OrderedProperties properties = new OrderedProperties();
            properties.load(new FileInputStream(file));
            boolean findUserName = true, findPassword = true, findUrl = true;
            for (Map.Entry<Object, Object> objectObjectEntry : properties.entrySet()) {
                Object key = objectObjectEntry.getKey();
                boolean isMatchUserName = Pattern.matches(".*?druid(.*)(username$)", (String) key);
                if (isMatchUserName && findUserName) {
                    findUserName = false;
                    Object value = objectObjectEntry.getValue();
                    connectDbSetting.getUserName().setText((String) value);
                }
                boolean isMatchPassword = Pattern.matches(".*?druid(.*)(password$)", (String) key);
                if (isMatchPassword && findPassword) {
                    findPassword = false;
                    String password = (String) objectObjectEntry.getValue();
                    if (password != null && password.length() >= 64) {
                        password = ConfigTools.decrypt(password);
                    }
                    connectDbSetting.getPassword().setText(password);
                }
                boolean isMatchUrl = Pattern.matches(".*?druid(.*)(url$)", (String) key);
                if (isMatchUrl && findUrl) {
                    findUrl = false;
                    Object value = objectObjectEntry.getValue();
                    String[] s = ((String) value).split("/");
                    String[] split = s[2].split(":");
                    connectDbSetting.getAddress().setText(split[0]);
                    connectDbSetting.getPort().setText(split[1]);
                    String s1 = s[3].split("\\?")[0];
                    connectDbSetting.getDbName().setText(s1);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insertPanelUseYaml(ConnectDbSetting connectDbSetting, File ymlFile) {
        try {
            Yaml yaml = new Yaml();
            //????????????
            Iterable<Object> result = yaml.loadAll(new FileInputStream(ymlFile));
            while (result.iterator().hasNext()) {
                Object next = result.iterator().next();
                String username = JavaUtils.findYamlValueByTag((LinkedHashMap) next, "username");
                connectDbSetting.getUserName().setText(username == null ? "root" : username);
                String password = JavaUtils.findYamlValueByTag((LinkedHashMap) next, "password");
                if (password != null && password.length() >= 64) {
                    password = ConfigTools.decrypt(password);
                }
                connectDbSetting.getPassword().setText(password);
                String url = JavaUtils.findYamlValueByTag((LinkedHashMap) next, "url");
                if (url != null && url.contains("jdbc:mysql")) {
                    String[] s = url.split("/");
                    String[] split = s[2].split(":");
                    connectDbSetting.getAddress().setText(split[0]);
                    connectDbSetting.getPort().setText(split[1]);
                    String s1 = s[3].split("\\?")[0];
                    connectDbSetting.getDbName().setText(s1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * private void showProgressBar() {
     *      *         JPanel panel = new JPanel();
     *      *         JProgressBar pb1 = new JProgressBar(0, 100);
     *      *         //progressTimerRequest = new ProgressTimerRequest(pb1);
     *      *         ProgressPanel progressPanel = ProgressPanel.getProgressPanel(pb1);
     *      *         panel.add(UI.PanelFactory.grid().add(UI.PanelFactory.panel(pb1).
     *      *                         withLabel("Label 1.1").
     *      *                         withCancel(() ->{
     *      *
     *      *                         }).andCancelText("Stop")).createPanel());
     *      *     }
     */

}
