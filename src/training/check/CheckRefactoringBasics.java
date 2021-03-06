package training.check;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;

/**
 * Created by karashevich on 21/08/15.
 */
public class CheckRefactoringBasics implements Check{

    Project project;
    Editor editor;

    @Override
    public void set(Project project, Editor editor) {
        this.project = project;
        this.editor = editor;
    }

    @Override
    public void before() {
    }

    @Override
    public boolean check() {
        return InplaceRefactoring.getActiveInplaceRenamer(editor) == null;
    }

    @Override
    public boolean listenAllKeys() {
        return true;
    }

}
