package orz.kassy.camera72;

import static android.view.ViewGroup.LayoutParams.*;

import java.io.FileNotFoundException;
import java.io.InputStream;
import org.opencv.core.Size;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import orz.kassy.camera72.view.Utils;
import orz.kassy.camera72.view.OverlayFigureView;
import orz.kassy.camera72.view.RecordFigurePreview;
import orz.kassy.camera72.view.RecordFigurePreview.OnFeedBackListener;
import orz.kassy.camera72.view.RecordFigurePreview.OnGetBitmapListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;


/**
 * 画像合成を行うアクティビティ
 * @author kashimoto
 */
public class OverlayImageActivity extends SherlockActivity implements OnClickListener, OnFeedBackListener, OnGetBitmapListener {

    public static final String BACKGROUND_TYPE = "bgtype";
    public static final int BACKGROUND_TYPE_IMAGE  = 1;
    public static final int BACKGROUND_TYPE_CAMERA = 2;    
    public static final int BACKGROUND_TYPE_NONE   = 3;
    
    private static final String TAG = "OverlayImage";
    private static final int GALLERY_IMAGE = 0;
    private static final int REQUEST_CODE_LIST_SELECT = 1;
    private static final int OPTIONS_ITEM_ID_ROTATE_UP     = 1;
    private static final int OPTIONS_ITEM_ID_ROTATE_DOWN   = 2;
    private static final int OPTIONS_ITEM_ID_FOCUS         = 3;
    private static final int OPTIONS_ITEM_ID_SHUTTER       = 4;
    private static final int OPTIONS_ITEM_ID_EXP_FIGURE_UP = 5;
    private static final int OPTIONS_ITEM_ID_EXP_FIGURE_DOWN = 6;
    private static final int OPTIONS_ITEM_ID_FEATHER_FIGURE_UP = 7;
    private static final int OPTIONS_ITEM_ID_FEATHER_FIGURE_DOWN = 8;
    private static final int OPTIONS_ITEM_ID_SWITCH = 9;
    
    static final float RATIO_H = 3;
    static final float RATIO_W = 4;

    // フィギュアの画像を調整するためのパラメータ
    private double mCvScalar = 1.d;
    private Size mCvSize = new Size(1.d, 1.d);
    
    // 他
    private int mBackgroundType = BACKGROUND_TYPE_NONE;
    private Bitmap mBackgroundImageBitmap;
    private ImageView mBackgroudImageView;
    private RecordFigurePreview mPreview;
    private String mDirectory = null;
    private FrameLayout mImageArea;
    private OverlayFigureView mOverlayFigureView;
    private int mCurrentCamera;

