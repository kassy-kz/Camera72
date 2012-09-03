package orz.kassy.camera72.view;

import java.io.IOException;
import java.util.List;

import orz.kassy.camera72.R;
import orz.kassy.tmpl.lib.Utils;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
 * to the surface. We need to center the SurfaceView because not all devices have cameras that
 * support preview sizes at the same aspect ratio as the device's display.
 */
public class RecordFigurePreview extends ViewGroup implements SurfaceHolder.Callback, PreviewCallback {

    // 親Activityへのフィードバック時に用いる定数
    public static final int WORK_UTOMOST_RECORD = 0;
    public static final int WORK_LEGO_RECORED   = 1;
    public static final int WORK_ONESHOT        = 2;
    public static final int WORK_BACKGROUND_SHOT= 3;
    public static final int WORK_ENCODE         = 4;

    public static final String PREF_BG_COUNT = "pref_bg_vcount";
    
    private final String TAG = "MyPreview";
    private OnFeedBackListener mListener;
    private OnGetBitmapListener mBmpListener;
    
    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Size mPreviewSize;

    List<Size> mSupportedPreviewSizes;
    Camera mCamera = null;
    private boolean mIsRecording = false;
    
    // 露出固定がサポートされているか否か。　API Level14未満は問答無用でfalse
    private boolean mIsAutoExposureLockSupported = false;

    // 撮影枚数
    private int mNumberOfPicture=1;
    private int mPictureCnt=0;
    private int mBackgroundCnt=0;
    private boolean mLegoShotFlag;
    private boolean mOneShotFlag;
    private int mDegree;
    private boolean mBackgroundShotFlag;
    ProgressDialog mDialog;
    private int mCurrentComp;
    private int mMaxComp;
    private int mMinComp;
    private String mSeqDir = "null";
    private boolean mGetBmpFlag = false;
    private int rearCameraId = -1;
    private int frontCameraId = -1;
    private int mCurrentCameraId=0;
    private int mCompCountMax;

    

