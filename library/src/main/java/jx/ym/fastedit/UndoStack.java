package jx.ym.fastedit;

import java.util.LinkedList;
import java.util.List;

public class UndoStack {


    public interface Step {
        void undo();

        void redo();

        boolean merge(Step step);
    }

    List<Step> stepList;
    int maxSize;
    int pos;

    public UndoStack() {
        this(10000);
    }

    public UndoStack(int maxSize) {
        this.maxSize = maxSize;
        stepList = new LinkedList<>();
        pos = 0;
    }

    public void clear() {
        pos = 0;
        stepList.clear();
    }

    public void add(Step step) {
        if (canUndo()) {
            Step undo = stepList.get(pos - 1);
            if (undo.merge(step)) {
                return;
            }
        }
        //添加
        stepList.add(pos, step);
        pos++;
        //清空后面的
        int size = stepList.size();
        for (int i = pos; i > 0 && i < size; i++) {
            stepList.remove(pos);
        }
        //判断是否超出
        if (stepList.size() > maxSize) {
            stepList.remove(0);
            pos--;
        }


    }

    public void undo() {
        if (canUndo()) {
            stepList.get(pos - 1).undo();
            pos--;
        }
    }

    public boolean canUndo() {
        return pos - 1 >= 0;
    }


    public void redo() {
        if (canRedo()) {
            stepList.get(pos).redo();
            pos++;
        }
    }

    public boolean canRedo() {
        return pos < stepList.size();
    }


}
