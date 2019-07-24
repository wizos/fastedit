package jx.ym.fastedit.app;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;

import jx.ym.fastedit.FastEdit;
import jx.ym.fastedit.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openWrapInputMode();
        FastEdit fastEdit = new FastEdit(this);
        setContentView(fastEdit);

    }

    /**
     * 自动调节输入框高度
     */
    public void openWrapInputMode() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
}
