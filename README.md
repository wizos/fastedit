# fastedit
安卓端高性能输入框，支持100万文字流畅显示，可用于开发语法高亮代码编辑器。
#### 使用方法
```java
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

```
