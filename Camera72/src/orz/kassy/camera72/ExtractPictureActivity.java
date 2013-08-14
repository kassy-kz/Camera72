package orz.kassy.camera72;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import orz.kassy.camera72.view.RecordFigurePreview;
import orz.kassy.camera72.view.Camera72Utils;
import orz.kassy.camera72.view.Utils;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;


/**
 * 抽出処理を行うアクティビティ
 * ・呼び出されると、直ぐにListSelectDirActivityを呼ぶ
 * ・その戻り値からディレクトリを取得して、作業開始
 * @author kashimoto
 */
public class ExtractPictureActivity extends SherlockActivity implements ActionBar.OnNavigationListener {
    private static final String TAG = null;
    private static final int REQUEST_CODE_LIST_SELECT = 0;
    private static final int OPTIONS_ITEM_ID_EXTRACT = 0;
    private static int sTH = 70;
    private static ExtractPictureActivity sSelf;
    private ImageView mImageViewOrigin;
    private ImageView mImageViewCut;
    private int mWidth;
    private int mHeight;
    private int mFgPictureNumber;
    private int mBgPictureNumber;
    private int mTargetBgImageNum;
    ProgressDialog mDialog;
    private String mDirectory;
    private int[] mColorList;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock); 
        super.onCreate(savedInstanceState);
        setContentView(R.layout.extract_picture);
        sSelf = this;
        
        // ActionBar関連セッティング（リスト）
        mColorList =  getResources().getIntArray(R.array.list_extract_bg_color);
        Context context = getSupportActionBar().getThemedContext();
        ArrayAdapter<CharSequence> list = ArrayAdapter.createFromResource(context, R.array.list_extract_bg_color_string, R.layout.sherlock_spinner_item);
        list.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        getSupportActionBar().setListNavigationCallbacks(list, this);

        // シークバーのセッティング
        SeekBar seekBar = (SeekBar) findViewById(R.id.pickupSeekbar);
        seekBar.setMax(100);
        seekBar.setProgress(30);
        seekBar.setOnSeekBarChangeListener(new MySeekBarListener());
        
        // 他のUI部品セッティング
        mImageViewOrigin = (ImageView) findViewById(R.id.pickupOriginImage);
        mImageViewCut = (ImageView) findViewById(R.id.pickupCutImage);
        
        Intent intent = getIntent();
        mFgPictureNumber = intent.getIntExtra(RecordListActivity.INTENT_EX_FG_COUNT, 1);
        mDirectory = intent.getStringExtra(RecordListActivity.INTENT_EX_DIRECTORY);
        mWidth  = intent.getExtras().getInt(RecordListActivity.INTENT_EX_WIDTH);
        mHeight = intent.getExtras().getInt(RecordListActivity.INTENT_EX_HEIGHT);
        mBgPictureNumber = intent.getExtras().getInt(RecordListActivity.INTENT_EX_BG_COUNT);
        mFgPictureNumber = intent.getExtras().getInt(RecordListActivity.INTENT_EX_FG_COUNT);

        // 背景選定　ワーカースレッド始動
        SelectBgAsyncTask task = new SelectBgAsyncTask(this);
        task.execute();

    }

    /**
     * onResume
     */
    @Override
    protected void onResume() {
        super.onResume();
        // スリープ禁止する
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * onPause
     */
    @Override
    protected void onPause() {
        super.onPause();
        // スリープ禁止をとく
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * リストからディレクトリ名もらって帰ってきた
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(requestCode != REQUEST_CODE_LIST_SELECT){
            finish();
            return;
            
        //　対象ディレクトリ指定して帰ってきた
        }else if(resultCode == RecordListActivity.LIST_SELECT_DIRECTORY){
            // ディレクトリ取得
            mDirectory = intent.getExtras().getString(RecordListActivity.LIST_SELECT_DIRECTORY_NAME);
            mWidth  = intent.getExtras().getInt(RecordListActivity.LIST_SELECT_WIDTH);
            mHeight = intent.getExtras().getInt(RecordListActivity.LIST_SELECT_HEIGHT);
            mBgPictureNumber = intent.getExtras().getInt(RecordListActivity.LIST_SELECT_BG_COUNT);
            mFgPictureNumber = intent.getExtras().getInt(RecordListActivity.LIST_SELECT_FG_COUNT);

            // 背景選定　ワーカースレッド始動
            SelectBgAsyncTask task = new SelectBgAsyncTask(this);
            task.execute();

        }else {
            finish();
            return;
        }
        
    }


    /**
     * 抽出に最適な背景画像を選択する
     * 時間かかるのでワーカースレッドから実行すること
     * @deprecated 
     */
    @SuppressWarnings("unused")
    private int selectBestBackgroundImage(){
        String fgJpgFileUri = Camera72Utils.getFgJpgFileFullPath(mDirectory, 0);        
        String bgJpgFileUri;

        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = false; 
        opt.inPurgeable = true;
        opt.inInputShareable = true;

        int backGroundNum = Utils.loadFromSharedPref(this, RecordFigurePreview.PREF_BG_COUNT);
        int curBestBackNum = 0;
        float curBestBackScore = 100000.f;
        for(int i = 0; i< backGroundNum; i++){
            Log.i(TAG,"selecting bg.. i="+i);
            bgJpgFileUri = Camera72Utils.getBgJpgFileFullPath(mDirectory, i);
            
            // ビットマップ用意
            Bitmap bmpFront = BitmapFactory.decodeFile(fgJpgFileUri, opt);
            Bitmap bmpBack  = BitmapFactory.decodeFile(bgJpgFileUri, opt);
    
            // OpenCVオブジェクトの用意
            Mat matFront = new Mat(mHeight,mWidth,CvType.CV_8UC4); 
            Mat matBack  = new Mat(mHeight,mWidth,CvType.CV_8UC4); 
            org.opencv.android.Utils.bitmapToMat(bmpFront, matFront);
            org.opencv.android.Utils.bitmapToMat(bmpBack, matBack);
            
            // グレースケールに変換
            Mat matFrontGS = new Mat(mHeight,mWidth,CvType.CV_8UC1);
            Mat matBackGS = new Mat(mHeight,mWidth,CvType.CV_8UC1);
            Imgproc.cvtColor(matFront, matFrontGS, Imgproc.COLOR_RGB2GRAY);
            Imgproc.cvtColor(matBack, matBackGS, Imgproc.COLOR_RGB2GRAY);

            // ４点抽出
            float s1 = Math.abs((float)(matFrontGS.get(0, 0)[0] - matBackGS.get(0, 0)[0])); 
            float s2 = Math.abs((float)(matFrontGS.get(mHeight-1, 0)[0] - matBackGS.get(mHeight-1, 0)[0])); 
            float s3 = Math.abs((float)(matFrontGS.get(0, mWidth-1)[0] - matBackGS.get(0, mWidth-1)[0])); 
            float s4 = Math.abs((float)(matFrontGS.get(0, 0)[0] - matBackGS.get(0, 0)[0])); 
            float score = Math.min(s1, Math.min(s2, Math.min(s3, s4)));
            Log.i(TAG,"background score =" +score + ", at"+i);
            if(score < curBestBackScore){
                curBestBackNum = i;
                curBestBackScore = score;
            }
        }          
        Log.i(TAG,"best score background image = "+curBestBackNum);
        return curBestBackNum;
    }
    
    /**
     *  OpenCVで前景抽出して表示する
     * @param fgJpgFileUri
     * @param bgJpgFileUri
     * @param targetPngFileUri 保存先、null可（その場合保存しない）
     * @param maskFileUri マスク画像の保存先、null可（その場合保存しない）
     * @param fromUI UIスレッドからの呼び出しならtrueにする
     */
    private void pickupCVFront(String fgJpgFileUri, String bgJpgFileUri, String targetPngFileUri, String maskFileUri,  boolean fromUI) {
        Log.i(TAG,"fguri = " + fgJpgFileUri);
        Log.i(TAG,"bguri = " + bgJpgFileUri);
        
        // bitmapの設定
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = false; 
        opt.inPurgeable = true;
        opt.inInputShareable = true;
        opt.outHeight = mHeight;
        opt.outWidth = mWidth;

        // ビットマップ用意
        Bitmap bmpFront = BitmapFactory.decodeFile(fgJpgFileUri, opt);
        Bitmap bmpBack  = BitmapFactory.decodeFile(bgJpgFileUri, opt);
        Bitmap bmpCanvas = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);

        // (1)OpenCVオブジェクトの用意
        Mat matFront = new Mat(mHeight,mWidth,CvType.CV_8UC4); 
        Mat matBack  = new Mat(mHeight,mWidth,CvType.CV_8UC4); 
        Mat matCanvas = new Mat(mHeight,mWidth,CvType.CV_8UC4); 

        // (2)ビットマップをOpenCVオブジェクトに変換
        org.opencv.android.Utils.bitmapToMat(bmpFront, matFront);
        org.opencv.android.Utils.bitmapToMat(bmpBack, matBack);
        org.opencv.android.Utils.bitmapToMat(bmpCanvas, matCanvas);
        
        // (3)差分を抽出
        Mat matDiff = new Mat(mHeight,mWidth,CvType.CV_8UC4); 
        Core.absdiff(matBack, matFront, matDiff);

        // (4)差分をグレースケールに変換
        Mat matMask = new Mat(mHeight,mWidth,CvType.CV_8UC1);
        Imgproc.cvtColor(matDiff, matMask, Imgproc.COLOR_RGB2GRAY);

        // (5)しきい値前後で二値化する、これでマスク完成
        Imgproc.threshold(matMask, matMask, sTH, 255, Imgproc.THRESH_BINARY);

        // マスクに対してゴミ除去を行う この例外はここでキャッチする
        try {
            matMask = cleanUpMask(matMask);
        } catch (Exception e) {
            Log.w(TAG,"clean up failed");
            e.printStackTrace();
        }

        // (6)マスクを使って前景だけをとりだす
        Core.add(matCanvas, matFront, matCanvas, matMask);  

        // (7)bitmapに変換
        Bitmap bmpTarget = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        org.opencv.android.Utils.matToBitmap(matCanvas, bmpTarget);

        /**
         * 画像を表示する
         */
        // 縮小
        Bitmap dstFG = Bitmap.createScaledBitmap(bmpFront, mWidth, mHeight, false);
        Bitmap dstBG = Bitmap.createScaledBitmap(bmpTarget, mWidth, mHeight, false);
        // 回転
        Matrix matrix3 = new Matrix();
        matrix3.postRotate(0, mWidth/2, mHeight/2); // 回転
        Bitmap rotFG = Bitmap.createBitmap(dstFG, 0, 0, mWidth, mHeight, matrix3, false);
        Bitmap rotBG = Bitmap.createBitmap(dstBG, 0, 0, mWidth, mHeight, matrix3, false);

        // UIスレッドからの処理の場合のみ、ImageViewへの表示をする
        if(fromUI) {
            drawBitmapOnFitScale(rotBG, mImageViewCut);
            drawBitmapOnFitScale(rotFG, mImageViewOrigin);
        }
        
        // PNGに保存する
        if(targetPngFileUri != null){
            saveBitmapAsPng(targetPngFileUri, bmpTarget);
        }
        
        // マスクを保存してみる（記事用）
        if(maskFileUri != null){
           saveMatMask(matMask, mHeight, mWidth, maskFileUri);
        }        
    }


    /**
     * 二値化マスクを綺麗にする処理を入れる
     * 物体抽出自体がうまく行っていないときは例外が投げられるので注意
     * @param matMask
     * @throws OpenCV処理がうまくいかなかった
     */
    private Mat cleanUpMask(Mat matMask) throws Exception {
        
        //Mat matMask = new Mat(mat.height(),mat.width(),CvType.CV_8UC1);
        //Imgproc.cvtColor(mat, matMask, Imgproc.COLOR_RGB2GRAY);

        // (1)収縮-膨張をしてゴマとりをします
        Mat kernel = Imgproc.getStructuringElement( Imgproc.MORPH_RECT, new Size(3,3));
        for(int i=0; i<2; i++){
            Imgproc.erode(matMask, matMask, kernel); // 収縮
        }
        for(int i=0; i<2; i++){
            Imgproc.dilate(matMask, matMask, kernel); // 膨張
        }

        // (2) 輪郭を抽出します
        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat(matMask.height(),matMask.width(),CvType.CV_8UC1);
        int mode = Imgproc.RETR_EXTERNAL;
        int method = Imgproc.CHAIN_APPROX_SIMPLE;
        Imgproc.findContours(matMask, contours, hierarchy, mode, method);
        
        // (3) 抽出した輪郭の内部を塗りつぶします
        Scalar color = new Scalar(255., 0., 0., 0.);
        Imgproc.drawContours(matMask, contours, -1, color, -1);
        
        // (4)最大長の輪郭について点群（＝輪郭）に外接する傾いていない矩形を求めます．
        Imgproc.boundingRect(contours.get(0));
        
        //（４）（３）で求めた矩形領域内のすべての点について，cv::pointPolygonTestを使って輪郭の内外判定を行い，内ならば塗りつぶします
        ///Imgproc.pointPolygonTest(contour, pt, true);
        return matMask;
    }



    /**
     * bitmapを、imageviewにぴったりあわせて描画する
     */
    private void drawBitmapOnFitScale(Bitmap bmp, ImageView iv){
        float scaleW = (float)iv.getWidth() / (float)bmp.getWidth();  
        float scaleH = (float)iv.getHeight() / (float)bmp.getHeight();  
        float scale = Math.min(scaleW, scaleH);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale); // 拡大縮小左上原点で
        float transX = (iv.getWidth() - scale*bmp.getWidth()) /2;
        float transY = (iv.getHeight() - scale*bmp.getHeight()) /2;
        matrix.postTranslate(transX, transY); // 拡大縮小左上原点で

        iv.setImageMatrix(matrix);
        iv.setImageBitmap(bmp);       

    }
    
    private void saveBitmapAsPng(String targetPngFileUri, Bitmap bmpTarget) {
        CompressFormat format = Bitmap.CompressFormat.PNG;
        FileOutputStream fos = null;
        File file = new File(targetPngFileUri);
        file.getParentFile().mkdirs(); // ディレクトリを作る　
        try {
            fos = new FileOutputStream(file);
            bmpTarget.compress(format, 100, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    /**
     * マットを保存する
     * @param matMask
     * @param rows
     * @param cols
     * @param targetPngFileUri
     */
    private void saveMatMask(Mat matMask, int rows, int cols, String targetPngFileUri){
        // マスクを4チャンネルに変換
        Mat matMaskColor = new Mat(rows, cols, CvType.CV_8UC4);
        Imgproc.cvtColor(matMask, matMaskColor, Imgproc.COLOR_GRAY2RGBA, 4);

        // ビットマップに変換
        Bitmap bmpTarget = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        org.opencv.android.Utils.matToBitmap(matMaskColor, bmpTarget);
        
        saveBitmapAsPng(targetPngFileUri, bmpTarget);
    }
    
    
    /**
     * シークバーリスナー
     */
    class MySeekBarListener implements OnSeekBarChangeListener{
        // トラッキング開始時に呼び出されます
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }
        // トラッキング中に呼び出されます
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
            // しきい値の変更
            sTH = progress;
            Log.i(TAG,"TH = "+sTH);
        }
        // トラッキング終了時に呼び出されます
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.i(TAG,"pickup");
            String fgJpgFileUri = Camera72Utils.getFgJpgFileFullPath(mDirectory, 0);        
            String bgJpgFileUri = Camera72Utils.getBgJpgFileFullPath(mDirectory, mTargetBgImageNum);
            //String targetPngFileUri = SeqShotUtils.getTargePngFileFullPath(1);
            pickupCVFront(fgJpgFileUri, bgJpgFileUri, null,null, true);
        }
    }

    /**
     * OpenCVによる前景抽出　１週分
     */
    private void pickupWholePictures(int pictureMax) {
        PickupAsyncTask task = new PickupAsyncTask(this, pictureMax);
        task.execute();
    }

    /**
     * ピックアップ処理を実施するタスク
     * ダイアログ出したりもする
     * @author kashimoto
     */
    public class PickupAsyncTask extends AsyncTask<Void, Integer, Boolean> implements OnCancelListener{
        final String TAG = "PickupAsyncTask";
        ProgressDialog dialog;
        Context mContext;
        int mFgPictureMax;

        public PickupAsyncTask(Context context, int pictureMax){
            mContext = context;
            mFgPictureMax = pictureMax;
            
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "onPreExecute");
            dialog = new ProgressDialog(mContext);
            dialog.setTitle(getString(R.string.extract_dialog_title));
            dialog.setMessage(getString(R.string.extract_dialog_message));
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setCancelable(false);
            dialog.setOnCancelListener(this);
            dialog.setMax(mFgPictureMax);
            dialog.setProgress(0);
            dialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... arg0) {

            // データベースサムネイル更新
            String thumbPath = Camera72Utils.getTargetPngFileFullPath(mDirectory, 0);
            Camera72Utils.updateDatabaseThumbPath(mContext, mDirectory, thumbPath);

            // 一枚ずつ抽出処理を行う
            for(int i=0; i < mFgPictureMax; i++){
                String fgJpgFileUri = Camera72Utils.getFgJpgFileFullPath(mDirectory, i);        
                String bgJpgFileUri = Camera72Utils.getBgJpgFileFullPath(mDirectory, mTargetBgImageNum);
                String targetPngFileUri = Camera72Utils.getTargetPngFileFullPath(mDirectory, i);
                String maskFileUri = Camera72Utils.getMaskFileFullPath(mDirectory, i);
                // 抽出
                pickupCVFront(fgJpgFileUri, bgJpgFileUri, targetPngFileUri, maskFileUri, false);
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
            //Camera72Utils.updateDatabaseExtractDone(mContext, mDirectory, 1);
            AlertDialog.Builder builder = new AlertDialog.Builder(sSelf);
            builder.setTitle(R.string.extract_complete_dialog_title);
            builder.setMessage(R.string.extract_complete_dialog_message);
            builder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // 終了する
                    sSelf.finish();
                }
            });
            builder.setIcon(R.drawable.ic_main_56);
            builder.setCancelable(false);
            final AlertDialog alertDialog = builder.create();
            alertDialog.show();     

        }

        @Override
        public void onCancel(DialogInterface arg0) {
        }
    }

    /**
     * 背景画像を選定するタスク
     * ダイアログ出したりもする
     * @author kashimoto
     */
    public class SelectBgAsyncTask extends AsyncTask<Void, Integer, Boolean> implements OnCancelListener{
        final String TAG = "PickupAsyncTask";
        ProgressDialog dialog;
        Context mContext;
        private int mCurBestBackNum;

        public SelectBgAsyncTask(Context context){
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "onPreExecute");
            // ダイアログ準備
            dialog = new ProgressDialog(mContext);
            dialog.setTitle("Please wait");
            dialog.setMessage("selecting background...");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//            dialog.setCancelable(true);
            dialog.setOnCancelListener(this);
            int backGroundNum = Utils.loadFromSharedPref(mContext, RecordFigurePreview.PREF_BG_COUNT);
            dialog.setMax(backGroundNum);
            dialog.setProgress(0);
            dialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            String fgJpgFileUri = Camera72Utils.getFgJpgFileFullPath(mDirectory, 0);        
            String bgJpgFileUri;

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = false; 
            opt.inPurgeable = true;
            opt.inInputShareable = true;

            mCurBestBackNum = 0;
            float curBestBackScore = 100000.f;
            for(int i = 0; i< mBgPictureNumber; i++){
                bgJpgFileUri = Camera72Utils.getBgJpgFileFullPath(mDirectory, i);
                Log.i(TAG,"select bg..  i="+i+", bgPath="+bgJpgFileUri+", fgPath="+fgJpgFileUri+", mWidth="+mWidth+", mHeight="+mHeight);
                
                // ビットマップ用意
                Bitmap bmpFront = BitmapFactory.decodeFile(fgJpgFileUri, opt);
                Bitmap bmpBack  = BitmapFactory.decodeFile(bgJpgFileUri, opt);
        
                // OpenCVオブジェクトの用意
                Mat matFront = new Mat(mHeight,mWidth,CvType.CV_8UC4); 
                Mat matBack  = new Mat(mHeight,mWidth,CvType.CV_8UC4); 
                org.opencv.android.Utils.bitmapToMat(bmpFront, matFront);
                org.opencv.android.Utils.bitmapToMat(bmpBack, matBack);
                
                // グレースケールに変換
                Mat matFrontGS = new Mat(mHeight,mWidth,CvType.CV_8UC1);
                Mat matBackGS = new Mat(mHeight,mWidth,CvType.CV_8UC1);
                Imgproc.cvtColor(matFront, matFrontGS, Imgproc.COLOR_RGB2GRAY);
                Imgproc.cvtColor(matBack, matBackGS, Imgproc.COLOR_RGB2GRAY);

                // ４点抽出
                float s1 = Math.abs((float)(matFrontGS.get(0, 0)[0] - matBackGS.get(0, 0)[0])); 
                float s2 = Math.abs((float)(matFrontGS.get(mHeight-1, 0)[0] - matBackGS.get(mHeight-1, 0)[0])); 
                float s3 = Math.abs((float)(matFrontGS.get(0, mWidth-1)[0] - matBackGS.get(0, mWidth-1)[0])); 
                float s4 = Math.abs((float)(matFrontGS.get(0, 0)[0] - matBackGS.get(0, 0)[0])); 
                float score = Math.min(s1, Math.min(s2, Math.min(s3, s4)));
                Log.i(TAG,"background score =" +score + ", at"+i);
                if(score < curBestBackScore){
                    mCurBestBackNum = i;
                    curBestBackScore = score;
                }
                publishProgress(i);
            }          
            Log.i(TAG,"best score background image = "+mCurBestBackNum);
            mTargetBgImageNum = mCurBestBackNum;
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
            Log.d(TAG, "onPostExecute");
            // OpenCVで前景抽出（サンプル一枚）
            String fgJpgFileUri = Camera72Utils.getFgJpgFileFullPath(mDirectory, 0);        
            String bgJpgFileUri = Camera72Utils.getBgJpgFileFullPath(mDirectory, mTargetBgImageNum);
            pickupCVFront(fgJpgFileUri, bgJpgFileUri, null,null,  true);

            // ダイアログ消去
            dialog.dismiss();
        }

        @Override
        public void onCancel(DialogInterface arg0) {
        }
    }

    /**
     * OptionsMenuを生成
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(Menu.NONE, OPTIONS_ITEM_ID_EXTRACT, Menu.NONE, "抜き出し実行")
            .setIcon(R.drawable.shutter)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return true;
    }

    /**
     * OptionsMenu をクリックしたとき
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem menu){
        Log.i(TAG, "Selected menu: "+ menu.getItemId()+", " + menu.getTitle() );
        switch(menu.getItemId()){
            case OPTIONS_ITEM_ID_EXTRACT:
                Log.i(TAG,"mFgPictureNumber="+mFgPictureNumber);
                pickupWholePictures(mFgPictureNumber);
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        View main = findViewById(R.id.pickupMain);
        main.setBackgroundColor(mColorList[itemPosition]);
        return false;
    }
}
