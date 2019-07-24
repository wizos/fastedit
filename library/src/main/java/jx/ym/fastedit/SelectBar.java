package jx.ym.fastedit;

import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class SelectBar extends PopupWindow implements View.OnClickListener {
    Context context;
    FastEdit fastEdit;
    View contentView;
    /**
     * 全选
     */
    private TextView mBtnSelectAll;
    /**
     * 复制
     */
    private TextView mBtnCopy;
    /**
     * 剪切
     */
    private TextView mBtnCut;
    /**
     * 粘贴
     */
    private TextView mBtnPaste;
    /**
     * 分享
     */
    private TextView mBtnShare;
    TextView mBtnCancel;
    Paint paint;

    public SelectBar(FastEdit fastEdit) {
        super(fastEdit.getContext());
        this.fastEdit = fastEdit;
        this.context = fastEdit.getContext();
        contentView = LayoutInflater.from(context).inflate(R.layout.fe_layout_select, null);
        setContentView(contentView);
        contentView.measure(fastEdit.getWidth(), fastEdit.getHeight());
        setWidth(contentView.getMeasuredWidth());
        setHeight(contentView.getMeasuredHeight());
        initView();
        paint = new Paint();
        setBackgroundDrawable(null);
        contentView.setBackgroundDrawable(new Drawable() {
            @Override
            public void draw(Canvas canvas) {
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


            @Override
            public int getOpacity() {
                return 0;
            }
        });

    }


    public void initView() {
        mBtnSelectAll = (TextView) contentView.findViewById(R.id.btn_select_all);
        mBtnSelectAll.setOnClickListener(this);
        mBtnCopy = (TextView) contentView.findViewById(R.id.btn_copy);
        mBtnCopy.setOnClickListener(this);
        mBtnCut = (TextView) contentView.findViewById(R.id.btn_cut);
        mBtnCut.setOnClickListener(this);
        mBtnPaste = (TextView) contentView.findViewById(R.id.btn_paste);
        mBtnPaste.setOnClickListener(this);
        mBtnShare = (TextView) contentView.findViewById(R.id.btn_share);
        mBtnShare.setOnClickListener(this);
        mBtnCancel = (TextView) contentView.findViewById(R.id.btn_cancel);
        mBtnCancel.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        dismiss();
        if (i == R.id.btn_select_all) {
            fastEdit.selectAll();
        } else if (i == R.id.btn_copy) {
            try {
                fastEdit.copy();
            } catch (Exception e) {
            }
        } else if (i == R.id.btn_cut) {
            fastEdit.cut();
        } else if (i == R.id.btn_paste) {
            fastEdit.paste();
        } else if (i == R.id.btn_share) {
            //分享
            try {
                shareMsg("分享", "分享文本", fastEdit.getSelectText(), null);
            } catch (Exception e) {
                Toast.makeText(context, "文字太长，分享失败！", Toast.LENGTH_SHORT).show();
            }
        } else {
            fastEdit.getSelectModel().cancel();
            fastEdit.postInvalidate();
        }
    }

    public void shareMsg(String activityTitle, String msgTitle, String msgText,
                         String imgPath) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        if (imgPath == null || imgPath.equals("")) {
            intent.setType("text/plain"); // 纯文本
        } else {
            File f = new File(imgPath);
            if (f != null && f.exists() && f.isFile()) {
                intent.setType("image/jpg");
                Uri u = Uri.fromFile(f);
                intent.putExtra(Intent.EXTRA_STREAM, u);
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, msgTitle);
        intent.putExtra(Intent.EXTRA_TEXT, msgText);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent, activityTitle));
    }
}
