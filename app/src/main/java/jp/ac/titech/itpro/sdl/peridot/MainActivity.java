package jp.ac.titech.itpro.sdl.peridot;

import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
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
import io.reactivex.subjects.PublishSubject;

import static com.jakewharton.rxbinding2.view.RxView.clicks;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";

    @BindView(R.id.draw_view) DrawView view;
    @BindView(R.id.bar_width) SeekBar seekbar;
    @BindView(R.id.toggle_online) ToggleButton toggle;
    @BindView(R.id.button_color) Button colorButton;
    @BindView(R.id.button_eraser) ToggleButton eraser;
    @BindView(R.id.button_spuit) ToggleButton spuit;
    @BindView(R.id.button_clear) FloatingActionButton clearButton;
    @BindView(R.id.button_save) Button saveButton;

    @BindInt(R.integer.max_width) int maxBrushWidth;
    @BindString(R.string.server_host) String serverHost;
    @BindInt(R.integer.server_port) int serverPort;
    @BindString(R.string.app_name) String appName;

    private ColorPicker cp;
    private Communicator comm;
    private final static int REQCODE_PERMISSIONS = 1111;

    private SensorManager sensorMgr;
    private Sensor accelerometer;
    private PublishSubject<Float> sensor = PublishSubject.create();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        comm = new Communicator(serverHost, serverPort);
        view.setCommunicator(comm);

        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            toastMessage("sensor not found...");
        }

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

        sensor
            .filter(vx -> vx < -10)
            .debounce(30, TimeUnit.MILLISECONDS)
            .buffer(700, TimeUnit.MILLISECONDS)
            .filter(x -> x.size() > 1)
            .observeOn(AndroidSchedulers.mainThread())
            .map(x -> new AlertDialog.Builder(this)
                    .setTitle("clear")
                    .setMessage("画面を消去しますか？")
                    .setPositiveButton("OK", (d, which)-> {
                        view.clear();
                        comm.sendClearMessage();
                    })
                    .setNegativeButton("Cancel", null)
                    .show()
            )
            .scan((prev, now)-> {
                if(prev.isShowing()) prev.cancel();
                return now;
            })
            .subscribe(d -> Log.d(TAG, "dialog"));


        // eraser
        clicks(eraser).subscribe(p -> {
            view.getLocalPen().setMode(eraser.isChecked() ? Pen.Mode.Eraser : Pen.Mode.Draw);
            // スポイトモードから離脱
            spuit.setChecked(false);
            view.setMode(DrawView.Mode.DRAW);
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
            // スポイトモードから離脱
            spuit.setChecked(false);
            view.setMode(DrawView.Mode.DRAW);
        });

        // カラーピッカーの変更イベントを検知
        cp.onPicked().subscribe(c -> view.getLocalPen().setColor(c));

        // spuit
        clicks(spuit).subscribe(p -> {
            view.setMode(spuit.isChecked() ? DrawView.Mode.SPUIT : DrawView.Mode.DRAW);
            // 消しゴムモードから脱出するようにする
            eraser.setChecked(false);
            view.getLocalPen().setMode(Pen.Mode.Draw);
        });

        view.onSpuit().subscribe(c -> cp.setColor(c));

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
            } else {
                comm.disconnect();
            }
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
                String filepath = view.saveFile(appName, filename);
                toastMessage("saved: " + filename);

                String[] paths = {filepath};//保存された画像のパス
                String[] mimeTypes = {"image/png"};
                MediaScannerConnection.scanFile(getApplicationContext(), paths, mimeTypes, (path, uri)-> {
                    Log.d(TAG, "scanFile:" + path +  " / " + uri);
                });

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

    @Override
    protected void onResume() {
        super.onResume();
        sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        sensorMgr.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensor.onNext(event.values[0]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
