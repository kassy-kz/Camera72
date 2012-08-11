package orz.kassy.camera72;

import static android.view.ViewGroup.LayoutParams.*;

import java.util.Timer;
import java.util.TimerTask;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import orz.kassy.camera72.view.*;
import orz.kassy.camera72.view.RecordFigurePreview.OnFeedBackListener;
import orz.kassy.tmpl.lib.Utils;

/**
 * フィギュアの撮影を行うアクティビティ
 * 力の限り回し続ける撮影方法
 * @author kashimoto
 */
public class RecordActivity extends SherlockActivity implements ActionBar.OnNavigationListener, OnFeedBackListener, OnDismissListener {

    // 撮影終了から人形除去までの許容時間
    private static final int TIME_FOR_REMOVING_FIGURE = 3000;
    private static final String TAG = "RecordActivity";

    // 撮影のアスペクト比
    static final float RATIO_H = 3;
    static final float RATIO_W = 4;

    private static final int OPTIONS_ITEM_ID_UP      = 0;
    private static final int OPTIONS_ITEM_ID_DOWN    = 1;
    private static final int OPTIONS_ITEM_ID_FOCUS   = 2;
    private static final int OPTIONS_ITEM_ID_SHUTTER = 3;
    
    private RecordFigurePreview mPreview;
    Camera mCamera;
    int numberOfCameras;
    int cameraCurrentlyLocked;

    // The first rear facing camera
    int defaultCameraId;
    private Handler mRecordUIHandler;
    static RecordActivity sSelf;
    private String[] mNumberList;
    private int[] mNumberListInteger;
    
