package orz.kassy.camera72;

import java.util.ArrayList;

import orz.kassy.camera72.view.Camera72SQLiteOpenHelper;
import orz.kassy.camera72.view.Camera72Utils;
import orz.kassy.tmpl.lib.Utils;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public class LiveWallpaperService extends WallpaperService {

    private static final String TAG = "LiveWallService";
    private final Handler mHandler = new Handler();
    ArrayList<Bitmap> mBmps = new ArrayList<Bitmap>();
    private int mFgCnt, mCurrentCnt=0;
    private String mDirectory;
    private int mDisplayW,mDisplayH; 
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG,"onCreate");
        // データベースからパス取得
        Camera72SQLiteOpenHelper helper = new Camera72SQLiteOpenHelper(getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor;
        cursor = db.query(Camera72SQLiteOpenHelper.TABLE_NAME, null, null, null, null, null, null);
        cursor.moveToFirst();
        mDirectory = cursor.getString(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_DIRECTORY));
        mFgCnt = cursor.getInt(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_FG_COUNT));
        
        Display display = Utils.getDisplayWidth(getApplicationContext());
        mDisplayW = display.getWidth();
        mDisplayH = display.getHeight();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"onDestroy");
    }

    // onCreateEngineでEngineを返します。
    @Override
    public Engine onCreateEngine() {
        Log.i(TAG,"onCreateEngine");
        return new Camera72LiveEngine();
    }

    /**
     *  描画を担当するEngineです。
     * @author kashimoto
     *
     */
    public class Camera72LiveEngine extends Engine  implements SharedPreferences.OnSharedPreferenceChangeListener{
        private static final String TAG ="LiveEngine";
        private Bitmap image;   // イメージ

        // 描画スレッド
        private final Runnable mDrawThread = new Runnable() {
            public void run() {
                drawFrame(0);
            }
        };

        //コンストラクタ
        public Camera72LiveEngine() {
            Log.i(TAG,"Camera72LiveEngine");
            // リソースからイメージをロードしておきます。
            //image = BitmapFactory.decodeResource(getResources(), R.drawable.sakura);
            for(int i=0; i<mFgCnt; i++){
                BitmapFactory.Options opt = new BitmapFactory.Options();
                String path = Camera72Utils.getFgJpgFileFullPath(mDirectory, new Integer(i));
                Bitmap bmp = Utils.decodeSmallBitmap(path, opt, 200, 200);
                mBmps.add(bmp);
            }
        }

        // Engine生成時に呼び出される
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            Log.i(TAG,"onCreate");
            setTouchEventsEnabled(true);
        }

        // Engine破棄時に呼び出される
        @Override
        public void onDestroy() {
            super.onDestroy();
            Log.i(TAG,"onDestroy");
            mHandler.removeCallbacks(mDrawThread);
        }

        // 表示状態変更時に呼び出される
        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.i(TAG,"onVisibility");
            if (visible) {
                drawFrame(0);
            } else {
                mHandler.removeCallbacks(mDrawThread);
            }
        }

        // サーフェイス変更時に呼び出される
        @Override
        public void onSurfaceChanged(SurfaceHolder holder,
            int format, int width, int height) {
            Log.i(TAG,"onSurfaceChanged");
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame(0);
        }

        // サーフェイス生成時に呼び出される
        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            Log.i(TAG,"onSurfaceCreated");
            super.onSurfaceCreated(holder);
        }

        // サーフェイス破棄時に呼び出される
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG,"onSurfaceDestroyed");
            super.onSurfaceDestroyed(holder);
            mHandler.removeCallbacks(mDrawThread);
        }

        // オフセット変更時に呼び出される
        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
            float xStep, float yStep, int xPixels, int yPixels) {
            Log.i(TAG, "onOffsetsChanged" +
            		     " xOffset="+xOffset
                        +", yOffset="+yOffset
                        +", xStep  ="+xStep
                        +", yStep = "+yStep
                        +",xpixels= "+xPixels
                        +",ypixels= "+yPixels);
            int cnt = (int)(xOffset * mFgCnt); 
            if(cnt==mFgCnt)cnt=0;
            drawFrame(cnt);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            Log.i(TAG,"onTouchEvent");
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mCurrentCnt++;
                if(mCurrentCnt>mFgCnt){
                    mFgCnt=0;
                }
                Log.i("LW", "change image cnt="+mCurrentCnt);
            }
        }

        // フレームの描画
        private void drawFrame(int cnt) {
            Log.i(TAG,"drawFrame="+cnt);
            final SurfaceHolder holder = getSurfaceHolder();
            Log.i("LW", "draw frame");

            Canvas c = null;
            image = mBmps.get(cnt);

            try {
                // Canvasをロック！
                c = holder.lockCanvas();
                if (c != null) {
                    // 描画 画面一杯に。
                    float scaleW = (float)mDisplayW / (float)image.getWidth();  
                    float scaleH = (float)mDisplayH / (float)image.getHeight();  
                    float scale = Math.min(scaleW, scaleH);
                    c.drawColor(Color.BLACK);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(0, image.getWidth()/2, image.getHeight()/2); // まず回転(その場で)
                    matrix.postScale(scale, scale); // 拡大縮小左上原点で
                    float transX = (mDisplayW - scale*image.getWidth()) /2;
                    float transY = (mDisplayH - scale*image.getHeight()) /2;
                    matrix.postTranslate(transX, transY); // 拡大縮小左上原点で
                    c.drawBitmap(image, matrix, null);            
                }
            } finally {
                // Canvasをアンロック！
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(TAG,"onSharedPreferenceChanged");
        }
    }
}
