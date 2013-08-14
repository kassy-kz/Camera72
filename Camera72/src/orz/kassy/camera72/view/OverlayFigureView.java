package orz.kassy.camera72.view;

import java.io.IOException;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import orz.kassy.camera72.R;
import orz.kassy.camera72.view.Utils;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.ScaleGestureDetector.OnScaleGestureListener;

/**
 * 重畳描画を行うビュー ここにフィギュアを描画する
 * @author kashimoto
 */
public class OverlayFigureView extends View {
    private static final String TAG = "OverlayView";

    // フィギュアのビットマップ
    private Bitmap mFigureBitmap;
    BitmapFactory.Options mOpt = new BitmapFactory.Options();

    // フィギュアのサイズ
    private float mFigureScale = 1.0f;

    // フィギュア貼り付け位置を設定するためのパラメータ
    private float mCenterX = 0;
    private float mCenterY = 0;

    // フィギュアの向きを制御するパラメータ
    private Integer mCurTarget = 0;
    private float mFigureRotate = 90.f;

    // フィギュアの画像を調整するためのパラメータ
    private double mCvBrightScalar = 1.d;
    private Size mCvGaussianSize;
    
    // フィギュアの枚数
    private int mFgCount;

    // ジェスチャ
    private GestureDetector mGesture = null;
    private ScaleGestureDetector scaleGesture = null;
    private MyGestureListener mGListener = null;
    private MyScaleGestureListener mScaleListener;

    // なんか全然仕事してくれないパラメータ
    private static final int STAMP_HEIGHT = 60;
    private static final int STAMP_WIDTH = 40;

    public static final int OVERLAY_NORMAL = 0;
    public static final int OVERLAY_REVERSE = 1;

    private Context mContext;
    private String mDirectory;
    
    /**
     * コンストラクタ
     * @param context
     */
    public OverlayFigureView(Context context, String seqDir, int fgCount) {
        super(context);
        
        // ビットマップ展開オプションを設定
        mOpt.inJustDecodeBounds = false; 
        mOpt.inPurgeable = true;
        mOpt.inInputShareable = true;
        mOpt.outHeight = STAMP_HEIGHT;
        mOpt.outWidth = STAMP_WIDTH;
        mOpt.inSampleSize = 1;

        // OpenCV設定用
        mCvGaussianSize = new Size(1,1);
        setFocusable(true);
        
        // gestureを作成（設定はview内）
        mGListener = new MyGestureListener(this);
        mGesture = new GestureDetector(mGListener);
        mScaleListener = new MyScaleGestureListener();
        scaleGesture = new ScaleGestureDetector(context, mScaleListener);
        
        //　フォルダ設定
        mDirectory = seqDir;
        
        // フィギュア枚数
        mFgCount = fgCount;
        
        mContext = context;
        
        // ビットマップ作成
        createFigureBitmap();
    }
    
    /**
     * フィギュアの大きさを設定する 
     * @param sampleSize 大きいほうが画像が小さくなる
     */
    public void setFigureSize(float size){
     //   mOpt.inSampleSize = sampleSize;
        mFigureScale = size;
    }
    
    /**
     * ぼかしに使うCV.sizeを設定する
     * @param size
     */
    public void setCvGaussianSize(Size gaussSize) {
        mCvGaussianSize = gaussSize;
        createFigureBitmap();
    }

    /**
     * 明るさ調整に使うCvScalarを設定する
     * @param brightScalar
     */
    public void setCvBrightScalar(double brightScalar) {
        mCvBrightScalar = brightScalar;
        createFigureBitmap();
    }
    
