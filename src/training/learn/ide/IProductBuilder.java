package training.learn.ide;

import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.learn.Module;

/**
 * Created by jetbrains on 14/12/2016.
 */
public interface IProductBuilder {
    static IProductBuilder create() {
        try {
            //TODO: make loading depending on IDE name
            IProductBuilder iProductBuilder = (IProductBuilder) Class.forName("training.learn.ide.AppCodeProductBuilder").newInstance();
            return iProductBuilder;
        } catch (Exception e){
            //TODO: correct handle
            e.printStackTrace();
        }
        return null;
    }

    //Interface
    public void checkEnvironment(Project project, @Nullable Module module) throws Exception;


    IElementType getEndOfLineComment();

    @Nullable
    Project initLearnProject(Project projectToClose);

    @NotNull
    public String getScratchExtension();

    @NotNull
    public String getScratchName() ;

    String getLearnProjectName();
}
