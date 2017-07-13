package jp.ac.titech.itpro.sdl.peridot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import com.annimon.stream.IntStream;

import java.util.HashMap;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.PublishSubject;


// custom view
public class DrawView extends View {

    private static final String TAG = "DrawView";

    private Communicator comm;
    private HashMap<String, PublishSubject<Pair<Float, Float>>> linesMap = new HashMap<>();

    private Canvas canvas;
    private Bitmap bitmap;
    private Rect bitmapRect;
    private Pen localPen = new Pen(this,Color.RED, 16.0f);
    private PublishSubject<Pair<Float, Float>> localLines;
    private PublishSubject<Integer> spuitColor = PublishSubject.create();

    private final int target[] = {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_MOVE,
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL
    };

    public enum Mode {
        DRAW,
        SPUIT
    }
    private Mode mode = Mode.DRAW;

    public DrawView(Context context) {
        super(context, null);
    }

    public DrawView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public DrawView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCommunicator(Communicator comm) {
        this.comm = comm;
    }

    public void sendDrawMessage(int action, Pen pen, float x, float y) {
        final int color = pen.getMode() == Pen.Mode.Draw ? pen.getColor() : Color.WHITE;
        comm.sendDrawMessage(action, pen.getWidth(), color, x, y);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (bitmap != null) {
            bitmap.recycle();
        }
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmapRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas = new Canvas();
        canvas.setBitmap(bitmap);
        canvas.drawColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        // 画面を反映
        c.drawBitmap(bitmap, bitmapRect, bitmapRect, null);
    }

    protected void processDraw(int action, float x, float y) {
        // 線の開始
        if(action == MotionEvent.ACTION_DOWN) {
            // 点列を送るためのSubjectを作成
            localLines = PublishSubject.create();
            observe(localLines, localPen);
        }

        // 座標だけ送信
        localLines.onNext(Pair.create(x, y));
        Log.d(TAG, "touch " + x + ", " + y);

        // 線の終了
        if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            localLines.onComplete();
            localLines = null;
        }

        sendDrawMessage(action, localPen, x, y);
    }

    protected void processSpuit(int action, float x, float y) {
        // 線の開始
        if(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            int color = bitmap.getPixel(Math.round(x), Math.round(y));
            spuitColor.onNext(color);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // 関係のないイベントを除外
        if(!IntStream.of(target).anyMatch(e -> e == ev.getAction())) return true;

        final int action = ev.getAction();

        switch (mode) {
            case DRAW: processDraw(action, ev.getX(), ev.getY()); break;
            case SPUIT: processSpuit(action, ev.getX(), ev.getY()); break;
        }

        return true;
    }

    public void invokleTouchEvent(String uuid, int action, Pen pen, float x, float y) {
        // 線の開始
        if(action == MotionEvent.ACTION_DOWN) {
            // 点列を送るためのSubjectを作成
            PublishSubject<Pair<Float, Float>> subject = PublishSubject.create();
            linesMap.put(uuid, subject);
            observe(subject, pen);
        }

        // 座標だけ送信
        if(linesMap.containsKey(uuid)) {
            linesMap.get(uuid).onNext(Pair.create(x, y));
        }

        // 線の終了
        if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if(linesMap.containsKey(uuid)) {
                linesMap.get(uuid).onComplete();
                linesMap.remove(uuid);
            }
        }
    }

    private void observe(PublishSubject<Pair<Float, Float>> lines, Pen pen) {
        lines
            .buffer(3, 2)   // 3つ組を作って始点を２つずらす (補間に3点使うため)
            .filter(p -> p.size() == 3) // 3つ未満なら除外 (onComplete時に3つなくても流れてくる)
            .observeOn(AndroidSchedulers.mainThread()) // 描画はメインスレッドで
            .subscribe(p -> {
                float x1 = p.get(0).first;
                float y1 = p.get(0).second;
                float x2 = p.get(1).first;
                float y2 = p.get(1).second;
                float x3 = p.get(2).first;
                float y3 = p.get(2).second;
                // 補間曲線のだいたいの長さ (この個数だけピクセルを描画する)
                int d = 1 + (int)(Math.floor(dist(x1, y1, x2, y2) + dist(x2, y2, x3, y3)));
                // 媒介変数t (0->1) をもとに描画
                for(int t = 0; t <= d; ++t) {
                    // TODO: 速度でブラシ幅を変えたい　
                    pen.draw(interpolate(x1, y1, x2, y2, x3, y3, t * 1.0f / d));
                }
                // ASAP描画指示
                invalidate();
            });
    }

    // 3点をB-スプライン補間
    private Pair<Float, Float> interpolate(float x1, float y1, float x2, float y2, float x3, float y3, float t) {
        return Pair.create(
            (1 - t) * (1 - t) * x1 + 2 * t * (1 - t) * x2 + t * t * x3,
            (1 - t) * (1 - t) * y1 + 2 * t * (1 - t) * y2 + t * t * y3
        );
    }

    // 2点間距離
    private float dist(float x1, float y1, float x2, float y2) {
        float dx = Math.abs(x2 - x1);
        float dy = Math.abs(y2 - y1);
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    public void drawRect(float x, float y, float halfw, float halfh, Paint paint) {
        canvas.drawRect(x - halfw, y - halfh, x + halfw, y + halfh, paint);
    }

    public void drawCircle(float x, float y, float r, Paint paint) {
        canvas.drawCircle(x, y, r, paint);
    }

    public void clear() {
        canvas.drawColor(Color.WHITE);
        invalidate();
    }

    public Pen getLocalPen() {
        return localPen;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Observable<Integer> onSpuit() {
        return spuitColor;
    }
}
