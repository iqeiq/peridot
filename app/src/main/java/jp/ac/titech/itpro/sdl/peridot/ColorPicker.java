package jp.ac.titech.itpro.sdl.peridot;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.util.Pair;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.annimon.stream.function.Function;
import com.jakewharton.rxbinding2.view.RxView;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.PublishSubject;

import static android.graphics.Color.HSVToColor;
import static android.graphics.Color.RGBToHSV;


public class ColorPicker {
    private Activity parent;
    private DrawView view;
    private Button colorButton;
    private PublishSubject<Integer> event = PublishSubject.create();
    private PublishSubject<Pair<Float, Boolean>> hueChange = PublishSubject.create();
    private PublishSubject<Pair<Float, Boolean>> satChange = PublishSubject.create();
    private PublishSubject<Pair<Float, Boolean>> valChange = PublishSubject.create();
    private PublishSubject<Boolean> bitmapChange = PublishSubject.create();

    private PopupWindow pw;

    private float[] hsv = new float[3]; // 選択されている色をHSV色空間で格納しておく [0, 0, 0] - [360, 1, 1]

    private Bitmap hueBitmap;
    private Bitmap satBitmap;
    private Bitmap valBitmap;
    private Bitmap mhueBitmap;
    private Bitmap msatBitmap;
    private Bitmap mvalBitmap;
    private Canvas mhc;
    private Canvas msc;
    private Canvas mvc;
    private final Paint marker = new Paint();

    private final int width = 720;
    private final int height = 92;
    private final int markerHeight = 24;


    ColorPicker(Activity parent, DrawView view, Button colorButton) {
        this.parent = parent;
        this.view = view;
        this.colorButton = colorButton;

        // マーカー用Bitmap
        mhueBitmap = Bitmap.createBitmap(width, markerHeight, Config.ARGB_8888);
        msatBitmap = Bitmap.createBitmap(width, markerHeight, Config.ARGB_8888);
        mvalBitmap = Bitmap.createBitmap(width, markerHeight, Config.ARGB_8888);
        mhc = new Canvas(mhueBitmap);
        msc = new Canvas(msatBitmap);
        mvc = new Canvas(mvalBitmap);

        marker.setColor(Color.GRAY);
        marker.setStrokeWidth(4.f);

        // 色変更イベント
        hueChange.subscribe(p -> {
            hsv[0] = p.first;
            updateMarker(mhc, hsv[0] * width / 360.f);
            if(p.second) {
                updateSaturation();
                updateValue();
                updateColor();
                bitmapChange.onNext(true);
            }
        });

        satChange.subscribe(p ->  {
            hsv[1] = p.first;
            updateMarker(msc, hsv[1] * width);
            if(p.second) {
                updateValue();
                updateColor();
                bitmapChange.onNext(true);
            }
        });

        valChange.subscribe(p -> {
            hsv[2] = p.first;
            updateMarker(mvc, hsv[2] * width);
            if(p.second) {
                updateSaturation();
                updateColor();
                bitmapChange.onNext(true);
            }
        });

        // 色相カラーマップを作成 (どの色を選んでもここは変わらないので最初だけしか呼ばない)
        updateHue();

        // 初期選択色をペンの色に合わせる
        setColor(view.getLocalPen().getColor());
    }

    private Bitmap _updateBitmap(Function<Integer, Integer> f) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        // bitmap.setPixel(x, y, color)を何回もやると遅いので、配列に入れてまとめてセットする
        int pixels[] = new int[width * height];
        for(int i = 0; i < width; ++i) {
            int color = f.apply(i);
            for(int j = 0; j < height; ++j) pixels[i + j * width] = color;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void updateHue() {
        hueBitmap = _updateBitmap(x -> HSVToColor(new float[] { x * 360.f / width, 1, 1 }));
    }

    private void updateSaturation() {
        satBitmap = _updateBitmap(x -> HSVToColor(new float[] { hsv[0], x * 1.0f / width, hsv[2] }));
    }

    private void updateValue() {
        valBitmap = _updateBitmap(i -> HSVToColor(new float[] { hsv[0], hsv[1], i * 1.0f / width }));
    }

    private void updateMarker(Canvas c, float x) {
        c.drawColor(Color.WHITE, PorterDuff.Mode.CLEAR);
        c.drawLine(x - 10, 0, x, markerHeight, marker);
        c.drawLine(x + 10, 0, x, markerHeight, marker);
    }

    public Observable<Integer> onPicked() {
        return event;
    }

    private void updateColor() {
        int color = Color.HSVToColor(hsv);
        // Gradientにするとこう描けて、動く
        // ここらへんはdrawableの構造に依存する？ layer-listとか使ってるとめんどくさそう
        GradientDrawable bgShape = (GradientDrawable)colorButton.getBackground();
        bgShape.setColor(color);
        // 更新通知
        event.onNext(color);
    }

    private ImageView _createAndAddImageView(LinearLayout parentLayout, Bitmap bitmap) {
        ImageView iv = new ImageView(parent);
        iv.setImageBitmap(bitmap);
        parentLayout.addView(iv);
        return iv;
    }

    private Observable<Float> _observeImageView(ImageView iv) {
        return RxView
            .touches(iv)
            .throttleLast(2, TimeUnit.MILLISECONDS)
            .map(ev -> ev.getX())
            .map(x -> x < 0 ? 0 : x > width ? width : x)    // はみ出していても座標として入ってくるので
            .observeOn(AndroidSchedulers.mainThread());     // 描画はメインスレッドで
    }

    public void setColor(int c) {
        float[] _hsv = new float[3];
        RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), _hsv);
        satChange.onNext(Pair.create(_hsv[1], false));
        valChange.onNext(Pair.create(_hsv[2], false));
        hueChange.onNext(Pair.create(_hsv[0], true));
    }


    public void show() {
        LinearLayout popLayout = (LinearLayout)parent.getLayoutInflater().inflate(R.layout.color_picker, null);

        // BitmapをもとにImageViewを作成して登録, 登録した順にLinearLayoutに追加される
        _createAndAddImageView(popLayout, mhueBitmap);
        ImageView hv = _createAndAddImageView(popLayout, hueBitmap);
        _createAndAddImageView(popLayout, msatBitmap);
        ImageView sv = _createAndAddImageView(popLayout, satBitmap);
        _createAndAddImageView(popLayout, mvalBitmap);
        ImageView vv = _createAndAddImageView(popLayout, valBitmap);

        // ビューの色選択を監視
        _observeImageView(hv).subscribe(h -> hueChange.onNext(Pair.create(h * 360.f / width, true)));
        _observeImageView(sv).subscribe(s -> satChange.onNext(Pair.create(s  * 1.0f / width, true)));
        _observeImageView(vv).subscribe(v -> valChange.onNext(Pair.create(v * 1.0f / width, true)));

        bitmapChange.filter(v -> pw.isShowing()).subscribe(v -> {
            sv.setImageBitmap(satBitmap);
            vv.setImageBitmap(valBitmap);
        });

        // ポップアップウィンドウを作成して表示
        pw = new PopupWindow(parent);
        pw.setContentView(popLayout);
        // ここに指定する数字よくわからん
        pw.showAtLocation(view, Gravity.END | Gravity.BOTTOM, 16, 256 + 16);
    }

    public void hide() {
        pw.dismiss();
    }

    public void toggle() {
        if(pw != null && pw.isShowing()) {
            hide();
        } else {
            show();
        }
    }

    public void destroy() {
        if(pw != null && pw.isShowing()) {
            pw.dismiss();
        }
    }
}