    /**
     * 背景とフィギュアの合成を保存する
     * TODO メモリー足りない問題
     * @param backgroundBmp 背景ビットマップ　このビューのサイズに合わせたものであること
     * @param overlayReverse 合成時にフィギュアを反転するか否かのフラグ
     */
    public void saveMergeBitmap(Bitmap backgroundBmp, int overlayReverse) {
        Log.i(TAG,"saveMergeBitamp");

        // 現在の現実的な案 bitmap重畳保存
        Bitmap bmp2 = getFigureOverlayedBitmap(backgroundBmp, overlayReverse);

        // 合成画像を保存する
        // 旧処理　これだとギャラリーに保存するだけ
        // MediaStore.Images.Media.insertImage(mContext.getContentResolver(), bmp2, "72shot.jpg", "Created by camera72");
        // 自前のフォルダに保存する
        String dir = Camera72Utils.APP_DIR_ON_SD +"/" + Camera72Utils.OUTPUT_FILE_DIR;
        String filename = Camera72Utils.getCurrentTimeString();
        try {
            Utils.saveBitmapAsJpgAtSdAndMediaScan(mContext, bmp2, dir, filename);
            //Utils.saveBitmapAsJpgAtSdAnd(bmp2, dir , filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    /**
     * 描画処理
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawFigure(canvas, OVERLAY_NORMAL);
    }

    /**
     * Canvasにフィギュアを描画する　
     * @param canvas
     * @param overlayReverse 
     */
    public void drawFigure(Canvas canvas, int overlayReverse) {
        // フィギュアの描画場所をMatrixで調整
        Matrix matrix = new Matrix();
        Log.i(TAG,"onDraw mScale = "+mFigureScale);
        matrix.postRotate(mFigureRotate , mFigureBitmap.getWidth()/2, mFigureBitmap.getHeight()/2);
        if(overlayReverse == OVERLAY_NORMAL){
            matrix.postScale(mFigureScale,mFigureScale);
            matrix.postTranslate(
                    mCenterX - (int)(mFigureBitmap.getWidth()/2 * mFigureScale),
                    mCenterY - (int)(mFigureBitmap.getHeight()/2 * mFigureScale));
        // 反転しているとき
        }else if(overlayReverse == OVERLAY_REVERSE){
            matrix.postScale(-mFigureScale,mFigureScale);
            matrix.postTranslate(
                    canvas.getWidth() - mCenterX + (int)(mFigureBitmap.getWidth()/2 * mFigureScale),
                    mCenterY - (int)(mFigureBitmap.getHeight()/2 * mFigureScale));
        }
        
        // 描画
        Log.i(TAG, "draw mX = " +mCenterX+"width = "+mFigureBitmap.getWidth());
        canvas.drawBitmap(mFigureBitmap, matrix, null);
    }

    
    private void createFigureBitmap() {
        if(mFigureBitmap != null) {
            mFigureBitmap.recycle();
        }
        // ビットマップ用意
        String targetPngFileUri = Camera72Utils.getTargetPngFileFullPath(mDirectory, mCurTarget);          
        mFigureBitmap = BitmapFactory.decodeFile(targetPngFileUri, mOpt);
        // 抜き出しまだな場合
        if(mFigureBitmap == null){
            Utils.showToast(mContext, R.string.not_extract_message);
            String targetJpgFileUri = Camera72Utils.getFgJpgFileFullPath(mDirectory, mCurTarget);          
            mFigureBitmap = BitmapFactory.decodeFile(targetJpgFileUri, mOpt);
            
        }
        Log.i(TAG,"targetPngFile = "+targetPngFileUri);
        
        // OpenCV用オブジェクトを用意
        Mat mat = new Mat(mFigureBitmap.getHeight(), mFigureBitmap.getWidth(), CvType.CV_8UC4);
        org.opencv.android.Utils.bitmapToMat(mFigureBitmap, mat);

        // OpenCVで明るさ調整する
        Core.convertScaleAbs(mat, mat, mCvBrightScalar, 0);

        // OpenCVでぼかし調整する
        Imgproc.GaussianBlur(mat, mat, mCvGaussianSize, 0);
        
        // Bitmapに戻す
        org.opencv.android.Utils.matToBitmap(mat, mFigureBitmap);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGesture.onTouchEvent(event);
        mGListener.setPointerCount(event.getPointerCount());
        return mGesture.onTouchEvent(event);
    }

    
    class MyGestureListener implements OnGestureListener {
        private static final String TAG = "MyGesture";
        private View mView;
        private int mPointerCount;

        /**
         * コンストラクタ
         * @param view　設定スべし
         */
        MyGestureListener(View view) {
            mView = view;
        }

        public void setPointerCount(int count) {
            Log.i(TAG,"set pointer count ="+count);
            mPointerCount = count;
        }
        
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            Log.i(TAG,"onScroll (disX,disY) = "+ distanceX + ", "+distanceY);
            //Log.i(TAG,"onScroll count = " + mPointerCount);

            // １本指スクロール
            if(mPointerCount == 1){
                mCenterX -= distanceX;
                mCenterY -= distanceY;
                Log.i(TAG,"Single scroll mX, mY = " + mCenterX +", "+mCenterY);

            // ２本指スクロール フィギュアを回転させる
            }else if(mPointerCount == 2){
                float d ;
                if(Math.abs(distanceX)> Math.abs(distanceY)){
                    d=distanceX;
                }else{
                    d=distanceY;
                }
                
                mCurTarget += (int)(d/3);
                if(mCurTarget >= mFgCount){
                    mCurTarget = mFgCount -1;
                } else if(mCurTarget < 0){
                    mCurTarget = 0;
                }
                createFigureBitmap();
                Log.i(TAG,"  twin scroll rotate d="+d+", cur="+mCurTarget);
            }
            invalidate();
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            Log.i(TAG,"onShowPress");
        }

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            Log.i(TAG,"onSingleTapUp");
            mCenterX = event.getX();
            mCenterY = event.getY();
            Log.i(TAG,"mX, mY = " + mCenterX +", "+mCenterY);
            mView.invalidate();
            return true;
        }
    }
    
    
    class MyScaleGestureListener implements OnScaleGestureListener{
        private float mSampleSize = 1;
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            Log.i(TAG,"onScale " + detector.getScaleFactor());
            mSampleSize *= detector.getScaleFactor();
            if(mSampleSize<0.2f){
                mSampleSize = 0.2f;
            }
            setFigureSize(mSampleSize);
            Log.i(TAG,"samplesize = " + mSampleSize);
            invalidate();
            return true;
        }
    };
    
    /**
     * 合成を行う　現在は画面で表示している内容をそのまま
     * @param bgBmp 背景のbitmap
     * @param overlayReverse 
     * @return
     */
    private Bitmap getFigureOverlayedBitmap(Bitmap bgBmp, int overlayReverse){
        // viewのサイズを取得
        int width = getWidth();
        int height = getHeight();
        Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);

        // 背景を描画
        float scaleW = (float)getWidth() / (float)bgBmp.getWidth();  
        float scaleH = (float)getHeight() / (float)bgBmp.getHeight();  
        float scale = Math.min(scaleW, scaleH);
        Matrix matrixBG = new Matrix();
        matrixBG.postScale(scale, scale); // 拡大縮小左上原点で
        float transX = (getWidth() - scale*bgBmp.getWidth()) /2;
        float transY = (getHeight() - scale*bgBmp.getHeight()) /2;
        matrixBG.postTranslate(transX, transY); // 拡大縮小左上原点で
        
        canvas.drawBitmap(bgBmp, matrixBG, null);            
        
        // Canvasにフィギュア描画
        drawFigure(canvas, overlayReverse);

        return newBitmap;
    }
    
    public void setFigureRotateDown() {
        mFigureRotate -= 90.f;
        invalidate();
    }

    public void setFigureRotateUp() {
        mFigureRotate += 90.f;
        invalidate();
    }
}
