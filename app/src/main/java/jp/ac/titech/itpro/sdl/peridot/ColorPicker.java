package jp.ac.titech.itpro.sdl.peridot;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
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

import static android.graphics.Color.HSVToColor;
import static android.graphics.Color.RGBToHSV;


public class ColorPicker {
    private Activity parent;
    private DrawView view;
    private Button colorButton;

    private PopupWindow pw;

    private float[] hsv = new float[3];

    private Bitmap hueBitmap;
    private Bitmap satBitmap;
    private Bitmap valBitmap;

    private final Paint marker = new Paint();

    private final int width = 720;
    private final int height = 92;
    private final int markerHeight = 24;


    ColorPicker(Activity parent, DrawView view, Button colorButton) {
        this.parent = parent;
        this.view = view;
        this.colorButton = colorButton;

        marker.setColor(Color.GRAY);
        marker.setStrokeWidth(4.f);

        int c = view.getLocalPen().getColor();
        RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv);

        updateHue();
        updateSaturation();
        updateValue();
    }

    private Bitmap _update(Function<Integer, Integer> f) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        int pixels[] = new int[width * height];
        for(int i = 0; i < width; ++i) {
            int color = f.apply(i);
            for(int j = 0; j < height; ++j) pixels[i + j * width] = color;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void updateHue() {
        hueBitmap = _update(x -> HSVToColor(new float[] { x * 360.f / width, 1, 1 }));
    }

    private void updateSaturation() {
        satBitmap = _update(x -> HSVToColor(new float[] { hsv[0], x * 1.0f / width, hsv[2] }));
    }

    private void updateValue() {
        valBitmap = _update(i -> HSVToColor(new float[] { hsv[0], hsv[1], i * 1.0f / width }));
    }

    private void updateMarker(Canvas c, float x) {
        c.drawColor(Color.WHITE, PorterDuff.Mode.CLEAR);
        c.drawLine(x - 10, 0, x, markerHeight, marker);
        c.drawLine(x + 10, 0, x, markerHeight, marker);
    }

    private void updateColor() {
        int color = Color.HSVToColor(hsv);
        view.getLocalPen().setColor(color);
        GradientDrawable bgShape = (GradientDrawable)colorButton.getBackground();
        bgShape.setColor(color);
    }

    private ImageView _wrapImageView(LinearLayout parentLayout, Bitmap bitmap) {
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
            .map(x -> x < 0 ? 0 : x > width ? width : x)
            .observeOn(AndroidSchedulers.mainThread());
    }

    public void show() {
        LinearLayout popLayout = (LinearLayout)parent.getLayoutInflater().inflate(R.layout.color_picker, null);

        Bitmap mhueBitmap = Bitmap.createBitmap(width, markerHeight, Config.ARGB_8888);
        Bitmap msatBitmap = Bitmap.createBitmap(width, markerHeight, Config.ARGB_8888);
        Bitmap mvalBitmap = Bitmap.createBitmap(width, markerHeight, Config.ARGB_8888);

        Canvas mhc = new Canvas(mhueBitmap);
        mhc.drawColor(Color.WHITE);
        updateMarker(mhc, hsv[0] * width / 360.f);
        Canvas msc = new Canvas(msatBitmap);
        msc.drawColor(Color.WHITE);
        updateMarker(msc, hsv[1] * width);
        Canvas mvc = new Canvas(mvalBitmap);
        mvc.drawColor(Color.WHITE);
        updateMarker(mvc, hsv[2] * width);

        _wrapImageView(popLayout, mhueBitmap);
        ImageView hv = _wrapImageView(popLayout, hueBitmap);
        _wrapImageView(popLayout, msatBitmap);
        ImageView sv = _wrapImageView(popLayout, satBitmap);
        _wrapImageView(popLayout, mvalBitmap);
        ImageView vv = _wrapImageView(popLayout, valBitmap);

        _observeImageView(hv).subscribe(h -> {
                hsv[0] = h * 360.f / width;
                updateMarker(mhc, h);
                updateSaturation();
                sv.setImageBitmap(satBitmap);
                updateValue();
                vv.setImageBitmap(valBitmap);
                updateColor();
            });

        _observeImageView(sv).subscribe(s -> {
                hsv[1] = s  * 1.0f / width;
                updateMarker(msc, s);
                updateValue();
                vv.setImageBitmap(valBitmap);
                updateColor();
            });

        _observeImageView(vv).subscribe(v -> {
                hsv[2] = v * 1.0f / width;
                updateMarker(mvc, v);
                updateSaturation();
                sv.setImageBitmap(satBitmap);
                updateColor();
            });

        pw = new PopupWindow(parent);
        pw.setContentView(popLayout);
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
