package orz.kassy.camera72;

import java.util.ArrayList;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import orz.kassy.camera72.view.Camera72SQLiteOpenHelper;
import orz.kassy.camera72.view.Camera72Utils;
import orz.kassy.camera72.view.RecordListArrayAdapter;
import orz.kassy.camera72.view.RecordListArrayItem;
import orz.kassy.camera72.view.Utils;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;


/**
 * リストから写真一覧フォルダを指定するアクティビティ
 * 戻り値
 *  resultCode  
 *    LIST_SELECT_DIRECTORY
 *  intent
 *    getExtras().getString(LIST_SELECT_DIRECTORY_NAME) : 名前
 */
public class RecordListActivity extends SherlockActivity implements OnItemClickListener, OnItemLongClickListener, OnClickListener {
    private static final String TAG = null;

    public static final int     LIST_SELECT_DIRECTORY = 1;
    public static final String  LIST_SELECT_DIRECTORY_NAME = "directoryName";
    public static final String  LIST_SELECT_WIDTH          = "width";
    public static final String  LIST_SELECT_HEIGHT         = "height";
    public static final String  LIST_SELECT_FG_COUNT       = "fgcnt";
    public static final String  LIST_SELECT_BG_COUNT       = "bgcnt";
    
    public static final String INTENT_EX_DIRECTORY = "intent_record_directory";
    public static final String INTENT_EX_FG_COUNT  = "intent_record_fg_count";
    public static final String INTENT_EX_BG_COUNT = "intent_record_bg_count";
    public static final String INTENT_EX_WIDTH    = "intent_record_width";
    public static final String INTENT_EX_HEIGHT   = "intent_record_height";

    
    private static final int OPTIONS_ITEM_ID_DELETE = 0;

    private static final int DIALOG_DELETE_ITEM = 0;
    private static final int DIALOG_LIST_ACTION = 1;


    private SQLiteDatabase mDB;
    private RecordListArrayItem mSelectingItem;
    private boolean mIsFromMain = false;    
    
    RecordListArrayAdapter mAdapter;
    ArrayList<RecordListArrayItem> mListItems;
    private AlertDialog cancelDialog;
    private Object listDialog;

    private ListView mListView;

