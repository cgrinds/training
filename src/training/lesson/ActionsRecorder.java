package training.lesson;

import com.intellij.find.editorHeaderActions.CloseOnESCAction;
import com.intellij.ide.actions.NextOccurenceAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.Nullable;
import training.check.Check;
import training.editor.EduEditor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Created by karashevich on 18/12/14.
 */
public class ActionsRecorder implements Disposable {


    private Project project;
    private Document document;
    private EduEditor eduEditor;
    private String target;
    private boolean triggerActivated;
    Queue<String> triggerQueue;

    DocumentListener myDocumentListener;
    AnActionListener myAnActionListener;

    private boolean disposed = false;
    private Runnable doWhenDone;
    @Nullable
    Check check = null;

    public ActionsRecorder(Project project, Document document, String target, EduEditor eduEditor) {
        this.project = project;
        this.document = document;
        this.target = target;
        this.triggerActivated = false;
        this.doWhenDone = null;
        this.eduEditor = eduEditor;

        Disposer.register(eduEditor, this);
    }

    @Override
    public void dispose() {
        removeListeners(document, ActionManager.getInstance());
        disposed = true;
    }

    public void startRecording(final Runnable doWhenDone){

        if (disposed) return;
        this.doWhenDone = doWhenDone;

//        documentListener = new DocumentListener() {
//            @Override
//            public void beforeDocumentChange(DocumentEvent event) {
//
//            }
//
//            @Override
//            public void documentChanged(DocumentEvent event) {
//
//                Notification notification = new Notification("IDEA Global Help", "document changed", "document changed", NotificationType.INFORMATION);
//                Notifications.Bus.notify(notification);
//
//                if (isTaskSolved(document, target)) {
//                    dispose();
//                    doWhenDone.run();
//                }
//            }
//        };


//        document.addDocumentListener(documentListener, this);
    }

    public void startRecording(final Runnable doWhenDone, final @Nullable String actionId, @Nullable Check check) {
        final String[] stringArray = {actionId};
        startRecording(doWhenDone, stringArray, check);

    }
    public void startRecording(final Runnable doWhenDone, final String[] actionIdArray, @Nullable Check check){
        if (check != null) this.check = check;
        if (disposed) return;
        this.doWhenDone = doWhenDone;

//        triggerMap = new HashMap<String, Boolean>(actionIdArray.length);
        triggerQueue = new LinkedList<String>();
        //set triggerMap
        for (String actionString : actionIdArray) {
            triggerQueue.add(actionString);
        }
        checkAction();

    }



    public boolean isTaskSolved(Document current, String target){
        if (disposed) return false;

        if (target == null){
            if (triggerQueue !=null) {
                return (triggerQueue.size() == 1 && (check == null ? true : check.check()));
            } else return (triggerActivated && (check == null ? true : check.check()));
        } else {

            List<String> expected = computeTrimmedLines(target);
            List<String> actual = computeTrimmedLines(current.getText());

            if (triggerQueue !=null) {
                return ((expected.equals(actual) && (triggerQueue.size() == 0)) && (check == null ? true : check.check()));
            } else return ((expected.equals(actual) && triggerActivated ) && (check == null ? true : check.check()));
        }

    }

    private List<String> computeTrimmedLines(String s) {
        ArrayList<String> ls = new ArrayList<String>();

        for (String it :StringUtil.splitByLines(s) ) {
            String[] splitted = it.split("[ ]+");
            if (splitted != null) {
                for(String element: splitted)
                if (!element.equals("")) {
                    ls.add(element);
                }
            }
        }
        return ls;
    }

    private void checkAction() {
        final ActionManager actionManager = ActionManager.getInstance();
        if(actionManager == null) return;

        myAnActionListener = new AnActionListener() {

            private boolean editorFlag;

            @Override
            public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                if (event.getProject() == null || FileEditorManager.getInstance(event.getProject()).getSelectedTextEditor() != eduEditor.getEditor() )
                    editorFlag = false;
                else
                    editorFlag = true;
            }

            @Override
            public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {

                //if action called not from project or current editor is different from EduEditor
                if (!editorFlag) return;


                final String actionId = extendActionId(action);

                if(actionId == null) return;
                //trigger queue can't be empty. Last action should lead to pass task in other case polled element should be returned back.
                if(triggerQueue.size() == 0) return;
                if (actionId.toUpperCase().equals(triggerQueue.peek().toUpperCase())) {
//                    System.out.println("Action trigger has been activated.");
                    if (triggerQueue.size() > 1) triggerQueue.poll();
                    if (triggerQueue.size() == 1) {
                        if (isTaskSolved(document, target)) {
                            actionManager.removeAnActionListener(this);
                            if (doWhenDone != null)
                                dispose();
                            doWhenDone.run();
                        }
                    }
                }
            }

            @Override
            public void beforeEditorTyping(char c, DataContext dataContext) {
            }
        };


        myDocumentListener = new DocumentListener() {


            @Override
            public void beforeDocumentChange(DocumentEvent event) {

            }

            @Override
            public void documentChanged(final DocumentEvent event) {
                if (PsiDocumentManager.getInstance(project).isUncommited(document)) {
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        @Override
                        public void run() {

                            if(!disposed) {
                                PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (triggerQueue.size() == 0) {
                                            if (isTaskSolved(document, target)) {
                                                removeListeners(document, actionManager);
                                                if (doWhenDone != null)
                                                    dispose();
                                                doWhenDone.run();
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            }
        };

        document.addDocumentListener(myDocumentListener);
        actionManager.addAnActionListener(myAnActionListener);
    }

    @Nullable
    private String extendActionId(AnAction action) {

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        String actionId = ActionManager.getInstance().getId(action);

        if (actionId != null) return actionId;

        if (action instanceof CloseOnESCAction)
            return IdeActions.ACTION_EDITOR_ESCAPE;
        else
            return null;
    }

    private void removeListeners(Document document, ActionManager actionManager){
        if (myAnActionListener != null) actionManager.removeAnActionListener(myAnActionListener);
        if (myDocumentListener != null) document.removeDocumentListener(myDocumentListener);
        myAnActionListener = null;
        myDocumentListener = null;
    }
}

