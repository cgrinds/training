package training.actions;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.learn.*;
import training.learn.dialogs.SdkModuleProblemDialog;
import training.learn.dialogs.SdkProjectProblemDialog;
import training.learn.exceptons.*;
import training.learn.ide.IDEAProductBuilder;
import training.learn.ide.IProductBuilder;
import training.ui.LearnToolWindowFactory;
import training.util.MyClassLoader;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;


/**
 * Created by karashevich on 20/05/16.
 */
public class OpenLessonAction extends AnAction {


    public static DataKey<Lesson> LESSON_DATA_KEY = DataKey.create("LESSON_DATA_KEY");

    @Override
    public void actionPerformed(AnActionEvent e) {

        final Lesson lesson = e.getData(LESSON_DATA_KEY);
        final Project project = e.getProject();
        final IProductBuilder productBuilder = IProductBuilder.create();

        try {
            if (lesson != null) {
                openLesson(project, lesson);
            } else {
                //in case of starting from Welcome Screen
                Project myLearnProject = productBuilder.initLearnProject(null);
                assert myLearnProject != null;
                openLearnToolWindowAndShowModules(myLearnProject);
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    public synchronized void openLesson(@Nullable Project project, final Lesson lesson) throws BadModuleException, BadLessonException, IOException, FontFormatException, InterruptedException, ExecutionException, LessonIsOpenedException {

        try {
            CourseManager.getInstance().setLastActivityTime(System.currentTimeMillis());

            if (lesson.isOpen()) throw new LessonIsOpenedException(lesson.getName() + " is opened");

            //If lesson doesn't have parent module
            if (lesson.getModule() == null)
                throw new BadLessonException("Unable to open lesson without specified module");

            final Project myProject = project;
            final String scratchFileName = "Learning";
            final IProductBuilder productBuilder = IProductBuilder.create();
            VirtualFile vf = null;
            final Project learnProject = CourseManager.getInstance().getLearnProject();
            if (lesson.getModule().moduleType == Module.ModuleType.SCRATCH) {
                productBuilder.checkEnvironment(project, lesson.getModule());
                vf = getScratchFile(myProject, lesson, scratchFileName);
            } else {
                //0. learnProject == null but this project is LearnProject then just getFileInLearnProject
                if (learnProject == null && getCurrentProject().getName().equals(productBuilder.getLearnProjectName())) {
                    CourseManager.getInstance().setLearnProject(getCurrentProject());
                    vf = getFileInLearnProject(lesson);

                    //1. learnProject == null and current project has different name then initLearnProject and register post startup open lesson
                } else if (learnProject == null && !getCurrentProject().getName().equals(productBuilder.getLearnProjectName())) {
                    Project myLearnProject = productBuilder.initLearnProject(myProject);
                    if (myLearnProject == null) return; // in case of user aborted to create a LearnProject
                    openLessonWhenLearnProjectStart(lesson, myLearnProject);
                    return;
                    //2. learnProject != null and learnProject is disposed then reinitProject and getFileInLearnProject
                } else if (learnProject.isDisposed()) {
                    Project myLearnProject = productBuilder.initLearnProject(myProject);
                    if (myLearnProject == null) return; // in case of user aborted to create a LearnProject
                    openLessonWhenLearnProjectStart(lesson, myLearnProject);
                    return;
                    //3. learnProject != null and learnProject is opened but not focused then focus Project and getFileInLearnProject
                } else if (learnProject.isOpen() && !getCurrentProject().equals(learnProject)) {
                    vf = getFileInLearnProject(lesson);
                    //4. learnProject != null and learnProject is opened and focused getFileInLearnProject
                } else if (learnProject.isOpen() && getCurrentProject().equals(learnProject)) {
                    vf = getFileInLearnProject(lesson);
                } else {
                    throw new Exception("Unable to start Learn project");
                }
            }

            if (vf == null) return; //if user aborts opening lesson in LearnProject or Virtual File couldn't be computed
            if (lesson.getModule().moduleType != Module.ModuleType.SCRATCH)
                project = CourseManager.getInstance().getLearnProject();

            //open next lesson if current is passed
            final Project currentProject = project;
            CourseManager.getInstance().setLessonView();

            lesson.onStart();

            lesson.addLessonListener(new LessonListenerAdapter() {
                @Override
                public void lessonNext(Lesson lesson) throws BadLessonException, ExecutionException, IOException, FontFormatException, InterruptedException, BadModuleException, LessonIsOpenedException {
                    if (lesson.getModule() == null) return;

                    if (lesson.getModule().hasNotPassedLesson()) {
                        Lesson nextLesson = lesson.getModule().giveNotPassedAndNotOpenedLesson();
                        if (nextLesson == null)
                            throw new BadLessonException("Unable to obtain not passed and not opened lessons");
                        openLesson(currentProject, nextLesson);
                    }
                }
            });

            final String target;
            if (lesson.getTargetPath() != null) {
                InputStream is = MyClassLoader.getInstance().getResourceAsStream(lesson.getModule().getAnswersPath() + lesson.getTargetPath());
                if (is == null) throw new IOException("Unable to get feedback for \"" + lesson.getName() + "\" lesson");
                target = new Scanner(is).useDelimiter("\\Z").next();
            } else {
                target = null;
            }


            //Dispose balloon while scratch file is closing. InfoPanel still exists.
            project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
                @Override
                public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                    lesson.close();
                }
            });

            //to start any lesson we need to do 4 steps:
            //1. open editor or find editor
            TextEditor textEditor = null;
            if (FileEditorManager.getInstance(project).isFileOpen(vf)) {
                FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(vf);
                for (FileEditor fileEditor : editors) {
                    if (fileEditor instanceof TextEditor) {
                        textEditor = (TextEditor) fileEditor;
                    }
                }
            }
            if (textEditor == null) {
                final java.util.List<FileEditor> editors = FileEditorManager.getInstance(project).openEditor(new OpenFileDescriptor(project, vf), true);
                for (FileEditor fileEditor : editors) {
                    if (fileEditor instanceof TextEditor) {
                        textEditor = (TextEditor) fileEditor;
                    }
                }
            }
            if (textEditor.getEditor().isDisposed()) {
                throw new Exception("Editor is already disposed!!!");
            }

            //2. set the focus on this editor
            //FileEditorManager.getInstance(project).setSelectedEditor(vf, TextEditorProvider.getInstance().getEditorTypeId());
            FileEditorManager.getInstance(project).openEditor(new OpenFileDescriptor(project, vf), true);

            //3. update tool window
            CourseManager.getInstance().getLearnPanel().clear();


            //4. Process lesson
            LessonProcessor.process(project, lesson, textEditor.getEditor(), target);

        } catch (NoSdkException | InvalidSdkException noSdkException) {
            Messages.showMessageDialog(project, LearnBundle.message("dialog.noSdk.message"), LearnBundle.message("dialog.noSdk.title"), Messages.getErrorIcon());
            ProjectSettingsService.getInstance(project).chooseAndSetSdk();
            openLesson(project, lesson);
        } catch (NoJavaModuleException noJavaModuleException) {
            showModuleProblemDialog(project);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Project getCurrentProject() {
        return ProjectManager.getInstance().getOpenProjects()[0];
    }

    private void openLearnToolWindowAndShowModules(@NotNull Project myLearnProject) {
        if (myLearnProject.isOpen() && myLearnProject.isInitialized()) {
            showModules(myLearnProject);
        } else {
            StartupManager.getInstance(myLearnProject).registerPostStartupActivity(() -> showModules(myLearnProject));
        }
    }

    private void showModules(Project project) {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        final ToolWindow learnToolWindow = toolWindowManager.getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW);
        if (learnToolWindow != null) {
            learnToolWindow.show(null);
            try {
                CourseManager.getInstance().setModulesView();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void openLessonWhenLearnProjectStart(@Nullable Lesson lesson, Project myLearnProject) {
        StartupManager.getInstance(myLearnProject).registerPostStartupActivity(() -> {
            final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myLearnProject);
            final ToolWindow learnToolWindow = toolWindowManager.getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW);
            if (learnToolWindow != null) {
                learnToolWindow.show(null);
                try {
                    CourseManager.getInstance().setLessonView();
                    CourseManager.getInstance().openLesson(myLearnProject, lesson);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @NotNull
    private VirtualFile getScratchFile(@NotNull final Project project, @Nullable Lesson lesson, @NotNull final String filename) throws IOException {
        VirtualFile vf = null;
        assert lesson != null;
        assert lesson.getModule() != null;
        String myLanguage = lesson.getLang() != null ? lesson.getLang() : "Swift";

        final Language languageByID = Language.findLanguageByID(myLanguage);
        if (CourseManager.getInstance().mapModuleVirtualFile.containsKey(lesson.getModule())) {
            vf = CourseManager.getInstance().mapModuleVirtualFile.get(lesson.getModule());
            ScratchFileService.getInstance().getScratchesMapping().setMapping(vf, languageByID);
        }
        if (vf == null || !vf.isValid()) {
            //while module info is not stored

            //find file if it is existed
            vf = ScratchFileService.getInstance().findFile(ScratchRootType.getInstance(), filename, ScratchFileService.Option.existing_only);
            if (vf != null) {
                FileEditorManager.getInstance(project).closeFile(vf);
                ScratchFileService.getInstance().getScratchesMapping().setMapping(vf, languageByID);
            }

            if (vf == null || !vf.isValid()) {
                vf = ScratchRootType.getInstance().createScratchFile(project, filename, languageByID, "");
                final VirtualFile finalVf = vf;
                assert vf != null;
            }
            CourseManager.getInstance().registerVirtualFile(lesson.getModule(), vf);
        }
        return vf;
    }

    private void showSdkProblemDialog(Project project, String sdkMessage) {
        final SdkProjectProblemDialog dialog = new SdkProjectProblemDialog(project, sdkMessage);
        dialog.show();
    }

    private void showModuleProblemDialog(Project project) {
        final SdkModuleProblemDialog dialog = new SdkModuleProblemDialog(project);
        dialog.show();
    }

    private VirtualFile getFileInLearnProject(Lesson lesson) throws IOException {

        return ApplicationManager.getApplication().runWriteAction((Computable<VirtualFile>) () -> {
            final Project learnProject = CourseManager.getInstance().getLearnProject();
            final IProductBuilder builder = IProductBuilder.create();
            assert learnProject != null;

            final VirtualFile sourceRootFile = ProjectRootManager.getInstance(learnProject).getContentSourceRoots()[0];

            String fileName = builder.getScratchName();
            if (lesson.getModule() != null) {
                String extensionFile = builder.getScratchExtension();
                if (lesson.getLang() != null) extensionFile = "." + lesson.getLang().toLowerCase();
                fileName = lesson.getModule().getNameWithoutWhitespaces() + extensionFile;
            }
            VirtualFile lessonVirtualFile = sourceRootFile.findChild(fileName);
            if (lessonVirtualFile == null) {

                try {
                    lessonVirtualFile = sourceRootFile.createChildData(this, fileName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            CourseManager.getInstance().registerVirtualFile(lesson.getModule(), lessonVirtualFile);
            return lessonVirtualFile;
        });
    }

}
