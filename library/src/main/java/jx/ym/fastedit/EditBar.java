package jx.ym.fastedit;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

public class EditBar extends PopupWindow implements View.OnClickListener {
    Context context;
    FastEdit fastEdit;
    View contentView;
    private TextView mBtnSelect;
    private TextView mBtnUndo;
    private TextView mBtnRedo;
    private TextView mBtnPaste;
    Paint paint;

    public EditBar(FastEdit fastEdit) {
        super(fastEdit.getContext());
        this.fastEdit = fastEdit;
        this.context = fastEdit.getContext();
        contentView = LayoutInflater.from(context).inflate(R.layout.fe_layout_edit, null);
        setContentView(contentView);
        contentView.measure(fastEdit.getWidth(), fastEdit.getHeight());
        setWidth(contentView.getMeasuredWidth());
        setHeight(contentView.getMeasuredHeight());
        initView();
        setOutsideTouchable(true);
        paint = new Paint();
        setBackgroundDrawable(null);
        contentView.setBackgroundDrawable(new Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                int shadowWidth = contentView.getPaddingTop();
                paint.setColor(0x33000000);
                paint.setMaskFilter(new BlurMaskFilter(shadowWidth, BlurMaskFilter.Blur.OUTER));
                canvas.drawRoundRect(shadowWidth,
                        shadowWidth,
                        getWidth() - shadowWidth,
                        getHeight() - shadowWidth,
                        shadowWidth, shadowWidth,
                        paint);
                paint.reset();
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(shadowWidth,
                        shadowWidth,
                        getWidth() - shadowWidth,
                        getHeight() - shadowWidth,
                        shadowWidth, shadowWidth,
                        paint);
            }

            @Override
            public void setAlpha(int alpha) {

            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {

            }


            @SuppressLint("WrongConstant")
            @Override
            public int getOpacity() {
                return 0;
            }
        });

    }


    public void initView() {
        mBtnSelect = (TextView) contentView.findViewById(R.id.btn_select);
        mBtnSelect.setOnClickListener(this);
        mBtnUndo = (TextView) contentView.findViewById(R.id.btn_undo);
        mBtnUndo.setOnClickListener(this);
        mBtnRedo = (TextView) contentView.findViewById(R.id.btn_redo);
        mBtnRedo.setOnClickListener(this);
        mBtnPaste = (TextView) contentView.findViewById(R.id.btn_paste);
        mBtnPaste.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        dismiss();
        if (i == R.id.btn_select) {
            fastEdit.select();
        } else if (i == R.id.btn_undo) {
            fastEdit.undo();
            fastEdit.showEditBar();
        } else if (i == R.id.btn_redo) {
            fastEdit.redo();
            fastEdit.showEditBar();
        } else if (i == R.id.btn_paste) {
            fastEdit.paste();
            fastEdit.showEditBar();
        }
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
    }

    public void updateUndoRedoState() {
        if (fastEdit.canUndo()) {
            mBtnUndo.setEnabled(true);
            mBtnUndo.setTextColor(Color.BLACK);
        } else {
            mBtnUndo.setEnabled(false);
            mBtnUndo.setTextColor(Color.GRAY);
        }


        if (fastEdit.canRedo()) {
            mBtnRedo.setEnabled(true);
            mBtnRedo.setTextColor(Color.BLACK);
        } else {
            mBtnRedo.setEnabled(false);
            mBtnRedo.setTextColor(Color.GRAY);
        }
    }
}