    /**
     * コンストラクタ
     * @param context
     * @param listener
     */
    public RecordFigurePreview(Context context, OnFeedBackListener listener) {
        super(context);
        mListener = listener;
        mSurfaceView = new SurfaceView(context);
        
        addView(mSurfaceView);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        // カメラID
        int numberOfCameras = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                rearCameraId = i;
            }else if(cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT){
                frontCameraId = i;
            }
        }
    }

    /**
     * カメラを切り替える
     */
    public int switchCamera(int width, int height){
        int retVal = -100;
        
        // 複数カメラがないとき
        if(frontCameraId == -1){
            // 背面カメラで返す
            return CameraInfo.CAMERA_FACING_BACK;
        }

        // 現在利用しているカメラを解放
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }

        // カメラを切り替え
        if(mCurrentCameraId == frontCameraId){
            mCurrentCameraId = rearCameraId;
            retVal = CameraInfo.CAMERA_FACING_BACK;
        }else if(mCurrentCameraId == rearCameraId){
            mCurrentCameraId = frontCameraId;
            retVal = CameraInfo.CAMERA_FACING_FRONT;
        }
        
        mCamera = Camera.open(mCurrentCameraId);
        try {
            mCamera.setPreviewDisplay(mHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // プレビューサイズ再設定
        Camera.Parameters params = mCamera.getParameters();
        mSupportedPreviewSizes = params.getSupportedPreviewSizes();
        mPreviewSize = Camera72Utils.getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mCamera.setParameters(params);

        // プレビュー再開
        mCamera.setPreviewCallback(this);
        mCamera.startPreview();

        // 戻り値
        return retVal;
    }
    
    /**
     * AFを発動する
     */
    public void focus() {
        mCamera.autoFocus(null);
    }

    /**
     * 録画を開始する（目一杯連写が始まる）
     * 終了すると WORK_UTOMOST_RECORDを引数にFeedBackCompleteが呼ばれる
     * @param numberOfPicture 撮影枚数
     * @param con context
     */
    @SuppressLint("NewApi")
    public void startRecord(int numberOfPicture, Context con) {
        // すでに撮影中ならば、何もせずに終わる
        if(mIsRecording) {
            return;
        }
        mIsRecording = true;
        mNumberOfPicture = numberOfPicture;

        // APILevel14以上かつ、露出ロック出来る場合のみ、露出ロック
        Camera.Parameters params = mCamera.getParameters();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mIsAutoExposureLockSupported = params.isAutoExposureLockSupported();
            if(mIsAutoExposureLockSupported){
                // 露出ロックする
                params.setAutoExposureLock(true);
                mCamera.setParameters(params);
            }
        }
        
        // データベースに各種値をセット
        mSeqDir = initDatabaseItem(con, mPreviewSize.width, mPreviewSize.height);
        
        // 連写枚数をセット
        mNumberOfPicture = numberOfPicture;
        mPictureCnt = mNumberOfPicture;
        mListener.onFeedBackText("Shooting");

        // ダイアログ出現させる
        mDialog = new ProgressDialog(con);
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setMax(mNumberOfPicture);
        mDialog.setTitle(R.string.record_dialog_title);
        mDialog.setTitle(R.string.record_dialog_message);

        // ダイアログはキャンセル禁止
        mDialog.setCancelable(false);
        mDialog.show();
    }

    /**
     * データベースに初期の値をセットする
     * @param con
     * @param width
     * @param height
     * @return ディレクトリ名
     */
    public String initDatabaseItem(Context con, int width, int height) {
        SQLiteDatabase dataBase;
        // データベースオープン
        Camera72SQLiteOpenHelper helper = new Camera72SQLiteOpenHelper(con);
        dataBase = helper.getReadableDatabase();

        // 値をセット
        ContentValues values;
        long currentTime = System.currentTimeMillis(); 
        String directory = currentTime+""; 
        values = new ContentValues();
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_NAME,      directory);
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_DIRECTORY, directory);
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_DATE,      currentTime);
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_WIDTH,     width);
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_HEIGHT,    height);
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_FG_COUNT,  mNumberOfPicture);
        dataBase.insert(Camera72SQLiteOpenHelper.TABLE_NAME, null, values);    
        
        // データベースをクローズ
        dataBase.close();
        
        return directory;
    }
    
    /**
     * フラグをセットする、このフラグをセットすると一枚撮る（そして即jpgに）
     * @param degree
     */
    public void setOneShotFlag(int degree) {
        mOneShotFlag = true;
        mDegree = degree;
    }

    /**
     * フラグをセットする。これをするとbitmapを返す
     */
    public void setGetBmpFlag(OnGetBitmapListener listener){
        mBmpListener = listener;
        mGetBmpFlag = true;
    }
    
    /**
     * フラグをセットする、LEGO360モータースレッドからはこのフラグをセットする
     * @param flag
     */
    public void setLegoShotFlag(int degree) {
        mLegoShotFlag = true;
        mDegree = degree;
    }
    
    /**
     * フラグセット、背景撮影を開始する
     */
    public void setBackgroundShotFlag(Context con) {
        mBackgroundShotFlag = true;
        Camera.Parameters params = mCamera.getParameters();

        // 露出ロックができている場合のみ、一枚だけ背景撮影
        if(mIsAutoExposureLockSupported){
            Log.i(TAG,"exposure is locked");            
            mBackgroundCnt = 0;
            mCompCountMax = 1;
        // 露出ロックができていない場合は、露出絨毯爆撃
        } else {
            Log.i(TAG,"comp min, max = "+mMinComp + ","+mMaxComp);

            // 露出補正ステップを取得 -> 実は意味がなかった
            // compStep = params.getExposureCompensationStep();
            
            // 露出補正して撮影する枚数をセット
            mCompCountMax = mMaxComp - mMinComp + 1;
            Camera72Utils.updateDatabaseBgCnt(con, mSeqDir, mCompCountMax);

            // カウンターセット（カウンターは０を含むので１引いとく）
            mBackgroundCnt = mCompCountMax-1;

            // とりま露出補正は一番下に
            params.setExposureCompensation(mMinComp);
            mCurrentComp = mMinComp;
            mCamera.setParameters(params);
        }
        
        // ダイアログだす
        mDialog = new ProgressDialog(con);
        mDialog.setTitle("getting background...");
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setMax(mBackgroundCnt);
        // ダイアログはキャンセル禁止
        mDialog.setCancelable(false);
        // 出現
        mDialog.show();
    }

    /**
     * エンコードボタンが押された、エンコード開始する
     */
    public void encodeButtonPressed(Context con) {
        EncodeAsyncTask task = new EncodeAsyncTask(con);
        task.execute();        
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            // ここでプレビューサイズ取得
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
            }

            // Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0,
                        (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2,
                        width, (height + scaledChildHeight) / 2);
            }
        }
    }

    /**
     * サーフェス生成処理を行う
     * ここでカメラオブジェクト生成も行う
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG,"surfaceCreated");
        try {
            if (mCamera == null) {
                // カメラ取得
                mCamera = Camera.open();
                mCurrentCameraId = rearCameraId;
                
                // プレビューサイズ一覧
                Camera.Parameters params = mCamera.getParameters();
                mSupportedPreviewSizes = params.getSupportedPreviewSizes();
                requestLayout();
                
                // 露出補正上限下限を取得
                mMaxComp = params.getMaxExposureCompensation();
                mMinComp = params.getMinExposureCompensation();
                mCurrentComp = params.getExposureCompensation();

                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }


    /**
     * サーフェスのサイズが変更された時に呼ばれる
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // surfaceviewのサイズ確認
        Log.i(TAG, "surfaceChanged (w,h) = " + width + ", " + height);

        // 一旦プレビュー停止
        mCamera.stopPreview();

        // ここでカメラプレビューサイズを設定する
        mPreviewSize = Camera72Utils.getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        Log.i(TAG, "  fix preview size w*h = "+mPreviewSize.width + ","+mPreviewSize.height);
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        Camera72Utils.updateDatabasePictureSize(getContext(), mSeqDir, mPreviewSize.width, mPreviewSize.height);
        // requestLayout();
        mCamera.setParameters(parameters);

        // プレビュー再開
        mCamera.setPreviewCallback(this);
        mCamera.startPreview();
    }

    
    /**
     * プレビューキャプチャ取得コールバック
     * ここで画像のバイナリ保存を行う
     * TODO スレッド化したほうがいいんだろうか
     */
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        // （キャプチャモード時のみ）キャプチャしたバイナリを保存する
        if(mIsRecording && mPictureCnt>=0) {
            // ログ
            Log.i(TAG, "shooting "+mPictureCnt);
            mListener.onFeedBackText("shooting " + mPictureCnt);

            // バイナリを保存
            saveCaptureAsBin(data, mPictureCnt);
            mPictureCnt--;

            // ダイアログを進める
            mDialog.incrementProgressBy(1);
        }
        
        // 背景を保存　
        if(mBackgroundShotFlag && mBackgroundCnt >=0) {
            
            // バイナリを保存
            saveBackgroundCaptureAsBin(data, mBackgroundCnt);
            mBackgroundCnt--;

            // ダイアログを進める
            mDialog.incrementProgressBy(1);
            
            // 次に備えて露出を修正
            Camera.Parameters params = mCamera.getParameters();
            Log.i(TAG,"setComp" + mCurrentComp );
            params.setExposureCompensation(mCurrentComp);
            mCamera.setParameters(params);
            mCurrentComp++;
        }

        // 一枚限りの保存　この場合はエンコードまでおえてしまう
        if(mOneShotFlag) {
            // バイナリを保存
            int width = mPreviewSize.width;
            int height = mPreviewSize.height;
            Log.i(TAG,"one shot at degree = " + mDegree);
            saveCaptureAsBin(data, mDegree);
            String binFileFullPath = Camera72Utils.getFgBinFileFullPath(mSeqDir, mDegree);
            String jpgFileFullPath = Camera72Utils.getFgJpgFileFullPath(mSeqDir, mDegree);
            try {
                Camera72Utils.encodeBin2Jpg(mSurfaceView.getContext(), width, height, binFileFullPath, jpgFileFullPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mOneShotFlag = false;
        }

        // BMP取得要求の場合、これはオーバーレイ時にビットマップを返す
        if(mGetBmpFlag && mBmpListener !=null) {
            mGetBmpFlag = false;
            Log.i(TAG,"get bmp");

            // バイナリを保存
            int width = mPreviewSize.width;
            int height = mPreviewSize.height;
            try {
                Bitmap bmp = Camera72Utils.encodeBin2Bmp(mSurfaceView.getContext(), width, height, data);
                mBmpListener.onGetBitmap(bmp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        if(mLegoShotFlag) {
            // バイナリを保存
            Log.i(TAG,"lego shot at degree = " + mDegree);
            saveCaptureAsBin(data, mDegree);
            mLegoShotFlag = false;
        }
//        camera.setPreviewCallback(this);
//        invalidate();
    }

    /**
     *  キャプチャーデータをバイナリ保存する
     */
    private void saveCaptureAsBin(byte[] data, int cnt) {
        String binFileFullPath = Camera72Utils.getFgBinFileFullPath(mSeqDir, cnt);
        try {
            Utils.saveBinAsFile(data, binFileFullPath);
        } catch (IOException e) {
            Log.i(TAG, "file error at "+cnt);
            mListener.onFeedBackText("file error at "+cnt);                
            e.printStackTrace();
        }
        if(cnt == 0){
            mIsRecording = false;
            mListener.onFeedBackText("shooting "+cnt);
            mDialog.dismiss();
            mListener.onFeedBackComplete(WORK_UTOMOST_RECORD);
        }
    }
    
    
    /**
     * 背景画像のバイナリ保存をする
     * @param data
     * @param cnt
     */
    private void saveBackgroundCaptureAsBin(byte[] data, int cnt) {
        String binbgFileFullPath = Camera72Utils.getBgBinFileFullPath(mSeqDir,  cnt);
        try {
            Utils.saveBinAsFile(data, binbgFileFullPath);
        } catch (IOException e) {
            Log.i(TAG, "file error at "+cnt);
            mListener.onFeedBackText("file error at "+cnt);                
            e.printStackTrace();
        }
        if(cnt == 0){
            mIsRecording = false;
            mListener.onFeedBackText("shooting "+cnt);
            mDialog.dismiss();
            mListener.onFeedBackComplete(WORK_BACKGROUND_SHOT);
        }
    }

    
    /**
     * セットとかする
     */
    public void setMode() {
        Camera.Parameters param = mCamera.getParameters();
        param.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_SHADE);
        Log.i(TAG,"exposure ="+param.getExposureCompensationStep());
        Log.i(TAG,"exposure ="+param.getExposureCompensation());
        try{
            mCamera.setParameters(param);
        }catch(Exception e){
            
        }
    }
    
    /**
     * 親Activityにフィードバック情報を返す
     * @author kashimoto
     */
    public interface OnFeedBackListener {
        void onFeedBackText(String text);
        void onFeedBackComplete(int work);
        void onFeedBackProgress(int progress);
    }

    /**
     * ビットマップを取得するコールバックを定義する
     */
    public interface OnGetBitmapListener {
        void onGetBitmap(Bitmap bmp);
    }
    
    /**
     * エンコード処理を実施するタスク
     * ダイアログ出したりもする
     * @author kashimoto
     */
    public class EncodeAsyncTask extends AsyncTask<Void, Integer, Boolean> implements OnCancelListener{
        final String TAG = "PickupAsyncTask";
        ProgressDialog dialog;
        Context context;
        int mBackgroundMax;

        public EncodeAsyncTask(Context context){
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "onPreExecute");
            mBackgroundMax = mCompCountMax;

            // ダイアログ出す
            dialog = new ProgressDialog(context);
            dialog.setTitle(R.string.encode_dialog_title);
            dialog.setMessage(getContext().getString(R.string.encode_dialog_message));
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setCancelable(false);
            dialog.setOnCancelListener(this);
            dialog.setMax(mNumberOfPicture + mBackgroundMax);
            dialog.setProgress(0);
            dialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            int width = mPreviewSize.width;
            int height = mPreviewSize.height;
            
            // データベースサムネイル更新
            String thumbPath = Camera72Utils.getFgJpgFileFullPath(mSeqDir, 0);
            Camera72Utils.updateDatabaseThumbPath(context, mSeqDir, thumbPath);

            // 前景画像をエンコード
            Log.i(TAG,"****** cnt = "+mNumberOfPicture);
            for(int i=0;i<mNumberOfPicture; i++){
                // binファイルを読み込み
                String binFileFullPath = Camera72Utils.getFgBinFileFullPath(mSeqDir, i);
                String jpgFileFullPath = Camera72Utils.getFgJpgFileFullPath(mSeqDir, i);
                Log.i(TAG,"*****binFileFullPath=" +binFileFullPath);
                Log.i(TAG,"*****binFileFullPath=" +jpgFileFullPath);
                try {
                    Camera72Utils.encodeBin2Jpg(mSurfaceView.getContext(), width, height, binFileFullPath, jpgFileFullPath);
                    dialog.incrementProgressBy(1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                publishProgress(i);                
            }
            
            // 次、背景画像軍をエンコード
            for(int i=0;i<mBackgroundMax; i++){
                // binファイルを読み込み
                String binFileFullPath = Camera72Utils.getBgBinFileFullPath(mSeqDir, i);
                String jpgFileFullPath = Camera72Utils.getBgJpgFileFullPath(mSeqDir, i);
                try {
                    Camera72Utils.encodeBin2Jpg(mSurfaceView.getContext(), width, height, binFileFullPath, jpgFileFullPath);
                    dialog.incrementProgressBy(1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                publishProgress(i);                
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.d(TAG, "onProgressUpdate - " + values[0]);
            dialog.setProgress(values[0]);
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "onCancelled");
            dialog.dismiss();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            dialog.dismiss();
            mListener.onFeedBackComplete(WORK_ENCODE);
        }

        @Override
        public void onCancel(DialogInterface arg0) {
        }
    }

    public void expDown() {
        // 露出を修正
        Camera.Parameters params = mCamera.getParameters();
        mCurrentComp--;
        if(mCurrentComp < mMinComp){
            mCurrentComp = mMinComp;
        }
        params.setExposureCompensation(mCurrentComp);
        mCamera.setParameters(params);
    }

    public void expUp() {
        // 露出を修正
        Camera.Parameters params = mCamera.getParameters();
        mCurrentComp++;
        if(mCurrentComp > mMaxComp){
            mCurrentComp = mMaxComp;
        }
        params.setExposureCompensation(mCurrentComp);
        mCamera.setParameters(params);
    }

    /**
     * ファイル保存ディレクトリを指定する
     * @param seqDir
     */
    public void setFilePaths(String seqDir){
        mSeqDir  = seqDir;
    }
}