package training.learn;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.learn.dialogs.LearnProjectWarningDialog;

import java.io.File;
import java.io.IOException;

/**
 * Created by karashevich on 24/09/15.
 */
public class NewLearnProjectUtil {


    public static boolean showDialogOpenLearnProject(Project project){
        //        final SdkProblemDialog dialog = new SdkProblemDialog(project, "at least JDK 1.6 or IDEA SDK with corresponding JDK");
        final LearnProjectWarningDialog dialog = new LearnProjectWarningDialog(project);
        dialog.show();
        return dialog.isOK();
    }


}
