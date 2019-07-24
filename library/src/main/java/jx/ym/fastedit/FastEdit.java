
package jx.ym.fastedit;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 非常快的文本编辑器
 */
public class FastEdit extends RelativeLayout {

    public interface SelectionListener {
        void onSelectChanged(SelectModel selectModel);

    }

    public interface TextListener {
        void onInserted(int post, String text);

        void onDeleted(String text, int start, int end);
    }


    public int lineNumberBgColor = 0xff333333;
    public int lineNumberTextColor = 0xffffffff;
    public int textSize = 14;
    public int lineNumberPadding = 5;
    public int textColor = 0xff000000;
    public int editLineColor = 0x33000000;

    StringBuilder code;
    UndoStack undoStack;
    //光标位置
    int cursorPos;
    //光标编辑状态的列
    int cursorInputCol;
    Paint paint;
    TextPaint textPaint;
    //文字颜色数组
    final String tabText = "    ";
    CodeInputConnection codeInputConnection;
    Integer lineStarts[];
    Scroller scroller;
    float codeScrollX, codeScrollY;
    InputEvent inputEvent;
    GestureDetector gestureDetector;
    float maxWidth, maxHeight;
    SelectModel selectModel;
    SelectBar selectBar;
    EditBar editBar;
    SelectionListener selectionListener;
    TextListener textListener;

    public FastEdit(Context context) {
        super(context);
        init();
    }

