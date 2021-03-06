package com.github.mustfun.mybatis.plugin.contributor;

import com.github.mustfun.mybatis.plugin.dom.model.IdDomElement;
import com.github.mustfun.mybatis.plugin.init.InitMybatisLiteActivity;
import com.github.mustfun.mybatis.plugin.model.DbSourcePo;
import com.github.mustfun.mybatis.plugin.model.LocalColumn;
import com.github.mustfun.mybatis.plugin.model.LocalTable;
import com.github.mustfun.mybatis.plugin.model.ModuleConfig;
import com.github.mustfun.mybatis.plugin.service.DbServiceFactory;
import com.github.mustfun.mybatis.plugin.setting.MybatisLiteSetting;
import com.github.mustfun.mybatis.plugin.ui.UiComponentFacade;
import com.github.mustfun.mybatis.plugin.util.*;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.database.dataSource.DatabaseDriver;
import com.intellij.database.dataSource.DatabaseDriverManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.*;

import static com.github.mustfun.mybatis.plugin.util.MybatisConstants.MODULE_DB_CONFIG;


/**
 * LocalDataSource localDataSource = LocalDataSource.create("test", "com.mysql.jdbc.Driver", dbSourcePo.getDbAddress(), dbSourcePo.getUserName());
 *         DatabaseCredentials credentials =DatabaseCredentials.getInstance();
 *         credentials.setPassword(localDataSource, new OneTimeString("root"));
 *         DatabaseSessionManager.Facade facade = DatabaseSessionManager.facade(project,localDataSource , credentials, null, null, DGDepartment.UNKNOWN);
 *         try {
 *             GuardedRef<DatabaseConnection> connect = facade.connect();
 *             DatabaseConnection databaseConnection = connect.get();
 *
 *         } catch (Exception e) {
 *             e.printStackTrace();
 *         }
 *
 * @date 2020-03-02
 * @author  itar
 * @function ???sql?????????????????????
 */
public class SqlFieldCompletionContributor extends CompletionContributor {

    private static  final  Logger logger = LoggerFactory.getLogger(SqlFieldCompletionContributor.class);

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
        @NotNull final CompletionResultSet result) {
        Map<String, String> valueMap = MybatisLiteSetting.getInstance().getValueMap();
        if (!MybatisConstants.TRUE.equalsIgnoreCase(valueMap.get(MybatisConstants.SQL_FIELD_STATUS))) {
            return ;
        }
        //sql???????????????????????????????????????smart
        if (parameters.getCompletionType() != CompletionType.BASIC) {
            return;
        }

        PsiElement position = parameters.getPosition();
        //???????????????????????????xmlFile???????????? position.getContainingFile(); ?????????????????????????????????????????????
        //sql???inject???xml?????????
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject()).getTopLevelFile(position);
        if (MybatisDomUtils.isMybatisFile(topLevelFile)) {
            if (shouldAddElement(position.getContainingFile(), parameters.getOffset())) {
                //new InitMybatisLiteActivity().runActivity(position.getProject()); //???????????????
                process(topLevelFile, result, position);
            }
        }
    }

    private void process(PsiFile xmlFile, CompletionResultSet result, PsiElement position) {
        //???????????????????????????documentWindows
        VirtualFile virtualFile = position.getContainingFile().getVirtualFile();
        DocumentWindow documentWindow = ((VirtualFileWindow) virtualFile).getDocumentWindow();
        int offset = documentWindow.injectedToHost(position.getTextOffset());
        Optional<IdDomElement> idDomElement = MapperUtils.findParentIdDomElement(xmlFile.findElementAt(offset));
        if (idDomElement.isPresent()) {
            addSqlFieldParameter(position.getProject(), result, idDomElement.get(),position);
            result.stopHere();
        }
    }

    /**
     * ???????????????????????????
     * @param project
     * @param result
     * @param idDomElement
     * @param position
     */
    @SuppressWarnings("unchecked")
    private void addSqlFieldParameter(Project project, CompletionResultSet result, IdDomElement idDomElement, PsiElement position) {
        String tableName = SqlUtil.getTableNameFromSql(idDomElement,position);
        if (tableName==null){
          logger.warn("???Mybatis Lite??????sql????????????????????????");
          return ;
        }
        Pair<Boolean, Object> configOrOne = ConnectionHolder.getInstance(project).getConfigOrOne(Objects.requireNonNull(idDomElement.getModule()).getName());
        if (configOrOne.getSecond()==null){
            configOrOne = Pair.pair(false,ConnectionHolder.getInstance(project).getConfig(project.getName()));
            if (configOrOne.getSecond()==null) {
                logger.warn("???Mybatis Lite??????????????????????????????????????????");
                return;
            }
        }
        //????????????module?????????
        DbSourcePo dbSourcePo= (DbSourcePo) configOrOne.getSecond();

        DatabaseDriverManager instance = DatabaseDriverManager.getInstance();
        Collection<? extends DatabaseDriver> drivers = instance.getDrivers();
        DatabaseDriver driver = drivers.stream().filter(x -> x.getName().equals("MySQL")).findAny().orElseGet(null);

        Connection connection = new DbUtil(dbSourcePo).getConnectionUseDriver(project, DigestUtils.md5Hex(dbSourcePo.getDbAddress()+dbSourcePo.getPort()+dbSourcePo.getDbName()), driver, dbSourcePo);

        if (connection==null){
            logger.warn("???Mybatis Lite???===================??????????????????");
            UiComponentFacade uiComponentFacade = UiComponentFacade.getInstance(project);
            uiComponentFacade.buildNotify(project, "Mybatis Lite", "??????????????????????????????");
            return;
        }

        List<LocalTable> tables = ConnectionHolder.getInstance(project).getTableCache(tableName);
        if (CollectionUtils.isEmpty(tables)){
            tables = DbServiceFactory.getInstance(project).createMysqlService().getTables(connection);
            ConnectionHolder.getInstance(project).putTableCache(tableName, tables);
        }
        for (LocalTable table : tables) {
            if (table.getTableName().equals(tableName)){
                addParameterToResult(table, result);
            }
        }



    }

    private void addParameterToResult(LocalTable table, CompletionResultSet result) {
        List<LocalColumn> columnList = table.getColumnList();
        for (LocalColumn localColumn : columnList) {
            LookupElementBuilder builder = LookupElementBuilder.create(localColumn.getColumnName())
                    .withIcon(Icons.FIELD_COMPLETION_ICON).withTypeText(table.getTableName());
            result.addElement(builder);
        }
    }

    /**
     * @param file
     * @param offset sql?????????2??????????????????????????????offset???????????????offset??????
     * @return
     * @function ???????????????
     */
    private boolean shouldAddElement(PsiFile file, int offset) {
        String text = file.getText();
        String s = String.valueOf(text.charAt(offset - 2));
        if(" ".equals(s) || ",".equals(s)){
            return true;
        }
        return false;
    }
}