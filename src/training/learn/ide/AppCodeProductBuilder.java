package training.learn.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.tree.IElementType;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.learn.Module;

import java.io.IOException;

/**
 * Created by jetbrains on 14/12/2016.
 */

public class AppCodeProductBuilder implements IProductBuilder {

    @Override
    public void checkEnvironment(Project project, @Nullable Module module) throws Exception {

    }

    @Override
    public IElementType getEndOfLineComment() {
        return null;
    }

    @Nullable
    @Override
    public Project initLearnProject(Project projectToClose) {
        Project project = null;
        try {
            project = ProjectManager.getInstance().loadAndOpenProject("/Users/jetbrains/Projects/TableView");
            return project;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JDOMException e) {
            e.printStackTrace();
        } catch (InvalidDataException e) {
            e.printStackTrace();
        }

        return project;
    }

    @NotNull
    @Override
    public String getScratchExtension() {
        return ".swift";
    }

    @NotNull
    @Override
    public String getScratchName() {
        return "TableViewTests.m";
    }

    @Override
    public String getLearnProjectName() {
        return "TableView";
    }
}
