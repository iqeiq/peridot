package jp.ac.titech.itpro.sdl.peridot;


import android.graphics.Color;
import android.graphics.Paint;
import android.util.Pair;


public class Pen {
    private final DrawView view;
    private final Paint paint = new Paint();
    private final Paint eraser = new Paint();
    private int color = Color.GRAY;
    private float width = 6.0f;
    private Shape shape = Shape.Circle;
    private Mode mode = Mode.Draw;

    public enum Shape {
        Square,
        Circle
    }

    public enum Mode {
        Draw,
        Eraser,
    }

    // constructors
    public Pen(DrawView view) {
        this.view = view;
        eraser.setColor(Color.WHITE);
    }

    public Pen(DrawView view, int color, float width) {
        this(view);
        set(color, width, Shape.Circle);
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

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    public int getColor() {
        return color;
    }

    public float getWidth() {
        return width;
    }

    public Shape getShape() {
        return shape;
    }

    public void draw(float x, float y, float rate) {
        switch(shape) {
            case Circle:
                drawCircle(x, y, rate);
                break;
            case Square:
                drawRect(x, y, rate);
                break;
        }
    }

    public void draw(float x, float y) {
        draw(x, y, 1.0f);
    }

    public void draw(Pair<Float, Float> p, float rate) {
        draw(p.first, p.second, rate);
    }

    public void draw(Pair<Float, Float> p) {
        draw(p.first, p.second);
    }

    private void drawRect(float x, float y, float rate) {
        float half = rate * width / 2.0f;
        view.drawRect(x, y, half, half, mode == Mode.Draw ? paint : eraser);
    }

    private void drawCircle(float x, float y, float rate) {
        float half = rate * width / 2.0f;
        view.drawCircle(x, y, half, mode == Mode.Draw ? paint : eraser);
    }
}