    // 録画から抜き出し処理へのショートカットパスを作る
    private boolean mShortcutFlag = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock); 
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record_list);
        Intent intent = getIntent();
        String from = intent.getStringExtra(MainActivity.INTENT_EXTRA);

        // メイン画面から直接呼ばれたか？
        if(from.equals(MainActivity.INTENT_EXTRA_FROM_MAIN)){
            mIsFromMain = true;
        }else if(from.equals(MainActivity.INTENT_EXTRA_FROM_RECORD)){
            mShortcutFlag = true;
            mIsFromMain = false;            
        }else {
            mIsFromMain = false;            
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // データベースから全件取得
        Camera72SQLiteOpenHelper helper = new Camera72SQLiteOpenHelper(getApplicationContext());
        mDB = helper.getReadableDatabase();
        Cursor cursor = mDB.query(Camera72SQLiteOpenHelper.TABLE_NAME, null, null, null, null, null, null);

        // カーソルのライフサイクル管理をアクティビティに任せます。
        startManagingCursor(cursor);
        mListItems = new ArrayList<RecordListArrayItem>();
        cursor.moveToFirst();

        // 一行ずつ値を取得
        for(int i=0; i<cursor.getCount(); i++){
            String name = cursor.getString(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_NAME));
            String thumbPath = cursor.getString(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_THUMBNAIL));
            String directory =   cursor.getString(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_DIRECTORY));
            long date = cursor.getLong(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_DATE));
            int width = cursor.getInt(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_WIDTH));
            int height = cursor.getInt(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_HEIGHT));
            int fgCnt =   cursor.getInt(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_FG_COUNT));
            int bgCnt =   cursor.getInt(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_BG_COUNT));
            //int extractDone =   cursor.getInt(cursor.getColumnIndex(Camera72SQLiteOpenHelper.COLUMN_NAME_EXTRACT_DONE));

            RecordListArrayItem item
              = new RecordListArrayItem(this, directory, thumbPath, name, date, fgCnt, bgCnt, width, height);
            mListItems.add(item);
            cursor.moveToNext();
        }
        mAdapter = new RecordListArrayAdapter(this, R.layout.record_list_row, mListItems);

        mListView = (ListView) findViewById(R.id.list_view);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);
        
        // 録画から抜き出しへのショートカットパスを作る
        if(mShortcutFlag){
            mShortcutFlag = false;
            mSelectingItem = mListItems.get(mListItems.size()-1);
            startExtractActivity();
        }
        
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDB != null) mDB.close();
    }
    

    /**
     * リスト部品を選択した時の処理
     */
    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        mSelectingItem = mListItems.get(position);
        String dir = mSelectingItem.getTitle();
        Log.i(TAG,"onItemClick : " + dir);

        // メイン画面から呼ばれていたら
        if(mIsFromMain) {
            // ダイアログを出す
            showDialog(DIALOG_LIST_ACTION);
        }else{
            // 戻り値を添えてActivity終了
            Intent intent = new Intent();
            intent.putExtra(LIST_SELECT_DIRECTORY_NAME, dir);
            intent.putExtra(LIST_SELECT_WIDTH,    mSelectingItem.getWidth());
            intent.putExtra(LIST_SELECT_HEIGHT,   mSelectingItem.getHeight());
            intent.putExtra(LIST_SELECT_FG_COUNT, mSelectingItem.getFgCnt());
            intent.putExtra(LIST_SELECT_BG_COUNT, mSelectingItem.getBgCnt());
            setResult(LIST_SELECT_DIRECTORY, intent);
            finish();
        }
    }

    /**
     * リスト部品が長押しされたときの処理
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        mSelectingItem = mListItems.get(position);
        Log.i(TAG,"onLongItemClick : ");
        // 消去処理を行う。消去確認ダイアログを出す。
        showDialog(DIALOG_DELETE_ITEM);
        return false;
    }
    
    /**
     * OptionsMenuを生成
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, OPTIONS_ITEM_ID_DELETE, Menu.NONE, "delete")
            .setIcon(android.R.drawable.ic_menu_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }
    
    /**
     * OptionsMenu をクリックしたとき
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem menu){
        Log.i(TAG, "Selected menu: "+ menu.getItemId()+", " + menu.getTitle() );
        switch(menu.getItemId()){
            // ごみばこボタンをクリックした
            case OPTIONS_ITEM_ID_DELETE:
                Utils.showToast(this, "消去するにはリストを長押ししてください \n ごめんなさい このボタンはフェイクです");
                break;
            default:
                break;
        }
        return false;
    }

    /**
     * ダイアログ生成処理
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        // アイテム消去ダイアログ
        case DIALOG_DELETE_ITEM:
            cancelDialog = new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_menu_delete)
                .setTitle(R.string.dialog_title_delete_item)
                .setMessage(R.string.dialog_message_delete_item)
                .setPositiveButton(R.string.alert_dialog_delete, this)
                .setNegativeButton(R.string.alert_dialog_cancel, this)
                .create();
            return cancelDialog;
        case DIALOG_LIST_ACTION:
            listDialog = new AlertDialog.Builder(this)
            .setTitle(R.string.action_list_dialog)
            .setItems(R.array.select_action_dialog_items, this)
            .create();
            return (Dialog) listDialog;
        default:
            break;
        }
        return null;
    }
    

    /**
     * ダイアログのボタンが押された
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {

        // ダイアログが行動選択ダイアログだった
        if(dialog.equals(listDialog)){
            String[] items = getResources().getStringArray(R.array.select_action_dialog_items);
            Intent intent; 
            switch(which){
                // 抜き出し処理を行う
                case 0:
                    Log.i(TAG,"case 0 extract "+mSelectingItem.getDirectory());
                    startExtractActivity();
                    break;
                // 見る
                case 1:
                    Log.i(TAG,"case 1 BACKGROUND_TYPE_NONE ");
                    intent = new Intent(this, OverlayImageActivity.class);
                    intent.putExtra(OverlayImageActivity.BACKGROUND_TYPE, OverlayImageActivity.BACKGROUND_TYPE_NONE);
                    intent.putExtra(INTENT_EX_FG_COUNT, mSelectingItem.getFgCnt());
                    intent.putExtra(INTENT_EX_DIRECTORY, mSelectingItem.getDirectory());
                    intent.putExtra(INTENT_EX_BG_COUNT, mSelectingItem.getBgCnt());
                    intent.putExtra(INTENT_EX_WIDTH, mSelectingItem.getWidth());
                    intent.putExtra(INTENT_EX_HEIGHT, mSelectingItem.getHeight());
                    startActivity(intent);
                    break;
                // 写真に重ねる
                case 2:
                    Log.i(TAG,"case 1 BACKGROUND_TYPE_IMAGE ");
                    intent = new Intent(this, OverlayImageActivity.class);
                    intent.putExtra(OverlayImageActivity.BACKGROUND_TYPE, OverlayImageActivity.BACKGROUND_TYPE_IMAGE);
                    intent.putExtra(INTENT_EX_FG_COUNT, mSelectingItem.getFgCnt());
                    intent.putExtra(INTENT_EX_DIRECTORY, mSelectingItem.getDirectory());
                    intent.putExtra(INTENT_EX_BG_COUNT, mSelectingItem.getBgCnt());
                    intent.putExtra(INTENT_EX_WIDTH, mSelectingItem.getWidth());
                    intent.putExtra(INTENT_EX_HEIGHT, mSelectingItem.getHeight());
                    startActivity(intent);
                    break;
                // カメラに重ねる
                case 3: 
                    Log.i(TAG,"case 1 BACKGROUND_TYPE_CAMERA ");
                    intent = new Intent(this, OverlayImageActivity.class);
                    intent.putExtra(OverlayImageActivity.BACKGROUND_TYPE, OverlayImageActivity.BACKGROUND_TYPE_CAMERA);
                    intent.putExtra(INTENT_EX_FG_COUNT, mSelectingItem.getFgCnt());
                    intent.putExtra(INTENT_EX_DIRECTORY, mSelectingItem.getDirectory());
                    intent.putExtra(INTENT_EX_BG_COUNT, mSelectingItem.getBgCnt());
                    intent.putExtra(INTENT_EX_WIDTH, mSelectingItem.getWidth());
                    intent.putExtra(INTENT_EX_HEIGHT, mSelectingItem.getHeight());
                    startActivity(intent);
                    break;
            }
        }
        
        // ダイアログが消去ダイアログだった
        if(dialog.equals(cancelDialog)){
            switch(which){
                // 消去しても良い場合
                case DialogInterface.BUTTON_POSITIVE:
                    Log.i(TAG,"delete : "+mSelectingItem.getDirectory());

                    // フォルダを消去
                    Utils.deleteFolder(mSelectingItem.getDirectory());

                    // データベースから消去
                    Camera72Utils.deleteDatabaseRow(this, mSelectingItem.getDirectory());
                    Utils.showToast(this, R.string.delete_complete);
                    
                    // リスト更新 うまくいかない...
//                    mAdapter.notifyDataSetChanged();
//                    mListView.invalidateViews();
                    // 自分再度呼び出し
                    Intent intent = new Intent(this, RecordListActivity.class);
                    intent.putExtra(MainActivity.INTENT_EXTRA, MainActivity.INTENT_EXTRA_FROM_MAIN);
                    startActivity(intent);
                    finish();
                    break;
            }
        }
    }

    private void startExtractActivity() {
        Intent intent;
        intent = new Intent(this, ExtractPictureActivity.class);
        intent.putExtra(INTENT_EX_FG_COUNT, mSelectingItem.getFgCnt());
        intent.putExtra(INTENT_EX_DIRECTORY, mSelectingItem.getDirectory());
        intent.putExtra(INTENT_EX_BG_COUNT, mSelectingItem.getBgCnt());
        intent.putExtra(INTENT_EX_WIDTH, mSelectingItem.getWidth());
        intent.putExtra(INTENT_EX_HEIGHT, mSelectingItem.getHeight());
        startActivity(intent);
    }
}