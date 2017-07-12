package jp.ac.titech.itpro.sdl.peridot;

import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jakewharton.rxbinding2.widget.RxSeekBar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindInt;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;

import static com.jakewharton.rxbinding2.view.RxView.clicks;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @BindView(R.id.draw_view) DrawView view;
    @BindView(R.id.bar_width) SeekBar seekbar;
    @BindView(R.id.toggle_online) ToggleButton toggle;
    @BindView(R.id.button_color) Button colorButton;
    @BindView(R.id.button_eraser) ToggleButton eraser;
    @BindView(R.id.button_clear) FloatingActionButton clearButton;
    @BindView(R.id.button_save) Button saveButton;

    @BindInt(R.integer.max_width) int maxBrushWidth;
    @BindString(R.string.server_host) String serverHost;
    @BindInt(R.integer.server_port) int serverPort;
    @BindString(R.string.app_name) String appName;

    private ColorPicker cp;
    private Communicator comm;
    private final static int REQCODE_PERMISSIONS = 1111;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        comm = new Communicator(serverHost, serverPort);
        view.setCommunicator(comm);

        // 線形ではなく指数スケールにする
        float param = 100.f / (float)Math.log(maxBrushWidth);
        RxSeekBar
            .userChanges(seekbar)
            .debounce(16, TimeUnit.MILLISECONDS)
            .subscribe(p -> { // p: 0 - 100
                float width = (float)Math.exp(p / param);
                view.getLocalPen().setWidth(width > 1f ? width : 1f);
                Log.d(TAG, "seekbar:" + p + " / width=" + width);
            });

        // clearボタン
        clicks(clearButton).subscribe(p -> {
            view.clear();
            comm.sendClearMessage();
        });

        // eraser
        clicks(eraser).subscribe(p -> {
            view.getLocalPen().setMode(eraser.isChecked() ? Pen.Mode.Eraser : Pen.Mode.Draw);
        });

        // colorボタン
        // ペンの色に合わせる
        GradientDrawable bgShape = (GradientDrawable)colorButton.getBackground();
        bgShape.setColor(view.getLocalPen().getColor());
        // カラーピッカー作成
        cp = new ColorPicker(this, view, colorButton);
        clicks(colorButton).subscribe(p -> {
            cp.toggle();
            // 色選択をしたら消しゴムモードから脱出するようにする
            eraser.setChecked(false);
            view.getLocalPen().setMode(Pen.Mode.Draw);
        });

        // onlineボタン
        clicks(toggle).subscribe(p -> {
            if(toggle.isChecked()) {
                if(comm.getState() == Communicator.State.CONNECTED) {
                    comm.disconnect();
                }
                comm.connect()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(message -> {
                        if(message.type.equals("clear")) {
                            view.clear();
                        } else if(message.type.equals("draw")) {
                            final Pen pen = new Pen(view, message.color, message.width);
                            view.invokleTouchEvent(message.uuid, message.action, pen, message.x, message.y);
                        } else {
                            Log.d(TAG, "unknown type: " + message.type);
                        }
                    });
                // toastMessage("接続に失敗しました");
                // toggle.setChecked(false);

            } else /*if(comm.getState() == Communicator.State.CONNECTED)*/ {
                comm.disconnect();
            } /*else if(comm.getState() == Communicator.State.CONNECTING) {
                toastMessage("please wait... try again later...");
                toggle.setChecked(true);
            }*/
        });

        // save
        clicks(saveButton).subscribe(p -> {
            final String filename = genFileName(".png");
            try {
                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQCODE_PERMISSIONS);
                    return;
                }
                view.saveFile(appName, filename);
                toastMessage("saved: " + filename);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                toastMessage("SD Card not found...?");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }

    @Override
    protected void onDestroy() {
        cp.destroy();
        super.onDestroy();
    }

    private void toastMessage(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER | Gravity.TOP, 0, 64);
        toast.show();
    }

    private String genFileName(String ext){
        return new SimpleDateFormat("yyyyMMddkkmmssSS", Locale.JAPAN).format(new Date()) + ext;
    }

}