    private int mNumberOfPicture=1;
    private FrameLayout mParentFrameLayout;
    private FrameLayout mFrameLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock); 
        super.onCreate(savedInstanceState);
        sSelf = this;
        
        // レイアウトセット
        setContentView(R.layout.record);

        // ActionBar関連セッティング（リスト）
        mNumberList = getResources().getStringArray(R.array.number_of_picture);
        mNumberListInteger = getResources().getIntArray(R.array.number_of_picture_int);
        Context context = getSupportActionBar().getThemedContext();
        ArrayAdapter<CharSequence> list = ArrayAdapter.createFromResource(context, R.array.number_of_picture, R.layout.sherlock_spinner_item);
        list.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        getSupportActionBar().setListNavigationCallbacks(list, this);

        // グリッド貼り付け（デバッグ用途）
        mParentFrameLayout = (FrameLayout) findViewById(R.id.cameraParent);
        MyGridView grid = new MyGridView(this);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        mParentFrameLayout.addView(grid,0, params);

        // カメラビューを生成して貼り付け
        mPreview = new RecordFigurePreview(this, this);
        mFrameLayout = (FrameLayout)findViewById(R.id.cameraSpaceView);
        mFrameLayout.addView(mPreview, 0, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));

        // 注意：この段階ではwidth, heightは取得不可能
        //Log.i(TAG,"layout width*height = " + mLinearLayout.getWidth() + ", " + mLinearLayout.getHeight());

        // カメラの数を取得
        numberOfCameras = Camera.getNumberOfCameras();

        // カメラID
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                defaultCameraId = i;
            }
        }

        // UIハンドラ
        mRecordUIHandler = new Handler();

    }

    /**
     * onResume
     */
    @Override
    protected void onResume() {
        super.onResume();

        // カメラオープン
//        mCamera = Camera.open();
//        cameraCurrentlyLocked = defaultCameraId;
//        mPreview.setCamera(mCamera);

        // スリープ禁止する
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);        
    }

    /**
     * onPause
     */
    @Override
    protected void onPause() {
        super.onPause();

        // カメラクローズ
        if (mCamera != null) {
//            mPreview.setCamera(null);
//            mCamera.stopPreview();
//            mCamera.setPreviewCallback(null);
//            mCamera.release();
//            mCamera = null;
        }

        // スリープ禁止解除
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);        
    }
    
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.i(TAG,"onWindowFocusChange");
        Log.i(TAG,"  parent view width="+mParentFrameLayout.getWidth()+", height="+mParentFrameLayout.getHeight());

        // surfaceViewのサイズをここで変更する
        int width, height;

        // 親Viewのサイズに収まる、かつアスペクト指定の最大のサイズを取得する
        {
            float targetRatio = (float)RATIO_H / (float)RATIO_W;
            float parentRatio = (float)mParentFrameLayout.getHeight() / (float)mParentFrameLayout.getWidth(); 
            if( parentRatio > targetRatio ){  //  親ratio 3.5/4 -> 親ratio 3/4  wそのまま h修正 
                width  = mParentFrameLayout.getWidth();
                height = (int)( (float)mParentFrameLayout.getWidth() * targetRatio);
            }else {                           //  親ratio 3/5 -> 親ratio 3/4  hそのまま w修正
                height = mParentFrameLayout.getHeight();
                width  = (int)( (float)mParentFrameLayout.getHeight() / targetRatio);
            }
        }
        
        // 入れ子元 surfaceview のサイズを設定する
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, Gravity.LEFT);
        Log.i(TAG,"  target width="+width+", height="+height);
        mFrameLayout.setLayoutParams(params);
    }

    /**
     * on feedback from preview
     */
    @Override
    public void onFeedBackText(String text) {
        //TODO なんか
    }

    /**
     * 作業完了時に呼ばれる
     */
    @Override
    public void onFeedBackComplete(int work) {
        switch(work){
            // 録画を終えた
            case RecordFigurePreview.WORK_UTOMOST_RECORD:
                // フィギュ除去促しダイアログを出す
                showRemoveFigureDialog(this, mRecordUIHandler, this);
                break;
            // 背景撮影を終えた
            case RecordFigurePreview.WORK_BACKGROUND_SHOT:
                // エンコード処理に入る
                Log.i(TAG,"encode button");
                mPreview.encodeButtonPressed(this);
                break;
            // エンコードを終えた
            case RecordFigurePreview.WORK_ENCODE:
                Utils.showToast(this, "エンコード終了しました");
                break;
            default:
                break;
        }
    }

    @Override
    public void onFeedBackProgress(int progress) {
    }

    /**
     *　全周撮影後、フィギュア除去促しダイアログが消えた後の処理
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        Log.i(TAG,"shot background");
        // 背景撮影に入る
        mPreview.setBackgroundShotFlag(this);
    }

    /**
     * ActionBar : リスト選択（枚数選択）したとき
     */
    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        Log.i(TAG,"Selected: " + mNumberListInteger[itemPosition]);
        mNumberOfPicture = mNumberListInteger[itemPosition];
        return false;
    }
    
    /**
     * OptionsMenu をクリックしたとき
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem menu){
        Log.i(TAG, "Selected menu: "+ menu.getItemId()+", " + menu.getTitle() );
        switch(menu.getItemId()){
            case OPTIONS_ITEM_ID_UP:
                mPreview.expUp();
               break;
            case OPTIONS_ITEM_ID_DOWN:
                mPreview.expDown();
                break;
            case OPTIONS_ITEM_ID_FOCUS:
                // フォーカス合わせる
                mPreview.focus();
                break;
            case OPTIONS_ITEM_ID_SHUTTER:
                // 撮影開始
                mPreview.startRecord(mNumberOfPicture, this);
                break;
             default:
                break;
        }
        return false;
    }
    
    /**
     * OptionsMenuを生成
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(Menu.NONE, OPTIONS_ITEM_ID_UP, Menu.NONE, "Up")
            .setIcon(android.R.drawable.arrow_up_float)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, OPTIONS_ITEM_ID_DOWN, Menu.NONE, "Down")
            .setIcon(android.R.drawable.arrow_down_float)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, OPTIONS_ITEM_ID_FOCUS, Menu.NONE, "Focus")
            .setIcon(R.drawable.focus)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        menu.add(Menu.NONE, OPTIONS_ITEM_ID_SHUTTER, Menu.NONE, "Start")
            .setIcon(R.drawable.shutter)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

//        menu.add("Search")
//            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return true;
    }

    
    
    /**
     * フィギュア除去を促すダイアログを出す（カウントダウン）
     * ３秒で終了するよ
     * @param con
     */
    public static void showRemoveFigureDialog(Context con, final Handler handler, DialogInterface.OnDismissListener dismissListener){
        AlertDialog.Builder builder = new AlertDialog.Builder(con);

        // アラートダイアログのタイトル、メッセージを設定
        builder.setTitle(R.string.remove_figure_dialog_title);
        builder.setIcon(R.drawable.ic_main_128);

        // アラートダイアログのキャンセルが可能かどうかを設定
        builder.setCancelable(false);

        // アラートダイアログを表示
        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnDismissListener(dismissListener);
        alertDialog.show();     

        Timer mTimer = new Timer(true);
        mTimer.schedule( new TimerTask(){
            @Override
            public void run() {
                // mHandlerを通じてUI Threadへ処理をキューイング
                handler.post( new Runnable() {
                    public void run() {
                        alertDialog.cancel();
                    }
                });
            }
        }, TIME_FOR_REMOVING_FIGURE);
    }
}