    public FastEdit(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    private void init() {
        selectBar = new SelectBar(this);
        editBar = new EditBar(this);
        setBackgroundColor(Color.WHITE);
        code = new StringBuilder();
        undoStack = new UndoStack();
        scroller = new Scroller(getContext());
        inputEvent = new InputEvent();
        selectModel = new SelectModel();
        codeInputConnection = new CodeInputConnection(this, false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setLongClickable(true);

        paint = new Paint();
        textPaint = new TextPaint();
        paint.setAntiAlias(true);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.MONOSPACE);
        setTextSize(textSize);
        gestureDetector = new GestureDetector(getContext(), new GestureListener());

    }


    class GestureListener implements GestureDetector.OnGestureListener {
        float downScrollX, downScrollY;
        boolean fling;
        long lastDown;

        @Override
        public boolean onDown(MotionEvent e) {
            if (System.currentTimeMillis() - lastDown < 300) {
                onDoublePress();
                return false;
            }
            lastDown = System.currentTimeMillis();
            downScrollX = codeScrollX;
            downScrollY = codeScrollY;
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            fling = false;
            return true;
        }

        void onDoublePress() {
            lastDown = 0;
            select();
        }

        /**
         * 手指按下
         *
         * @param e
         */
        @Override
        public void onShowPress(MotionEvent e) {

        }

        /**
         * 单击输入框，需要显示输入法
         * 将光标位置显示在点击位置
         *
         * @param e
         * @return
         */
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            //将光标位置显示在点击位置
            if (!fling) {
                onClickPos(e);
            }
            return false;
        }


        /**
         * 滑动，需要根据手势滑动界面
         *
         * @param e1
         * @param e2
         * @param distanceX
         * @param distanceY
         * @return
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            setScroll(downScrollX - e2.getX() + e1.getX(), downScrollY - e2.getY() + e1.getY());
            return false;
        }

        @SuppressLint("RestrictedApi")
        @Override
        public void onLongPress(MotionEvent e) {
            int cursorPos = getCursorPos(e.getX(), e.getY());
            setCursorPos(cursorPos);
            showEditBar();
        }


        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            scroller.fling((int) codeScrollX, (int) codeScrollY, (int) -velocityX, (int) -velocityY,
                    0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            fling = true;
            return false;
        }
    }

    public void showEditBar() {
        if (selectModel.isSelected()) {
            return;
        }
        float cursorX = getPosX(cursorPos);
        float cursorY = getPosY(cursorPos);

        int[] location = new int[2];
        getLocationInWindow(location);
        int left = (int) (location[0] + cursorX - getCodeScrollX());
        int top = (int) (location[1] + cursorY - getCodeScrollY());

        int x = left - editBar.getWidth() / 2;
        int y = top - editBar.getHeight();

        if (y > location[1] + getHeight() - editBar.getHeight()) {
            y = location[1] + getHeight() - editBar.getHeight();
        }
        if (editBar.isShowing()) {
            editBar.update(x, y, editBar.getWidth(), editBar.getHeight());
        } else {
            editBar.showAtLocation(FastEdit.this,
                    Gravity.LEFT | Gravity.TOP, x, y);
        }
        editBar.updateUndoRedoState();
    }

    public void hideEditBar() {
        editBar.dismiss();
    }

    public void select() {
        select(cursorPos);
    }

    public void select(int cursorPos) {
        int length = code.length();
        //获取光标所在的字符串(文字字母或者数字可以组成字符串)
        char[] chars = Strings.getChars(code);
        if (chars == null) {
            return;
        }
        if (cursorPos >= length) {
            cursorPos = length - 1;
        }
        if (cursorPos < 0) {
            cursorPos = 0;
        }

        setCursorPos(cursorPos);
        int startPos, endPos;
        startPos = cursorPos;
        endPos = cursorPos + 1;
        char aChar = chars[cursorPos];
        if (Strings.isRightChar(aChar)) {
            //往前
            for (int i = startPos; i >= 0; i--) {
                if (Strings.isRightChar(chars[i])) {
                    startPos = i;
                } else {
                    break;
                }
            }
            //往后
            for (int i = endPos; i < length; i++) {
                if (Strings.isRightChar(chars[i])) {
                    endPos = i + 1;
                } else {
                    break;
                }
            }

        }
        selectModel.setSelected(true);
        selectModel.select(startPos, endPos);
        selectModel.showSelectBar();
    }

    protected void onClickPos(MotionEvent e) {
        int cursorPos = getCursorPos(e.getX(), e.getY());
        if (!selectModel.isSelected() && !editBar.isShowing() && cursorPos == FastEdit.this.cursorPos) {
            showEditBar();
            return;
        }
        setCursorPos(cursorPos);
        //显示输入法
        showInputBord();
        //更新光标列
        updateCursorCol();
        //取消选择
        selectModel.cancel();
        //重新绘制
        postInvalidate();
    }

    /**
     * 取消选择文字
     */
    public void cancelSelect() {
        selectModel.cancel();
        postInvalidate();
    }

    Timer cursorTimer;
    boolean showCursor;

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        Log.d("CodeView", "onWindowFocusChanged: " + hasWindowFocus);
        if (cursorTimer != null) {
            cursorTimer.cancel();
        }
        if (hasWindowFocus) {
            cursorTimer = new Timer();
            cursorTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    showCursor = !showCursor;
                    postInvalidate();

                }
            }, 500, 500);
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            setScroll((float) scroller.getCurrX(), scroller.getCurrY());
            postInvalidate();
        }
    }


    public SelectModel getSelectModel() {
        return selectModel;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        inputEvent.onKeyEvent(event);
        if (event.isCanceled()) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 键盘↑
     *
     * @param event
     */
    protected void onKeyCodeDpadDown(KeyEvent event) {
        int line = getLine(cursorPos) + 1;
        if (line >= lineStarts.length) {
            return;
        }
        setCursorPos(getColPos(line, cursorInputCol));
        if (selectModel.hasSelect()) {
            selectModel.cancel();
        }
        postInvalidate();
    }

    /**
     * 键盘↓
     * <p>
     * 1.如果是shift状态，则选择文字
     * 2.光标下移
     * <p>
     * 取消选择状态：用户输入，
     *
     * @param event
     */

    protected void onKeyCodeDpadUp(KeyEvent event) {

        int line = getLine(cursorPos) - 1;
        if (line < 0) {
            return;
        }
        setCursorPos(getColPos(line, cursorInputCol));
        if (selectModel.hasSelect()) {
            selectModel.cancel();
        }
        postInvalidate();

    }

    public int getColPos(int line, int col) {
        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        int newPos = lineStart + col;
        if (newPos > lineEnd) {
            newPos = lineEnd;
        }
        return newPos;
    }

    /**
     * 键盘上翻页
     *
     * @param event
     */
    protected void onKeyCodePageUp(KeyEvent event) {
        codeScrollY -= getHeight();

        isSide();
        postInvalidate();
    }

    /**
     * 键盘下翻页
     *
     * @param event
     */
    protected void onKeyCodePageDown(KeyEvent event) {
        codeScrollY += getHeight();
        isSide();
        postInvalidate();
    }

    protected void onKeyCodeMoveHome(KeyEvent event) {
        setCursorPos(getLineStart(getLine(cursorPos)));
        updateCursorCol();
    }

    protected void onKeyCodeMoveEnd(KeyEvent event) {
        setCursorPos(getLineEnd(getLine(cursorPos)));
        updateCursorCol();
    }

    /**
     * 键盘←
     *
     * @param event
     */
    protected void onKeyCodeDpadLeft(KeyEvent event) {
        if (selectModel.hasSelect()) {
            setCursorPos(selectModel.getStart());
            selectModel.cancel();
        } else {
            setCursorPos(cursorPos - 1);
        }
        postInvalidate();
        updateCursorCol();
    }

    /**
     * 键盘→
     *
     * @param event
     */
    protected void onKeyCodeDpadRight(KeyEvent event) {

        if (selectModel.hasSelect()) {
            setCursorPos(selectModel.getEnd());
            selectModel.cancel();
        } else {
            setCursorPos(cursorPos + 1);
        }
        postInvalidate();
        updateCursorCol();
    }

    /**
     * 键盘Enter
     *
     * @param event
     */
    protected void onKeyCodeEnter(KeyEvent event) {
        insert("\n");
    }

    /**
     * 键盘删除
     *
     * @param event
     */
    protected void onKeyCodeDel(KeyEvent event) {
        delete();
    }

    /**
     * 键盘删除
     *
     * @param event
     */
    protected void onKeyCodeForwardDel(KeyEvent event) {
        if (selectModel.hasSelect()) {
            selectModel.delete();
        } else {
            delete(cursorPos, cursorPos + 1);
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (selectModel.onTouchEvent(event)) {
            return true;
        }
        return gestureDetector.onTouchEvent(event);
    }


    /**
     * 设置文字大小
     *
     * @param dip
     */
    public void setTextSize(int dip) {
        textPaint.setTextSize(px(dip));
    }

    private float px(int dip) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dip, getResources().getDisplayMetrics());
    }

    RectF lineNumberRect;

    int getTextColor(int pos) {
        return textColor;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //绘制背景
        Drawable background = getBackground();
        if (background != null) {
            background.draw(canvas);
        }

        long t = System.currentTimeMillis();
        try {
            //行数量
            int lineCount = getLineCount();
            //绘制行号宽度
            float lineNumberWidth = getLineNumberWidth();
            //行高
            float lineHeight = getLineHeight();
            //可视文字开始行
            int textVisibleStartLine = (int) (getCodeScrollY() / lineHeight);
            //可视文字结尾行
            int textVisibleEndLine = (int) ((getHeight() + getCodeScrollY() + 0.5f) / lineHeight);
            if (textVisibleEndLine > lineCount - 1) {
                textVisibleEndLine = lineCount - 1;
            }

            //可视文字y偏移量
            float textVisibleStartOffY = getCodeScrollY() - textVisibleStartLine * lineHeight;

            if (code != null) {
                //绘制文字背景
                paint.setColor(Color.RED);
                if (selectModel.hasSelect()) {
                    final Path selectPath = selectModel.getSelectPath();
                    canvas.drawPath(selectPath, paint);
                }

                //绘制文字
                drawText(canvas, lineNumberWidth, lineHeight, textVisibleStartLine, textVisibleEndLine, textVisibleStartOffY);

            }
            {//绘制行号背景
                if (lineNumberRect == null) {
                    lineNumberRect = new RectF();
                }
//                    lineNumberRect.set(-getCodeScrollX(), 0, lineNumberWidth - getCodeScrollX(), getHeight());
                lineNumberRect.set(0, 0,
                        lineNumberWidth,
                        getHeight());
                paint.setColor(lineNumberBgColor);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(lineNumberRect, paint);
            }
            {//绘制行号
                textPaint.setColor(lineNumberTextColor);
                for (int i = textVisibleStartLine; i <= textVisibleEndLine; i++) {
                    float topY = (i - textVisibleStartLine) * lineHeight - textVisibleStartOffY;
                    Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
                    float textY = (topY - fontMetrics.ascent + fontMetrics.descent);
//                        canvas.drawText(String.valueOf(i + 1), getLineNumberMargin() - getCodeScrollX(), textY, textPaint);
                    canvas.drawText(String.valueOf(i + 1), getLineNumberMargin(), textY, textPaint);
                }
            }
            {//绘制选择
                selectModel.drawSelectWater(canvas);
            }

        } finally {

            Log.d("CodeView", "draw use time: " + (System.currentTimeMillis() - t) + " text length:" + length());
        }


    }


    /**
     * 绘制文字
     *
     * @param canvas
     * @param lineNumberWidth
     * @param lineHeight
     * @param textVisibleStartLine
     * @param textVisibleEndLine
     * @param textVisibleStartOffY
     */
    private void drawText(Canvas canvas, float lineNumberWidth, float lineHeight, int textVisibleStartLine, int textVisibleEndLine, float textVisibleStartOffY) {
        for (int i = textVisibleStartLine; i <= textVisibleEndLine; i++) {
            float topY = (i - textVisibleStartLine) * lineHeight - textVisibleStartOffY;
            int lineStart = getLineStart(i);
            int lineEnd = getLineEnd(i);
            if (lineEnd < lineStart) {
                lineEnd = lineStart;
            }
            float[] widths = new float[lineEnd - lineStart];
            if (lineStart != lineEnd && lineStart < code.length()) {
                textPaint.getTextWidths(code.subSequence(lineStart, lineEnd).toString(), widths);
                final float width = textPaint.measureText(code.subSequence(lineStart, lineEnd).toString()) + lineNumberWidth + getLineNumberMargin();
                if (width > maxWidth) {
                    maxWidth = width;
                }
            }

            //绘制光标
            if (cursorPos >= lineStart && cursorPos <= lineEnd && !selectModel.isSelected()) {
                //绘制选择行背景
                drawEditLineBackground(canvas, topY, topY + lineHeight);
                drawCursor(canvas, lineNumberWidth, lineHeight, topY, lineStart, widths);
            }

            //绘制文字
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textY = (topY - fontMetrics.ascent + fontMetrics.descent);

            float offsetX = lineNumberWidth + getLineNumberMargin();
            for (int j = lineStart; j < lineEnd; j++) {
                int textColor = getTextColor(j);
                textPaint.setColor(textColor);
                if (offsetX + textPaint.getTextSize() > getCodeScrollX()) {

                    canvas.drawText(code, j, j + 1, offsetX - getCodeScrollX(), textY, textPaint);
                }
                offsetX += widths[j - lineStart];
                if (offsetX > getWidth() + getCodeScrollX()) {
                    break;
                }
            }


        }
        if (isSide()) {
            postInvalidate();
        }
    }

    private void drawEditLineBackground(Canvas canvas, float topY, float v) {

        paint.setColor(editLineColor);
        canvas.drawRect(0, topY, getWidth(), v, paint);
    }

    private void drawCursor(Canvas canvas, float lineNumberWidth, float lineHeight, float topY, int lineStart, float[] widths) {
        if (showCursor && isFocused()) {
            //绘制光标
            float off = lineNumberWidth + getLineNumberMargin();
            for (int j = 0; j < (cursorPos - lineStart); j++) {
                off += widths[j];
            }

            paint.setColor(Color.BLACK);
            canvas.drawRect(off - getCodeScrollX() - 2, topY + 5, off - getCodeScrollX() + 2, topY + lineHeight - 5, paint);
        }
    }

    private int getTokenCharCount(int lineEnd, int i) {
        int textColor = getTextColor(i);
        int count = 1;
        for (int k = i + 1; k < lineEnd; k++) {
            if (textColor != getTextColor(k)) {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * 返回这个，让系统认为这个界面为编辑器界面
     *
     * @return
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    /**
     * 返回这个，让输入法输入
     *
     * @param outAttrs
     * @return
     */
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return codeInputConnection;
    }

    /**
     * 设置文字
     *
     * @param text
     */
    public void setText(String text) {
        delete(0, length());
        if (text != null) {
            append(text);
        }
    }

    /**
     * 初始化行信息，统计行数量
     */
    private void initLines() {
        char[] chars = Strings.getChars(code);
        List<Integer> integers = new ArrayList<>();
        integers.add(0);
        int length = code.length();
        for (int i = 0; i < length; i++) {
            if (chars[i] == '\n') {
                integers.add(i + 1);
            }
        }
        lineStarts = integers.toArray(new Integer[0]);

        maxWidth = 0;
        maxHeight = lineStarts.length * getLineHeight();


    }

    private void updateCursorCol() {
        //计算列
        cursorInputCol = cursorPos - getLineStartByPos(cursorPos);
    }

    /**
     * 获取行数量
     *
     * @return
     */
    public int getLineCount() {
        return lineStarts == null ? 1 : lineStarts.length;
    }

    /**
     * 根据光标位置获取行
     *
     * @param pos
     * @return
     */
    public int getRow(int pos) {
        int length = code.length();
        int count = 1;
        for (int i = 0; i < length; i++) {
            if (code.charAt(i) == '\n') {
                count++;
            }
            if (i == pos) {
                break;
            }
        }
        return count;
    }

    /**
     * 根据光标位置获取列
     *
     * @param pos
     * @return
     */

    public int getCol(int pos) {
        int length = code.length();
        int count = 0;
        String s = code.toString();
        for (int i = pos; i > 0 && i < length; i--) {
            if (s.charAt(i) == '\n') {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * 获取行头
     *
     * @param pos
     * @return
     */

    public int getLineStartByPos(int pos) {
        if (code == null) {
            return 0;
        }
        if (pos < 0) {
            return 0;
        }
        char[] chars = Strings.getChars(code);
        int length = code.length();

        for (int i = pos - 1; i >= 0 && i < length; i--) {
            if (chars[i] == '\n') {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 获取行尾
     *
     * @param pos
     * @return
     */

    public int getLineEndByPos(int pos) {
        if (code == null) {
            return 1;
        }
        if (pos < 0) {
            return 1;
        }
        int length = code.length();
        char[] chars = Strings.getChars(code);
        for (int i = pos + 1; i < length; i++) {
            if (chars[i] == '\n') {
                return i;
            }
        }
        return length;
    }

    /**
     * 获取光标位置
     *
     * @param x eventX
     * @param y eventY
     * @return
     */

    public int getCursorPos(float x, float y) {
        if (code == null) {
            return 0;
        }
        //整除得到行
        int line = (int) ((y + getCodeScrollY()) / getLineHeight());
        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        //切割行文字
        String lineText = substring(lineStart, lineEnd);
        if (lineText == null) {
            lineText = "";
        }
        float[] widths = new float[lineText.length()];
        //计算每个字宽度
        textPaint.getTextWidths(lineText, widths);

        //获取点击点的x相对位置
        float relativeX = x + getCodeScrollX() - getLineNumberMargin() - getLineNumberWidth();
        float offX = 0;
        //相加计算最接近点击相对x的位置
        for (int i = 0; i < widths.length; i++) {
            offX += widths[i];
            if (offX - widths[i] / 2 > relativeX) {
                return lineStart + i;
            }
        }
        return lineEnd;
    }


    public int getCursorPos() {
        return cursorPos;
    }


    public void setCursorPos(int pos) {
        cursorPos = pos;
        if (cursorPos < 0) {
            cursorPos = 0;
        }
        if (cursorPos > length()) {
            cursorPos = length();
        }
        onCursorChanged();
    }


    public void undo() {
        undoStack.undo();
    }


    public void redo() {
        undoStack.redo();
    }


    public boolean canUndo() {
        return undoStack.canUndo();
    }


    public boolean canRedo() {
        return undoStack.canRedo();
    }

    boolean lockUndoStack;


    public void lockUndoStack() {
        lockUndoStack = true;
    }


    public void unlockUndoStack() {
        lockUndoStack = false;
    }


    public void insert(char c) {
        insert(cursorPos, c);
    }


    public void insert(String text) {
        insert(cursorPos, text);
    }


    public void insert(int pos, String text) {
        if (pos < 0) {
            return;
        }
        if (selectModel.hasSelect()) {
            selectModel.delete();
            pos = cursorPos;
        }
        code.insert(pos, text);
        setCursorPos(pos + text.length());
        afterTextChanged();
        onInserted(pos, text);
    }

    public void clearUndoStack() {
        undoStack.clear();
    }

    /**
     * 插入文字后触发
     *
     * @param pos
     * @param text
     */
    protected void onInserted(int pos, String text) {
        if (!lockUndoStack) {
            undoStack.add(new InsertTextStep(this, pos, text));
        }
        if (textListener != null) {
            textListener.onInserted(pos, text);
        }
    }


    public void delete(int start, int end) {
        if (code == null) {
            return;
        }
        if (start < 0) {
            return;
        }
        if (end > code.length()) {
            end = code.length();
        }
        String delete = code.substring(start, end);
        code.delete(start, end);
        if (delete != null) {
            setCursorPos(start);
            afterTextChanged();
            onDeleted(start, end, delete);
        }
    }

    protected void onDeleted(int start, int end, String delete) {
        if (!lockUndoStack) {
            undoStack.add(new DeleteTextStep(this, start, delete));
        }
        if (textListener != null) {
            textListener.onDeleted(delete, start, end);
        }

    }

    public void insert(int pos, char c) {
        if (selectModel.hasSelect()) {
            selectModel.delete();
            pos = cursorPos;
        }
        code.insert(pos, String.valueOf(c));
        setCursorPos(pos + 1);
        afterTextChanged();
        onInserted(pos, String.valueOf(c));
    }


    public void append(String text) {
//        code.append(text);
//        setCursorPos(length());
//        afterTextChanged();
        insert(cursorPos, text);
    }

    private void afterTextChanged() {
        initLines();
        updateCursorCol();
        scrollToVisible();
        postInvalidate();
    }

    public void onCursorChanged() {
        showCursor = true;
        scrollToVisible();
        postInvalidate();
    }

    public void selectAll() {
        select(0, length());
        selectModel.showSelectBar();
    }


    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            scrollToVisible();
            postInvalidate();
        }
    }


    public String getText() {
        return code == null ? null : code.toString();
    }


    public int length() {
        return code == null ? 0 : code.length();
    }


    public String substring(int start, int end) {
        try {
            return code == null ? null : code.subSequence(start, end).toString();
        } catch (Exception e) {
            return null;
        }
    }


    public void select(int start, int end) {
        selectModel.select(start, end);
    }


    public void deleteLine() {
        int lineStart = getLineStartByPos(cursorPos);
        int lineEnd = getLineEndByPos(cursorPos);
        delete(lineStart, lineEnd);
    }


    public void clear() {
        if (code != null) {
            delete(0, code.length());
        }
    }


    public void gotoLine(int line) {
        int lineStart = getLineStart(line);
        setCursorPos(lineStart);
    }


    public void searchDown(String text, int startIndex) {

    }


    public void searchUp(String text, int startIndex) {

    }


    public void searchRegDown(String reg, int startIndex) {

    }


    public void searchRegUp(String reg, int startIndex) {

    }


    public void replaceSelected(String text) {

    }


    public void replaceAllText(String text, String replace) {

    }


    public void replaceAllReg(String reg, String replace) {

    }


    public void showInputBord() {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(this, 0);
    }


    public void hideInputBord() {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
    }


    public float getLineHeight() {
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        return (fontMetrics.bottom - fontMetrics.top) * 1.2f;
    }


    public void setLineHeight(float lineHeight) {

    }


    public float getLineNumberWidth() {
        int lineCount = getLineCount();
        return textPaint.measureText(String.valueOf(lineCount)) + 2 * getLineNumberMargin();
    }

    public float getLineNumberMargin() {
        return px(lineNumberPadding);
    }


    public float getCodeScrollX() {

        return codeScrollX;
    }


    public float getCodeScrollY() {

        return codeScrollY;
    }

    public void setScroll(float x, float y) {
        codeScrollX = x;
        codeScrollY = y;
        isSide();
        postInvalidate();
    }

    private boolean isSide() {
        boolean side = false;
        if (codeScrollX > maxWidth) {
            codeScrollX = maxWidth;
            side = true;
        }
        if (codeScrollY > maxHeight) {
            codeScrollY = maxHeight;
            side = true;
        }
        if (codeScrollY < 0) {
            codeScrollY = 0;
            side = true;
        }
        if (codeScrollX < 0) {
            codeScrollX = 0;
            side = true;
        }

        return side;
    }


    public int getLineStart(int line) {
        if (code == null || code.length() == 0 || lineStarts == null || lineStarts.length == 0 || line < 0) {
            return 0;
        }
        if (line < lineStarts.length) {
            return lineStarts[line];
        }
        return code.length() + 1;
    }


    public int getLineEnd(int line) {
        int lineStart = getLineStart(line + 1);
        if (lineStart > 0) {
            return lineStart - 1;
        }
        return 0;
    }


    public void delete() {
        if (selectModel.hasSelect()) {
            selectModel.delete();
        } else {
            delete(cursorPos - 1, cursorPos);
        }

    }


    public void scrollToVisible() {
        int line = getLine(cursorPos);
        float posX = getPosX(cursorPos);
        float posY = line * getLineHeight();
        float textSize = px(this.textSize);
        //计算是否在可视范围之内
        if (codeScrollX > posX - textSize - getLineNumberWidth()) {
            //在视野左边
            codeScrollX = posX - textSize - getLineNumberWidth();
            if (codeScrollX < 0) {
                codeScrollX = 0;
            }
        }
        if (codeScrollX < posX - getWidth() + textSize) {
            codeScrollX = posX - getWidth() + textSize;
        }

        if (codeScrollY < posY + getLineHeight() - getHeight()) {
            codeScrollY = posY + getLineHeight() - getHeight();
            if (codeScrollY < 0) {
                codeScrollY = 0;
            }
        }

        if (codeScrollY > posY) {
            codeScrollY = posY;
        }

    }

    /**
     * 获取光标的y，真实位置需要减去scrollY
     *
     * @param cursorPos
     * @return
     */
    private float getPosY(int cursorPos) {
        final int line = getLine(cursorPos);
        return line * getLineHeight();
    }

    /**
     * 获取光标的x，真实位置需要减去scrollX
     *
     * @param cursorPos
     * @return
     */
    private float getPosX(int cursorPos) {
        int lineStart = getLineStartByPos(cursorPos);
        int lineEnd = getLineEndByPos(cursorPos);
        String lineText = substring(lineStart, lineEnd);
        float x = getLineNumberMargin() + getLineNumberWidth();

        if (lineText != null) {
            float[] widths = new float[lineText.length()];
            textPaint.getTextWidths(lineText, widths);
            for (int i = 0; i < cursorPos - lineStart && i < widths.length; i++) {
                x += widths[i];
            }
        }
        return x;
    }

    public int getLine(int cursorPos) {
        int line = 0;
        if (lineStarts != null) {
            for (int i = 1; i < lineStarts.length; i++) {
                if (cursorPos >= lineStarts[i]) {
                    line = i;
                    continue;
                }
                break;
            }
        }
        return line;
    }

    class InputEvent {
        void onKeyEvent(KeyEvent event) {
            int action = event.getAction();
            int keyCode = event.getKeyCode();
            switch (action) {
                case KeyEvent.ACTION_DOWN: {

                    //输入模式
                    int unicodeChar = event.getUnicodeChar();
                    if (unicodeChar != 0) {

                        input((char) unicodeChar);
                        event.cancel();
                    }
                    if (!event.isCanceled() && !event.isCtrlPressed() && !event.isAltPressed()
                            && !event.isShiftPressed()) {
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_TAB:
                                //tab
                                input(tabText);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_SPACE:
                                //空格
                                input(' ');
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_ENTER:
                                //enter
                                onKeyCodeEnter(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_DEL:
                                //del
                                onKeyCodeDel(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_FORWARD_DEL:
                                //del
                                onKeyCodeForwardDel(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_DPAD_LEFT:
                                //left
                                onKeyCodeDpadLeft(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_DPAD_RIGHT:
                                //right
                                onKeyCodeDpadRight(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_DPAD_UP:
                                //up
                                onKeyCodeDpadUp(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_DPAD_DOWN:
                                //down
                                onKeyCodeDpadDown(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_VOLUME_DOWN:

                            case KeyEvent.KEYCODE_PAGE_DOWN:
                                //page down
                                onKeyCodePageDown(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_VOLUME_UP:

                            case KeyEvent.KEYCODE_PAGE_UP:
                                //page up
                                onKeyCodePageUp(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_MOVE_HOME:
                                onKeyCodeMoveHome(event);
                                event.cancel();
                                break;
                            case KeyEvent.KEYCODE_MOVE_END:
                                onKeyCodeMoveEnd(event);
                                event.cancel();
                                break;
                        }
                    }
                    if (!event.isCanceled()) {
                        //快捷键模式
                        if (onShortKey(event.isCtrlPressed(), event.isAltPressed(), event.isShiftPressed(), keyCode)) {
                            event.cancel();
                        }
                    }

                    break;
                }

            }
        }

        /**
         * 快捷方式
         *
         * @param ctrl
         * @param alt
         * @param shift
         * @param keyCode
         */
        protected boolean onShortKey(boolean ctrl, boolean alt, boolean shift, int keyCode) {
            if (ctrl) {
                if (keyCode == KeyEvent.KEYCODE_A) {
                    selectModel.select(0, length());
                    postInvalidate();
                    return true;


                }
                if (keyCode == KeyEvent.KEYCODE_C) {
                    if (selectModel.hasSelect()) {
                        try {
                            copy();
                        } catch (Exception e) {
                        }
                        return true;
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_X) {
                    if (selectModel.hasSelect()) {
                        cut();
                        return true;
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_Z) {
                    undo();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_Y) {
                    redo();
                    return true;
                }

            }
            if (shift) {
                try {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (!selectModel.hasSelect()) {
                            selectModel.start(cursorPos);
                        }
                        selectModel.toLeft(1);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (!selectModel.hasSelect()) {
                            selectModel.start(cursorPos);
                        }
                        selectModel.toRight(1);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (!selectModel.hasSelect()) {
                            selectModel.start(cursorPos);
                        }
                        selectModel.toUp(1);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (!selectModel.hasSelect()) {
                            selectModel.start(cursorPos);
                        }
                        selectModel.toDown(1);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) {
                        if (!selectModel.hasSelect()) {
                            selectModel.start(cursorPos);
                        }
                        selectModel.toHome();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_MOVE_END) {
                        if (!selectModel.hasSelect()) {
                            selectModel.start(cursorPos);
                        }
                        selectModel.toEnd();
                        return true;
                    }

                } finally {
                    postInvalidate();
                }
            }
            return false;
        }

        private void input(String s) {
            insert(s);
        }

        private void input(char c) {
            if (c == '\t') {
                input(tabText);
            } else {
                insert(c);
            }
        }
    }

    public void paste() {
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence text = cm.getText();
        insert(text.toString());
    }

    public String getSelectText() {
        int start = selectModel.getStart();
        int end = selectModel.getEnd();
        if (start == end || end > length()) {
            return null;
        }
        return code.substring(start, end);
    }

    public void copy() throws Exception {
        String selectText = getSelectText();
        if (selectText == null) {
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("copy", selectText));
        } catch (Exception e) {
            Toast.makeText(getContext(), "文字太长，复制失败！", Toast.LENGTH_SHORT).show();
            throw e;
        }
    }

    public void cut() {
        try {
            copy();
            delete();
        } catch (Exception e) {
        }

    }

    /**
     * 按下shift，进行文字选择
     */
    public class SelectModel {
        private int start;//可能大于end
        private int end;
        private boolean selected;
        private Drawable startWater;
        private Drawable endWater;

        public SelectModel() {
            startWater = getResources().getDrawable(R.drawable.ic_water_start);
            endWater = getResources().getDrawable(R.drawable.ic_water_end);
        }

        //更新选择水滴位置
        private void setEndWaterBounds(Drawable water, float posX, float posY) {
            int wx = (int) (posX - codeScrollX);
            int wy = (int) (posY + getLineHeight() - codeScrollY);
            water.setBounds(wx, wy, (int) (wx + px(24)), (int) (wy + px(24)));
        }

        //更新选择水滴位置
        private void setStartWaterBounds(Drawable water, float posX, float posY) {
            int wx = (int) (posX - px(24) - codeScrollX);
            int wy = (int) (posY + getLineHeight() - codeScrollY);
            water.setBounds(wx, wy, (int) (wx + px(24)), (int) (wy + px(24)));
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            if (this.selected != selected) {
                this.selected = selected;
                fireSelectionChanged();
            }
        }

        public void drawSelectWater(Canvas canvas) {
            if (isSelected()) {
                startWater.draw(canvas);
                endWater.draw(canvas);
            }
        }

        boolean adjustStartWater;
        boolean adjustEndWater;
        //按下的位置与左水滴光标距离
        float distanceStartWater;
        //按下的位置与右水滴光标距离
        float distanceEndWater;

        public boolean onTouchEvent(MotionEvent event) {
            if (!isSelected()) {
                return false;
            }
            hideSelectBar();
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    adjustEndWater = adjustStartWater = false;
                    if (endWater.getBounds().contains((int) x, (int) y)) {
                        //end调整
                        adjustEndWater = true;
                        distanceEndWater = endWater.getBounds().left - x;
                    } else if (startWater.getBounds().contains((int) x, (int) y)) {
                        //start调整
                        adjustStartWater = true;
                        distanceStartWater = startWater.getBounds().right - x;
                    } else {
                        return false;
                    }
                case MotionEvent.ACTION_MOVE:
                    //计算当前位置的光标位置，然后进行调整
                    if (adjustStartWater) {
                        if (y > endWater.getBounds().bottom - getLineHeight()) {
                            y = endWater.getBounds().bottom - getLineHeight();
                        }
                        float px = x + distanceStartWater;
                        float py = y - getLineHeight();

                        int cursorPos = getCursorPos(px, py);
                        if (cursorPos < end) {
                            start = cursorPos;
                        } else {
                            start = end - 1;
                        }

                        postInvalidate();
                    } else if (adjustEndWater) {
                        if (y < startWater.getBounds().bottom - getLineHeight()) {
                            y = startWater.getBounds().bottom - getLineHeight();
                        }
                        if (y >= (getLineCount() + 0.5f) * getLineHeight() - getCodeScrollY()) {
                            y = (getLineCount() + 0.5f) * getLineHeight() - getCodeScrollY();
                        }

                        float px = x + distanceEndWater;
                        float py = y - getLineHeight();


                        int cursorPos = getCursorPos(px, py);
                        if (cursorPos > start) {
                            end = cursorPos;
                        } else {
                            end = start + 1;
                        }
                        postInvalidate();

                    } else {
                        return false;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!adjustStartWater && !adjustEndWater) {
                        return false;
                    }
                    showSelectBar();
            }


            return true;
        }

        public void showSelectBar() {
            if (!isSelected()) {
                return;
            }
            getSelectPath();
            int[] location = new int[2];
            getLocationInWindow(location);
            int left = location[0] + startWater.getBounds().right;
            int top = location[1] + (int) (startWater.getBounds().top - getLineHeight());

            int x = left;
            int y = top - selectBar.getHeight();

            if (y > location[1] + getHeight() - selectBar.getHeight()) {
                y = location[1] + getHeight() - selectBar.getHeight();
            }
            if (selectBar.isShowing()) {
                selectBar.update(x, y, selectBar.getWidth(), selectBar.getHeight());
            } else {
                selectBar.showAtLocation(FastEdit.this,
                        Gravity.LEFT | Gravity.TOP, x, y);
            }
        }

        void hideSelectBar() {
            selectBar.dismiss();
        }

        void fireSelectionChanged() {
            //显示编辑器
            if (!isSelected()) {
                hideSelectBar();

            }

            if (selectionListener != null) {
                selectionListener.onSelectChanged(this);
            }


        }

        public void start(int pos) {
            end = start = pos;
            fireSelectionChanged();
        }

        public int getEnd() {
            return end > start ? end : start;
        }

        public int getStart() {
            return start > end ? end : start;
        }

        public void select(int start, int end) {
            this.start = start;
            this.end = end;
            setCursorPos(end);
            updateCursorCol();
            fireSelectionChanged();
        }


        public void cancel() {
            end = start;
            setSelected(false);
            updateCursorCol();
            fireSelectionChanged();
        }

        public void delete() {
            int sStart, sEnd;
            if (start < end) {
                sStart = start;
                sEnd = end;
            } else {
                sStart = end;
                sEnd = start;
            }
            FastEdit.this.delete(sStart, sEnd);
            cancel();
            fireSelectionChanged();

        }


        Path getSelectPath() {
            int sStart, sEnd;
            if (start < end) {
                sStart = start;
                sEnd = end;
            } else {
                sStart = end;
                sEnd = start;
            }
            Path path = new Path();
            //计算
            float startX = getPosX(sStart);
            float startY = getPosY(sStart);
            float endX = getPosX(sEnd);
            float endY = getPosY(sEnd);
            //设置选择水滴位置
            setStartWaterBounds(startWater, startX, startY);
            setEndWaterBounds(endWater, endX, endY);

            float codeScrollX = getCodeScrollX();
            if (startX - codeScrollX < 0) {
                startX = 0;
            } else if (startX - codeScrollX > getWidth()) {
                startX = getWidth();
            } else {
                startX = startX - codeScrollX;
            }

            if (endX - codeScrollX < 0) {
                endX = 0;
            } else if (endX - codeScrollX > getWidth()) {
                endX = getWidth();
            } else {
                endX = endX - codeScrollX;
            }

            float codeScrollY = getCodeScrollY();
            if (startY - codeScrollY < -getLineHeight()) {
                startY = -getLineHeight();
            } else if (startY - codeScrollY > getHeight()) {
                startY = getHeight();
            } else {
                startY = startY - codeScrollY;
            }
            if (endY - codeScrollY < -getLineHeight()) {
                endY = -getLineHeight();
            } else if (endY - codeScrollY > getHeight()) {
                endY = getHeight();
            } else {
                endY = endY - codeScrollY;
            }
            float left = getLineNumberWidth() + getLineNumberMargin() - codeScrollX;
            if (left < 0) {
                left = 0;
            }

            path.moveTo(startX, startY);
            path.lineTo(startX, startY + getLineHeight());
            path.lineTo(left, startY + getLineHeight());
            path.lineTo(left, endY + getLineHeight());
            path.lineTo(endX, endY + getLineHeight());
            path.lineTo(endX, endY);
            path.lineTo(getWidth(), endY);
            path.lineTo(getWidth(), startY);
            path.close();
            return path;
        }

        public boolean hasSelect() {
            if (start != end) {
                setSelected(true);
            }
            return isSelected();
        }

        public void toLeft(int x) {
            end -= x;
            setCursorPos(end);
            updateCursorCol();
            fireSelectionChanged();
        }

        public void toRight(int x) {
            end += x;
            setCursorPos(end);
            updateCursorCol();
            fireSelectionChanged();
        }


        public void toDown(int i) {
            int line = getLine(end);
            line += i;
            if (line < 0) {
                return;
            }
            end = getColPos(line, cursorInputCol);
            setCursorPos(end);
            fireSelectionChanged();
        }

        public void toUp(int i) {
            int line = getLine(end);
            line -= i;
            if (line < 0) {
                return;
            }
            end = getColPos(line, cursorInputCol);
            setCursorPos(end);
            fireSelectionChanged();
        }

        public void toHome() {
            end = getLineStart(getLine(cursorPos));
            setCursorPos(end);
            updateCursorCol();
            fireSelectionChanged();
        }

        public void toEnd() {
            end = getLineEnd(getLine(cursorPos));
            setCursorPos(end);
            updateCursorCol();
            fireSelectionChanged();
        }
    }

    public static class InsertTextStep implements UndoStack.Step {
        FastEdit fastEdit;
        int pos;
        String text;

        public InsertTextStep(FastEdit fastEdit, int pos, String text) {
            this.fastEdit = fastEdit;
            this.pos = pos;
            this.text = text;
        }

        @Override
        public void undo() {
            fastEdit.lockUndoStack();
            fastEdit.delete(pos, pos + text.length());
            fastEdit.unlockUndoStack();

        }

        @Override
        public void redo() {
            fastEdit.lockUndoStack();
            fastEdit.insert(pos, text);
            fastEdit.unlockUndoStack();
        }

        @Override
        public boolean merge(UndoStack.Step step) {
            if (step instanceof InsertTextStep) {
                if (((InsertTextStep) step).text.contains("\n")) {
                    return false;
                }
                if (((InsertTextStep) step).text.contains(" ")) {
                    return false;
                }
                if (((InsertTextStep) step).pos != pos + text.length()) {
                    return false;
                }
                text = text + ((InsertTextStep) step).text;
                return true;
            }
            return false;
        }

    }

    public static class DeleteTextStep implements UndoStack.Step {
        FastEdit fastEdit;
        int start;
        String text;

        public DeleteTextStep(FastEdit fastEdit, int start, String text) {
            this.fastEdit = fastEdit;
            this.start = start;
            this.text = text;

        }

        @Override
        public void undo() {
            fastEdit.lockUndoStack();
            fastEdit.insert(start, text);
            fastEdit.setCursorPos(start + text.length());
            fastEdit.unlockUndoStack();

        }

        @Override
        public void redo() {
            fastEdit.lockUndoStack();
            fastEdit.delete(start, start + text.length());
            fastEdit.setCursorPos(start);
            fastEdit.unlockUndoStack();
        }

        @Override
        public boolean merge(UndoStack.Step step) {
            if (step instanceof DeleteTextStep) {
                if (((DeleteTextStep) step).text.contains("\n")) {
                    return false;
                }
                if (((DeleteTextStep) step).text.contains(" ")) {
                    return false;
                }
                if (((DeleteTextStep) step).start + ((DeleteTextStep) step).text.length() == start) {
                    start = ((DeleteTextStep) step).start;
                    text = ((DeleteTextStep) step).text + text;
                    return true;
                }
            }
            return false;
        }

    }

    public static class CodeInputConnection extends BaseInputConnection {
        FastEdit fastEdit;

        public CodeInputConnection(FastEdit targetView, boolean fullEditor) {
            super(targetView, fullEditor);
            fastEdit = targetView;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            fastEdit.insert(text.toString());
            return true;
        }

        /**
         * 屏蔽输入法按键触发键盘，适应模拟器键盘输入文字
         *
         * @param event
         * @return
         */
        @Override
        public boolean sendKeyEvent(KeyEvent event) {

            return fastEdit.onKeyDown(event.getKeyCode(), event);
        }

        @Override
        public boolean setSelection(int start, int end) {
            return true;
        }

        @Override
        public Editable getEditable() {
            return super.getEditable();
        }
    }
}