    @Override
	public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock); 
		super.onCreate(savedInstanceState);

		// 画面構成
		setContentView(R.layout.overlay_figure);		
        mImageArea = (FrameLayout)findViewById(R.id.overlayImageArea);

        // intentから背景タイプを取得する
        Intent intentFrom = getIntent();
        mBackgroundType = intentFrom.getExtras().getInt(BACKGROUND_TYPE, BACKGROUND_TYPE_NONE);
        
        // 背景となるビューを生成して貼り付ける
        switch(mBackgroundType){
            case BACKGROUND_TYPE_IMAGE:
                // イメージビューを新造・貼り付け
                mBackgroudImageView = new ImageView(this);
                mImageArea.addView(mBackgroudImageView, 0, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));
                break;
            case BACKGROUND_TYPE_CAMERA:
                // カメラビューを貼り付け
                mPreview = new RecordFigurePreview(this, this);
                mImageArea.addView(mPreview, 0, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));
                break;
            case BACKGROUND_TYPE_NONE:
                mBackgroudImageView = new ImageView(this);
                mImageArea.addView(mBackgroudImageView, 0, new FrameLayout.LayoutParams(0, 0));
                break;
        }

        // ディレクトリ情報を取得する（ないこともある）
        mDirectory = intentFrom.getStringExtra(RecordListActivity.INTENT_EX_DIRECTORY);
        Log.i(TAG, "intent from directory="+mDirectory);
        
        if(mDirectory==null){
            // フィギュアリスト選択画面を呼び出す
            Intent intentTo = new Intent(this, RecordListActivity.class);
            startActivityForResult(intentTo, REQUEST_CODE_LIST_SELECT);
        }

        int fgCount = intentFrom.getIntExtra(RecordListActivity.INTENT_EX_FG_COUNT, 1);

        // 重畳するフィギュアのビューを貼り付け
        mOverlayFigureView = new OverlayFigureView(this, mDirectory, fgCount);
        mImageArea.addView(mOverlayFigureView, 1, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));

        // 背景を設定する
        switch(mBackgroundType){
            // ギャラリーから背景用画像を選択する（ギャラリーを呼び出す）
            case BACKGROUND_TYPE_IMAGE:
                Intent intent2 = new Intent();
                intent2.setType("image/*");
                intent2.setAction(Intent.ACTION_PICK);
                startActivityForResult(intent2,GALLERY_IMAGE);
                break;
        }
	}
    
    /**
     * onResume
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 簡単な使い方トーストを出す。（写真に重ねあわせ、の場合は後で）
        if (mBackgroundType != BACKGROUND_TYPE_IMAGE) {
            Utils.showToast(this, R.string.overlay_inst_message);
        }
    }
    
    
    /**
     * 別アクティビティから帰ってきたときの処理
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // 別画面を呼んでフィギュア画像を選択してきた
        if(requestCode == REQUEST_CODE_LIST_SELECT && 
               resultCode == RecordListActivity.LIST_SELECT_DIRECTORY){

            // ディレクトリ取得
            mDirectory = intent.getExtras().getString(RecordListActivity.LIST_SELECT_DIRECTORY_NAME);
            int fgCount = intent.getIntExtra(RecordListActivity.LIST_SELECT_FG_COUNT, 1);

            // 重畳するフィギュアのビューを貼り付け
            mOverlayFigureView = new OverlayFigureView(this, mDirectory, fgCount);
            mImageArea.addView(mOverlayFigureView, 1, new FrameLayout.LayoutParams(FILL_PARENT, FILL_PARENT));

            // 背景を設定する
            switch(mBackgroundType){
                // ギャラリーから背景用画像を選択する（ギャラリーを呼び出す）
                case BACKGROUND_TYPE_IMAGE:
                    Intent intent2 = new Intent();
                    intent2.setType("image/*");
                    intent2.setAction(Intent.ACTION_PICK);
                    startActivityForResult(intent2,GALLERY_IMAGE);
                    break;
            }
            
        // ギャラリーから画像取得した（mBackgroundType が BACKGROUND_TYPE_IMAGEの時のみここを通る）
        }else if (requestCode == GALLERY_IMAGE && resultCode == RESULT_OK) {
            try {
                Uri uri = intent.getData();
                InputStream is;
                is = getContentResolver().openInputStream(uri);
                mBackgroundImageBitmap = BitmapFactory.decodeStream(is);
                mBackgroudImageView.setImageBitmap(mBackgroundImageBitmap);
                Utils.showToast(this, R.string.overlay_inst_message);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            finish();
            return;
        }
    }

    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(mBackgroundType == BACKGROUND_TYPE_CAMERA){
            Log.i(TAG,"onWindowFocusChange");
    
            FrameLayout parent = (FrameLayout) findViewById(R.id.overlayImageParent);
            Log.i(TAG,"  parent view width="+parent.getWidth()+", height="+parent.getHeight());
    
            // surfaceViewのサイズをここで変更する
            int width, height;
    
            // 親Viewのサイズに収まる、かつアスペクト指定の最大のサイズを取得する
            {
                float targetRatio = (float)RATIO_H / (float)RATIO_W;
                float parentRatio = (float)parent.getHeight() / (float)parent.getWidth(); 
                if( parentRatio > targetRatio ){  //  親ratio 3.5/4 -> 親ratio 3/4  wそのまま h修正 
                    width  = parent.getWidth();
                    height = (int)( (float)parent.getWidth() * targetRatio);
                }else {                           //  親ratio 3/5 -> 親ratio 3/4  hそのまま w修正
                    height = parent.getHeight();
                    width  = (int)( (float)parent.getHeight() / targetRatio);
                }
            }
            
            // 入れ子元 surfaceview のサイズを設定する
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, Gravity.LEFT);
            Log.i(TAG,"  target width="+width+", height="+height);
            mImageArea.setLayoutParams(params);
        }
    }

    
    /**
     * OptionsMenuを生成
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        SubMenu subRotate = menu.addSubMenu("Rotate");
        subRotate.add(Menu.NONE, OPTIONS_ITEM_ID_ROTATE_DOWN, 0, R.string.rotate_left);
        subRotate.add(Menu.NONE, OPTIONS_ITEM_ID_ROTATE_UP,   0, R.string.rotate_right);
        subRotate.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        SubMenu subExp = menu.addSubMenu("Figure");
        subExp.add(Menu.NONE, OPTIONS_ITEM_ID_EXP_FIGURE_UP, 0, R.string.figure_exp_up);
        subExp.add(Menu.NONE, OPTIONS_ITEM_ID_EXP_FIGURE_DOWN,   0, R.string.figure_exp_down);
        subExp.add(Menu.NONE, OPTIONS_ITEM_ID_FEATHER_FIGURE_UP, 0, R.string.figure_feather_up);
        subExp.add(Menu.NONE, OPTIONS_ITEM_ID_FEATHER_FIGURE_DOWN,   0, R.string.figure_feather_down);
        subExp.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        if(mBackgroundType == BACKGROUND_TYPE_CAMERA){
            menu.add(Menu.NONE, OPTIONS_ITEM_ID_FOCUS, Menu.NONE, "Focus")
            .setIcon(R.drawable.focus)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            menu.add(Menu.NONE, OPTIONS_ITEM_ID_SWITCH, Menu.NONE, "Switch")
            .setIcon(android.R.drawable.ic_menu_camera)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
        
        menu.add(Menu.NONE, OPTIONS_ITEM_ID_SHUTTER, Menu.NONE, "Shutter")
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
            case OPTIONS_ITEM_ID_ROTATE_UP:
                Log.i(TAG, "rotate up2");
                mOverlayFigureView.setFigureRotateUp();
               break;
            case OPTIONS_ITEM_ID_ROTATE_DOWN:
                Log.i(TAG, "rotate down");
                mOverlayFigureView.setFigureRotateDown();
                break;
                
            // カメラチェンジ
            case OPTIONS_ITEM_ID_SWITCH:
                Log.i(TAG, "switch");
                mCurrentCamera = mPreview.switchCamera(mPreview.getWidth(), mPreview.getHeight());
                break;

            // 明るさアップ
            case OPTIONS_ITEM_ID_EXP_FIGURE_UP:
                mCvScalar+=0.2d;
                mOverlayFigureView.setCvBrightScalar(mCvScalar);
                break;

            // 明るさダウン(ただしAも下がって透明になってしまう)
            case OPTIONS_ITEM_ID_EXP_FIGURE_DOWN:
                mCvScalar-=0.2d;
                mOverlayFigureView.setCvBrightScalar(mCvScalar);
                break;

            // ぼかし
            case OPTIONS_ITEM_ID_FEATHER_FIGURE_UP:
                mCvSize.height+=20.d;
                mCvSize.width+=20.d;
                mOverlayFigureView.setCvGaussianSize(mCvSize);
                break;
            case OPTIONS_ITEM_ID_FEATHER_FIGURE_DOWN:
                mCvSize.height-=20.d;
                mCvSize.width-=20.d;
                if(mCvSize.height<1){
                    mCvSize.height=1;
                    mCvSize.width=1;
                }
                mOverlayFigureView.setCvGaussianSize(mCvSize);
                break;

            // フォーカス
            case OPTIONS_ITEM_ID_FOCUS:
                // フォーカス合わせる
                mPreview.focus();
                break;

            // 合成画像を保存する
            case OPTIONS_ITEM_ID_SHUTTER:
                Log.i(TAG, "shutter");
                // 背景によって若干処理を変える
                switch(mBackgroundType){
                    // 背景がカメラのときのみ、すぐにはbitmapが取得できないのでコールバック待ちにする
                    case BACKGROUND_TYPE_CAMERA:
                        // 非同期処理でカメラプレビューからビットマップ取得
                        // コールバックはonGetBitmap
                        mPreview.setGetBmpFlag(this);
                        break;
                    // それ以外はここで撮る
                    case BACKGROUND_TYPE_IMAGE:
                        mOverlayFigureView.saveMergeBitmap(mBackgroundImageBitmap, OverlayFigureView.OVERLAY_NORMAL);
                        Utils.showToast(this, R.string.complete_save_picture);
                        break;
                    default:
                        break;
                }
                break;
             default:
                break;
        }
        return false;
    }

    @Override
    public void onFeedBackText(String text) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onFeedBackComplete(int work) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onFeedBackProgress(int progress) {
        // TODO Auto-generated method stub
    }

    /**
     * カメラプレビューからbitmapを取得して返されるコールバック
     */
    @Override
    public void onGetBitmap(Bitmap bmp) {
        // カメラが自分撮りカメラの場合は、反転することが必要
        if(mCurrentCamera == CameraInfo.CAMERA_FACING_FRONT){
            mOverlayFigureView.saveMergeBitmap(bmp, OverlayFigureView.OVERLAY_REVERSE);
        // 外向きカメラの場合は、そのまま合成保存
        }else {
            mOverlayFigureView.saveMergeBitmap(bmp, OverlayFigureView.OVERLAY_NORMAL);
        }
        Utils.showToast(this, R.string.complete_save_picture);
    }


    @Override
    public void onClick(View v) {
        // DO nothing
    }

}
