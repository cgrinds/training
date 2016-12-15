package training.learn.ide;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.JdkBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.learn.CourseManager;
import training.learn.Module;
import training.learn.NewLearnProjectUtil;
import training.learn.exceptons.InvalidSdkException;
import training.learn.exceptons.NoJavaModuleException;
import training.learn.exceptons.NoSdkException;
import training.learn.exceptons.OldJdkException;
import training.util.JdkSetupUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class IDEAProductBuilder implements IProductBuilder {

    @NotNull
    private static Sdk getJavaSdk() {

        //check for stored jdk
        ArrayList<Sdk> jdkList = getJdkList();
        if (!jdkList.isEmpty()) {
            for (Sdk sdk : jdkList) {
                if (JavaSdk.getInstance().getVersion(sdk) != null && JavaSdk.getInstance().getVersion(sdk).isAtLeast(JavaSdkVersion.JDK_1_6)) {
                    return sdk;
                }
            }
        }

        //if no predefined jdks -> add bundled jdk to available list and return it
        JavaSdk javaSdk = JavaSdk.getInstance();

        ArrayList<JdkBundle> bundleList = JdkSetupUtil.findJdkPaths().toArrayList();
        //we believe that Idea has at least one bundled jdk
        JdkBundle jdkBundle = bundleList.get(0);
        String jdkBundleLocation = JdkSetupUtil.getJavaHomePath(jdkBundle);
        String jdk_name = "JDK_" + jdkBundle.getVersion().toString();
        final Sdk newJdk = javaSdk.createJdk(jdk_name, jdkBundleLocation, false);

        final Sdk foundJdk = ProjectJdkTable.getInstance().findJdk(newJdk.getName(), newJdk.getSdkType().getName());
        if (foundJdk == null) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                ProjectJdkTable.getInstance().addJdk(newJdk);
            });
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
            SdkModificator modificator = newJdk.getSdkModificator();
            JavaSdkImpl.attachJdkAnnotations(modificator);
            modificator.commitChanges();
        });
        return newJdk;

    }

    @NotNull
    private static ArrayList<Sdk> getJdkList() {

        ArrayList<Sdk> compatibleJdks = new ArrayList<>();

        SdkType type = JavaSdk.getInstance();
        final Sdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
        for (Sdk projectJdk : allJdks) {
            if (isCompatibleJdk(projectJdk, type)) {
                compatibleJdks.add(projectJdk);
            }
        }
        return compatibleJdks;
    }

    private static boolean isCompatibleJdk(final Sdk projectJdk, final @Nullable SdkType type) {
        return type == null || projectJdk.getSdkType() == type;
    }

    /**
     * checking environment to start learning plugin. Checking SDK.
     *
     * @param project where lesson should be started
     * @param module  learning module
     * @throws OldJdkException     - if project JDK version is not enough for this module
     * @throws InvalidSdkException - if project SDK is not suitable for module
     */
    public void checkEnvironment(Project project, @Nullable Module module) throws OldJdkException, InvalidSdkException, NoSdkException, NoJavaModuleException {

        if (module == null) return;

        final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (projectJdk == null) throw new NoSdkException();

        final SdkTypeId sdkType = projectJdk.getSdkType();
        if (module.getSdkType() == Module.ModuleSdkType.JAVA) {
            if (sdkType instanceof JavaSdk) {
                final JavaSdkVersion version = ((JavaSdk) sdkType).getVersion(projectJdk);
                if (version != null) {
                    if (!version.isAtLeast(JavaSdkVersion.JDK_1_6))
                        throw new OldJdkException(JavaSdkVersion.JDK_1_6.toString());
                    try {
                        checkJavaModule(project);
                    } catch (NoJavaModuleException e) {
                        throw e;
                    }
                }
            } else if (sdkType.getName().equals("IDEA JDK")) {
                try {
                    checkJavaModule(project);
                } catch (NoJavaModuleException e) {
                    throw e;
                }
            } else {
                throw new InvalidSdkException("Please use at least JDK 1.6 or IDEA SDK with corresponding JDK");
            }
        }
    }

    private static void checkJavaModule(Project project) throws NoJavaModuleException {

        if (ModuleManager.getInstance(project).getModules().length == 0) {
            throw new NoJavaModuleException();
        }

    }


    //Interface
    @NotNull
    private Sdk getProjectSdkInWA() {
        final Sdk newJdk;
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            newJdk = ApplicationManager.getApplication().runWriteAction((Computable<Sdk>) () -> {
                return getJavaSdk();
            });
        } else {
            newJdk = getJavaSdk();
        }
        return newJdk;
    }


    private Project createLearnProject(@NotNull String projectName, @Nullable Project projectToClose) throws IOException {
        final Sdk projectSdk = getProjectSdkInWA();
        final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();

        String allProjectsDir = "/Users/jetbrains/IdeaProjects";
        allProjectsDir = ProjectUtil.getBaseDir();
        final ProjectBuilder projectBuilder = new JavaModuleBuilder();

        try {
            String projectFilePath = allProjectsDir + //IdeaProjects dir
                    File.separator + projectName;//Project dir
            File projectDir = new File(projectFilePath).getParentFile();        //dir where project located
            FileUtil.ensureExists(projectDir);
            final File ideaDir = new File(projectFilePath, Project.DIRECTORY_STORE_FOLDER);
            FileUtil.ensureExists(ideaDir);

            final Project newProject;

            if (!projectBuilder.isUpdate()) {
                newProject = projectBuilder.createProject(projectName, projectFilePath);
            } else {
                newProject = projectToClose;
            }

            if (newProject == null) return projectToClose;


            CommandProcessor.getInstance().executeCommand(newProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
                NewProjectUtil.applyJdkToProject(newProject, projectSdk);
            }), null, null);

            if (!ApplicationManager.getApplication().isUnitTestMode()) {
                newProject.save();
            }

            if (!projectBuilder.validate(projectToClose, newProject)) {
                return projectToClose;
            }
            if (newProject != projectToClose && !ApplicationManager.getApplication().isUnitTestMode() && projectToClose != null) {
                NewProjectUtil.closePreviousProject(projectToClose);
            }
            projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER);

            if (newProject != projectToClose) {
                ProjectUtil.updateLastProjectLocation(projectFilePath);

                if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) {
                    IdeFocusManager instance = IdeFocusManager.findInstance();
                    IdeFrame lastFocusedFrame = instance.getLastFocusedFrame();
                    if (lastFocusedFrame instanceof IdeFrameEx) {
                        boolean fullScreen = ((IdeFrameEx) lastFocusedFrame).isInFullScreen();
                        if (fullScreen) {
                            newProject.putUserData(IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN, Boolean.TRUE);
                        }
                    }
                }
                if (ApplicationManager.getApplication().isUnitTestMode()) return newProject;
                else projectManager.openProject(newProject);
            }

            newProject.save();

            return newProject;
        } finally {
            projectBuilder.cleanup();
        }
    }

    @Override
    public IElementType getEndOfLineComment() {
        return TokenType.BAD_CHARACTER;
    }

    @Override
    @Nullable
    public Project initLearnProject(Project projectToClose) {
        Project myLearnProject = null;

        //if projectToClose is open
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        for (Project openProject : openProjects) {
            final String name = openProject.getName();
            if (name.equals(getLearnProjectName())) {
                myLearnProject = openProject;
                if (ApplicationManager.getApplication().isUnitTestMode()) return openProject;
            }
        }
        if (myLearnProject == null || myLearnProject.getProjectFile() == null) {

            if (!ApplicationManager.getApplication().isUnitTestMode() && projectToClose != null)
                if (!NewLearnProjectUtil.showDialogOpenLearnProject(projectToClose))
                    return null; //if user abort to open lesson in a new Project
            if (CourseManager.getInstance().getLearnProjectPath() != null) {
                try {
                    myLearnProject = ProjectManager.getInstance().loadAndOpenProject(CourseManager.getInstance().getLearnProjectPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    myLearnProject = createLearnProject(getLearnProjectName(), projectToClose);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //Set language level for LearnProject
            LanguageLevelProjectExtensionImpl.getInstanceImpl(myLearnProject).setCurrentLevel(LanguageLevel.JDK_1_6);
        }

        if (myLearnProject != null) {

            CourseManager.getInstance().setLearnProject(myLearnProject);

            assert CourseManager.getInstance().getLearnProject() != null;
            assert CourseManager.getInstance().getLearnProject().getProjectFile() != null;
            assert CourseManager.getInstance().getLearnProject().getProjectFile().getParent() != null;
            assert CourseManager.getInstance().getLearnProject().getProjectFile().getParent().getParent() != null;

            CourseManager.getInstance().setLearnProjectPath(CourseManager.getInstance().getLearnProject().getBasePath());
            //Hide LearnProject from Recent projects
            RecentProjectsManager.getInstance().removePath(CourseManager.getInstance().getLearnProject().getPresentableUrl());

            return myLearnProject;
        }

        return null;

    }


    @NotNull
    public String getScratchExtension() {
        return ".java";
    }

    @NotNull
    public String getScratchName() {
        return "Test.java";
    }

    @Override
    public String getLearnProjectName() {
        return "LearnProject";
    }
}
