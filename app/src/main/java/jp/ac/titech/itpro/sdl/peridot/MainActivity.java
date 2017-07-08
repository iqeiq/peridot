package jp.ac.titech.itpro.sdl.peridot;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import com.jakewharton.rxbinding2.widget.RxSeekBar;

import java.util.concurrent.TimeUnit;

import static com.jakewharton.rxbinding2.view.RxView.clicks;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private DrawView view;
    private ColorPicker cp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 描画ビュー
        view = (DrawView)this.findViewById(R.id.draw_view);
        int max_width = getResources().getInteger(R.integer.max_width);
        float param = 100.f / (float)Math.log((double)max_width);

        // ブラシ幅バー
        RxSeekBar
            .userChanges((SeekBar)this.findViewById(R.id.bar_width))
            .debounce(16, TimeUnit.MILLISECONDS)
            .subscribe(p -> {
                float width = (float)Math.exp(p / param);
                view.getLocalPen().setWidth(width > 1f ? width : 1f);
                Log.d(TAG, "seekbar:" + p + " / width=" + width);
            });

        // clearボタン
        clicks(this.findViewById(R.id.button_clear))
            .subscribe(p -> view.clear() );

        // colorボタン
        Button colorButton = (Button)this.findViewById(R.id.button_color);
        GradientDrawable bgShape = (GradientDrawable)colorButton.getBackground();
        bgShape.setColor(view.getLocalPen().getColor());
        cp = new ColorPicker(this, view, colorButton);
        clicks(colorButton).subscribe(p -> cp.toggle());

        // onlineボタン
        ToggleButton toggle = (ToggleButton)this.findViewById(R.id.toggle_online);
        clicks(toggle)
            .subscribe(p -> {
                Log.d(TAG, "online: " + toggle.isChecked());
            });

    }

    @Override
    protected void onDestroy() {
        cp.destroy();
        super.onDestroy();
    }

}
