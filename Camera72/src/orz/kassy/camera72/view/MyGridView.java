package orz.kassy.camera72.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.View;

/**
 * 独自View 点線グリッドを引く
 * @author kashimoto
 */
public class MyGridView extends View{

    public MyGridView(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 全体背景
        canvas.drawColor(0xff111111);
        drawGrid(canvas);
    }
    
    /**
     *  座標系がわかるような罫線を引く
     * @param canvas
     */
    private void drawGrid(Canvas canvas) {
        Paint paint = new Paint();
        Paint paint2 = new Paint();
        paint.setColor(Color.argb(255, 255, 255, 255));
        paint2.setColor(Color.argb(255, 255, 255, 255));
        paint.setStrokeWidth(1);
        paint2.setStrokeWidth(1);
        paint.setTextSize(10);
        paint2.setPathEffect(new DashPathEffect(new float[]{10.0f,10.0f}, 0.0f));
        for (int y = 0; y < 1300; y+=100) {
            canvas.drawLine(0, y, 1300, y, paint);
            canvas.drawText(y+"", 0, y, paint);
        }
        for (int y =50; y < 1300; y+=100) {
            canvas.drawLine(0, y, 1300, y, paint2);
        }
        for (int x = 0; x < 1300; x += 100) {
            canvas.drawLine(x, 0, x, 1300, paint);
            canvas.drawText(x+"", x, 10, paint);
        }
        for (int x = 50; x < 1300; x += 100) {
            canvas.drawLine(x, 0, x, 1300, paint2);
        }
    }
}
