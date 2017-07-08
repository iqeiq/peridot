package jp.ac.titech.itpro.sdl.peridot;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.SeekBar;

import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxbinding2.widget.RxSeekBar;

import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private DrawView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        view = (DrawView)this.findViewById(R.id.draw_view);
        int max_width = getResources().getInteger(R.integer.max_width);
        float param = 100.f / (float)Math.log((double)max_width);

        RxSeekBar
            .userChanges((SeekBar)this.findViewById(R.id.bar_width))
            .debounce(16, TimeUnit.MILLISECONDS)
            .subscribe(p -> {
                float width = (float)Math.exp(p / param);
                view.getLocalPen().setWidth(width > 1f ? width : 1f);
                Log.d(TAG, "seekbar:" + p + " / width=" + width);
            });

        RxView
            .clicks(this.findViewById(R.id.button_clear))
            .subscribe(p -> view.clear() );

        RxView
            .clicks(this.findViewById(R.id.button_color))
            .subscribe(p -> { Log.d(TAG, "color"); });

    }
}
