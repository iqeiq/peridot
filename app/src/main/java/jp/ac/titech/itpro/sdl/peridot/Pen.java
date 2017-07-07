package jp.ac.titech.itpro.sdl.peridot;


import android.graphics.Color;
import android.graphics.Paint;
import android.util.Pair;


public class Pen {
    private final DrawView view;
    private final Paint paint = new Paint();
    private int color = Color.GRAY;
    private float width = 6.0f;
    private Shape shape = Shape.Circle;

    public enum Shape {
        Square,
        Circle
    }

    // constructors
    public Pen(DrawView view) {
        this.view = view;
    }

    public Pen(DrawView view, int color, float width, Shape shape) {
        this(view);
        set(color, width, shape);
    }

    public void set(int color, float width, Shape shape) {
        setColor(color);
        setWidth(width);
        setShape(shape);
    }

    public void setColor(int color) {
        this.color = color;
        paint.setColor(color);
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
    }

    public void draw(float x, float y) {
        switch(shape) {
            case Circle:
                drawCircle(x, y);
                break;
            case Square:
                drawRect(x, y);
                break;
        }
    }

    public void draw(Pair<Float, Float> p) {
        draw(p.first, p.second);
    }

    private void drawRect(float x, float y) {
        float half = width / 2.0f;
        view.drawRect(x, y, half, half, paint);
    }

    private void drawCircle(float x, float y) {
        float half = width / 2.0f;
        view.drawCircle(x, y, half, paint);
    }
}
