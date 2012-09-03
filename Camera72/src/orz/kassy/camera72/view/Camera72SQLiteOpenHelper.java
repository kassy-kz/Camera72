package orz.kassy.camera72.view;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Camera72SQLiteOpenHelper extends SQLiteOpenHelper {

    // カラムの名前
    public static final String COLUMN_NAME_NAME      = "name";
    public static final String COLUMN_NAME_DATE      = "date";
    public static final String COLUMN_NAME_DIRECTORY = "directory";
    public static final String COLUMN_NAME_THUMBNAIL = "thumbnail";
    public static final String COLUMN_NAME_WIDTH     = "width";
    public static final String COLUMN_NAME_HEIGHT    = "height";
    public static final String COLUMN_NAME_FG_COUNT    = "fg_count";
    public static final String COLUMN_NAME_BG_COUNT    = "bg_count";
    public static final String COLUMN_NAME_EXTRACT_DONE = "extract_done";
    
    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "camera72.db";
    public static final String TABLE_NAME = "camera72_table";

    public Camera72SQLiteOpenHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // DBが新規作成された時に呼び出される。
    @Override
    public void onCreate(SQLiteDatabase db) {
        // テーブル作成
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME
                + " (_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "  " + COLUMN_NAME_NAME         + " TEXT,"
                + "  " + COLUMN_NAME_DATE         + " INTEGER,"
                + "  " + COLUMN_NAME_DIRECTORY    +" TEXT,"
                + "  " + COLUMN_NAME_THUMBNAIL    +" TEXT,"
                + "  " + COLUMN_NAME_WIDTH        +" INTEGER,"
                + "  " + COLUMN_NAME_HEIGHT       +" INTEGER,"
//                + "  " + COLUMN_NAME_EXTRACT_DONE +" INTEGER default 0"
                + "  " + COLUMN_NAME_FG_COUNT     +" INTEGER,"
                + "  " + COLUMN_NAME_BG_COUNT     +" INTEGER);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,
                          int oldVersion,
                          int newVersion) {
    }
}
