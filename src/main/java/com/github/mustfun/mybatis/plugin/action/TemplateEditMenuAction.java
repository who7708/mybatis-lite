package com.github.mustfun.mybatis.plugin.action;

import com.github.mustfun.mybatis.plugin.model.Template;
import com.github.mustfun.mybatis.plugin.model.enums.VmTypeEnums;
import com.github.mustfun.mybatis.plugin.service.DbServiceFactory;
import com.github.mustfun.mybatis.plugin.service.SqlLiteService;
import com.github.mustfun.mybatis.plugin.setting.TemplateListForm;
import com.github.mustfun.mybatis.plugin.setting.TemplateListForm.MyTableModel;
import com.github.mustfun.mybatis.plugin.ui.custom.TemplateListPanel;
import com.github.mustfun.mybatis.plugin.util.ConnectionHolder;
import com.github.mustfun.mybatis.plugin.util.Icons;
import com.github.mustfun.mybatis.plugin.util.MybatisConstants;
import com.intellij.AppTopics;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.table.JBTable;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author itar
 * @version 1.0
 * @date 2018/6/12
 * @since 1.0
 */
public class TemplateEditMenuAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        TemplateListForm templateListForm = new TemplateListForm(project);
        JBTable templateList = templateListForm.getTemplateList();
        String[] headName = {"??????ID","????????????", "?????????", "????????????", "??????",""};

        SqlLiteService sqlLiteService = DbServiceFactory.getInstance(Objects.requireNonNull(project)).createSqlLiteService();

        List<Template> templates = sqlLiteService.queryTemplateList();
        Object[][] obj = new Object[templates.size()][];
        for (int i = 0; i < templates.size(); i++) {
            Template template = templates.get(i);
            JButton button = new JButton("??????");
            JButton button2 = new JButton("?????????");
            Object[] objects = new Object[6];
            objects[0] = template.getId();
            objects[1] = template.getTepName();
            objects[2] = template.getCreateBy() == null ? "" : template.getCreateBy();
            objects[3] = VmTypeEnums.findVmNameByVmType(template.getVmType()).getMgs();
            objects[4] = button;
            objects[5] = button2;
            obj[i] = objects;
        }
        templateList.setModel(new MyTableModel(headName, obj));
        templateListForm.getMainPanel().validate();
        TemplateListPanel templateListPanel = new TemplateListPanel(project, true, templateListForm);
        templateListPanel.setTitle("????????????");
        addHandler(templateList, templates, project, templateListPanel);
        templateListPanel.show();
    }

    private void addHandler(JBTable table, List<Template> templates, Project project,
        TemplateListPanel templateListPanel) {
        //????????????
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.getSelectedRow();
                int column = table.getSelectedColumn();
                if (column == 4) {
                    //??????button??????????????????...
                    Integer id = (Integer) table.getValueAt(row, 0);
                    Template editingTemplate = DbServiceFactory.getInstance(project).createSqlLiteService().queryTemplateById(id);
                    if (editingTemplate==null){
                        return ;
                    }
                    AtomicReference<PsiDirectory> psiDirectory = new AtomicReference<>();
                    AtomicReference<PsiFile> file = new AtomicReference<>();
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        //?????????????????????????????????
                        VirtualFile vFile = null;
                        try {
                            vFile = VfsUtil.createDirectoryIfMissing(MybatisConstants.TEMP_DIR_PATH + "/tmp");
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        PsiFile psiFile = PsiFileFactory.getInstance(project)
                            .createFileFromText(editingTemplate.getTepName() + ".vm", JavaFileType.INSTANCE,
                                editingTemplate.getTepContent().replaceAll("\r\n", "\n"));
                        psiDirectory.set(PsiDirectoryFactory.getInstance(project).createDirectory(vFile));
                        file.set(psiDirectory.get().findFile(editingTemplate.getTepName() + ".vm"));
                        if (file.get() != null) {
                            psiDirectory.get().delete();
                        }
                        psiDirectory.get().add(psiFile);
                        //?????????
                        PsiFile realPsiFile = Arrays.stream(psiDirectory.get().getFiles())
                            .filter(x -> x.getName().equals(editingTemplate.getTepName() + ".vm")).findAny().get();
                        new OpenFileDescriptor(project, realPsiFile.getVirtualFile()).navigateInEditor(project, true);

                        templateListPanel.doCancelAction();
                    });

                    /**
                     * ??????????????????
                     */
                    ApplicationManager.getApplication().getMessageBus().connect()
                        .subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {

                            @Override
                            public void beforeDocumentSaving(@NotNull final Document document) {
                                final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

                                if (openProjects.length > 0) {
                                    final PsiFile psiFile = PsiDocumentManager.getInstance(openProjects[0])
                                        .getPsiFile(document);
                                    if (psiFile == null) {
                                        return;
                                    }
                                    String text = psiFile.getText();
                                    if (!psiFile.getVirtualFile().getName().startsWith(editingTemplate.getTepName())) {
                                        return;
                                    }
                                    if (StringUtils.isEmpty(document.getText())) {
                                        Messages.showErrorDialog("????????????????????????", "??????????????????");
                                        return;
                                    }
                                    if (StringUtils.isEmpty(text)) {
                                        return;
                                    }
                                    if (DigestUtils.md5Hex(text)
                                        .equals(DigestUtils.md5Hex(editingTemplate.getTepContent()))) {
                                        return;
                                    }
                                    Connection connection = ConnectionHolder.getInstance(project).getConnection(MybatisConstants.SQL_LITE_CONNECTION);
                                    if (connection == null) {
                                        return;
                                    }
                                    SqlLiteService instance = DbServiceFactory.getInstance(project).createSqlLiteService();
                                    Template updatePo = new Template();
                                    updatePo.setId(editingTemplate.getId());
                                    updatePo.setTepContent(text);
                                    instance.updateTemplate(updatePo);
                                }
                            }
                        });
                }
                //?????????????????????
                if (column==5){
                    if (Messages.showYesNoDialog("???????????????????????????????", "Mybatis Lite", Icons.MYBATIS_LOGO_MINI)==Messages.NO){
                        return;
                    }
                    Integer id = (Integer)table.getValueAt(row, 0);
                    SqlLiteService innerSqlLiteService = DbServiceFactory.getInstance(project).createInnerSqlLiteService();
                    Template template = innerSqlLiteService.queryTemplateById(id);
                    SqlLiteService sqlLiteService = DbServiceFactory.getInstance(project).createSqlLiteService();
                    sqlLiteService.updateTemplate(template);
                    Messages.showMessageDialog("??????????????????", "Mybatis Lite", Icons.MYBATIS_LOGO_MINI);
                }
            }
        });
    }


}
