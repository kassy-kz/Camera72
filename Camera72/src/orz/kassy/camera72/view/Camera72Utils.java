package orz.kassy.camera72.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import orz.kassy.tmpl.lib.Utils;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.util.Log;
import orz.kassy.camera72.*;
public class Camera72Utils {

    // public static final int SEQ_SHOT_MAX = 100;
    public static final String PREF_FILE_NAME = "pref_file";

    public static final String APP_DIR_ON_SD = "camera72";
    
    public static final String JPG_FILE_DIR = "front_jpg";
    public static final String JPG_FILE_HEADER = "front_jpg_";
    public static final String JPG_FILE_FOOTER = ".jpg";

    public static final String PNG_FILE_DIR = "png";
    public static final String PNG_FILE_HEADER = "ext_png_";
    public static final String PNG_FILE_FOOTER = ".png";

    public static final String BIN_FILE_DIR =  "bin";
    public static final String BIN_FILE_HEADER = "front_bin_";
    public static final String BIN_FILE_FOOTER = ".bin";

    private static final String BG_BIN_FILE_HEADER = "back_bin_";

    private static final String BG_JPG_FILE_DIR = "back_jpg";
    private static final String BG_JPG_FILE_HEADER = "back_jpg_";

    public static final String MASK_FILE_DIR    = "mask";
    public static final String MASK_FILE_HEADER = "72shot_mask";
    public static final String MASK_FILE_FOOTER = ".png";
    
    public static final boolean CONFIG_USE_SDCARD = true;
    private static final String CAMERA_SIZE_WIDTH = "camera_width";
    private static final String CAMERA_SIZE_HEIGHT = "camera_height";
    private static final String ASK_GET_FRONT = "ask_get_front";
    
    private static final String TAG = "Utils";
    private static final Uri IMAGE_URI = null;
    private static final String PREF_SHOT_CNT = "seq_shot_cnt";

    /**
     * デコードする関数
     * @param rgb
     * @param yuv420sp
     * @param width
     * @param height
     */
    public static final void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

    /**
     * エンコードする　bin -> jpg
     * @param con
     * @param width
     * @param height
     * @param inFilePath　binソースファイル フルパスで指定のこと
     * @param outFilePath jpg出力ファイル フルパスで指定のこと
     * @throws IOException 
     */
    public static void encodeBin2Jpg(Context con, int width, int height, String inFilePath, String outFilePath) throws IOException {
        int[] rgb = new int[(width * height)]; // ARGB8888の画素の配列分確保
        File file = new File(inFilePath);
        file.getParentFile().mkdir();
        FileInputStream fis;     

        fis = new FileInputStream(file);
        byte[] data =  new byte[fis.available()];;
        fis.read(data);
        fis.close();
        Bitmap bmp = null;
        Log.i(TAG,"encode w*h="+width+","+height);
        try {
            // ARGB8888で空のビットマップ作成
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            // 変換！
            Camera72Utils.decodeYUV420SP(rgb, data, width, height); 
            // 変換した画素からビットマップにセット
            bmp.setPixels(rgb, 0, width, 0, 0, width, height); 
        } catch(Exception e) {
            e.printStackTrace();
        }
        // jpegで保存
        Utils.saveBitmapAsJpg(bmp, outFilePath);
    }
        
    /**
     * エンコードする　bin -> bmp
     * @param con
     * @param width
     * @param height
     * @param data[]
     * @throws IOException 
     */
    public static Bitmap encodeBin2Bmp(Context con, int width, int height, byte[] data) throws IOException {
        int[] rgb = new int[(width * height)]; // ARGB8888の画素の配列分確保
        Bitmap bmp = null;
        Log.i(TAG,"encode w*h="+width+","+height);
        try {
            // ARGB8888で空のビットマップ作成
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            // 変換！
            Camera72Utils.decodeYUV420SP(rgb, data, width, height); 
            // 変換した画素からビットマップにセット
            bmp.setPixels(rgb, 0, width, 0, 0, width, height);
            // bmp
            return bmp;
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    
    /**
     * データをバイナリのまま保存する
     * @deprecated
     * @param data
     * @param width
     * @param height
     * @param filePath ファイル名　 フルパスでよろ
     * @throws IOException 
     */
    public static void saveCaptureAsBinary(byte[] data, final int width, final int height, String filePath) throws IOException {
//        int[] rgb;
//        Log.i(TAG,"preview frame w*h = "+width+", "+height);//640 480
//        rgb = new int[(width * height)]; // ARGB8888の画素の配列
        // dataを保存
        File file = new File(filePath);
        // 前のをいったん消すよ
        file.delete();
        // 親ディレクトリ作る
        file.getParentFile().mkdirs();
        FileOutputStream fos;     
        fos = new FileOutputStream(file, true);
        fos.write(data);
        fos.close();
    }

    
    
    public static Uri addImageAsApplication(ContentResolver cr, String name,  
            long dateTaken, String directory,  
            String filename, Bitmap source, byte[] jpegData) {  
      
        OutputStream outputStream = null;  
        String filePath = directory + "/" + filename;  
        try {  
            File dir = new File(directory);  
            if (!dir.exists()) {  
                dir.mkdirs();  
                Log.d(TAG, dir.toString() + " create");  
            }  
            File file = new File(directory, filename);  
            if (file.createNewFile()) {  
                outputStream = new FileOutputStream(file);  
                if (source != null) {  
                    source.compress(CompressFormat.JPEG, 75, outputStream);  
                } else {  
                    outputStream.write(jpegData);  
                }  
            }  
      
        } catch (FileNotFoundException ex) {  
            Log.w(TAG, ex);  
            return null;  
        } catch (IOException ex) {  
            Log.w(TAG, ex);  
            return null;  
        } finally {  
            if (outputStream != null) {  
                try {  
                    outputStream.close();  
                } catch (Throwable t) {  
                }  
            }  
        }  
          
        ContentValues values = new ContentValues(7);  
        values.put(Images.Media.TITLE, name);  
        values.put(Images.Media.DISPLAY_NAME, filename);  
        values.put(Images.Media.DATE_TAKEN, dateTaken);  
        values.put(Images.Media.MIME_TYPE, "image/jpeg");  
        values.put(Images.Media.DATA, filePath);  
        return cr.insert(IMAGE_URI, values);  
    }  
    
    /**
     * ビットマップを任意の名前で保存する
     * @param mBitmap ビットマップ
     * @param dirPath ディレクトリのパス　/sdcard/以下のみ、最後の/は無し推奨、null可（直下保存）
     * @param fileName ファイル名
     * @throws IOException 
     */
    public static void saveBitmapAsJpgAtSd(Bitmap mBitmap, String dirPath, String fileName) throws IOException {
        String fullPath = Environment.getExternalStorageDirectory().toString() + "/" + dirPath +"/"+ fileName;
        FileOutputStream fos = null;
        File file = new File(fullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　mkdirsは再帰的に作ることが可能
        fos = new FileOutputStream(file);
        mBitmap.compress(CompressFormat.JPEG, 100, fos);
        fos.close();
    }
    
    
    /**
     * intを３桁文字列に整形して返す
     * example: 12 -> 012,   4 -> 004,  102 -> 102
     * @param intCnt
     * @return
     */
    public static String getCountStr3(int intCnt) {
        String strCnt;
        if(intCnt <10){
            strCnt = "00" + intCnt; 
        }else if(intCnt < 100){
            strCnt = "0" + intCnt;                 
        }else {
            strCnt = intCnt+"";
        }

        return strCnt;
    }
    

    /**
     * 前景抽出するか否かを尋ねるダイアログを出す
     * @param con
     */
    public static void showAskGetFrontDialog(Context con){

        AlertDialog.Builder builder = new AlertDialog.Builder(con);
        // アラートダイアログのタイトル、メッセージを設定
        builder.setTitle(R.string.ask_get_front_dialog_title);
        builder.setMessage(R.string.ask_get_front_dialog_message);
        builder.setIcon(R.drawable.ic_launcher);
        // アラートダイアログのキャンセルが可能かどうかを設定
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                
            }
        });
        builder.setNegativeButton(R.string.ask_get_front_dialog_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                
            }
        });
        // アラートダイアログを表示
        AlertDialog alertDialog = builder.create();
        alertDialog.show();     
    }

    
    /**
     * 前景抽出するか否かの判断を保存する
     * @param context
     * @param bool 前景抽出するか否か
     */
    public static void saveAskGetFront(Context context, Boolean bool) {
        SharedPreferences shPref = context.getSharedPreferences(PREF_FILE_NAME,Context.MODE_PRIVATE);
        Editor editor = shPref.edit();
        editor.putBoolean(ASK_GET_FRONT, bool);
        editor.commit();
    }
    public static boolean loadAskGetFront(Context context) {
        SharedPreferences shPref = context.getSharedPreferences(PREF_FILE_NAME,Context.MODE_PRIVATE);
        boolean b = shPref.getBoolean(ASK_GET_FRONT, true);
        return b;
    }
    
//    /**
//     * 撮影枚数をロードする
//     */
//    public static int loadSeqShotNum(Context con){
//        int ret = Utils.loadFromSharedPref(con, PREF_SHOT_CNT);
//        if(ret>0){
//            return ret;
//        }else {
//            return 1;
//        }
//    }
//
//    /**
//     * 撮影枚数を保存する
//     */
//    public static void saveSeqShotNum(Context con, int cnt){
//        if(cnt>0){
//            Utils.saveToSharedPref(con, PREF_SHOT_CNT, cnt);
//        }else {
//            Utils.saveToSharedPref(con, PREF_SHOT_CNT, 1);            
//        }
//    }
//    
    /**
     * 候補から最適なプレビューサイズを取得する
     * @param sizes　サイズ候補リスト
     * @param w
     * @param h
     * @return
     */
    public static Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        Log.i("MyUtils", "optimal param w*h = "+w+","+h + ", " +
                         "-> optimal w*h "+optimalSize.width+","+optimalSize.height);
        return optimalSize;
    }
    
    
    
    /**
     * 1, バイナリ保存するファイルのフルパスを取得する
     * @param con
     * @param cnt  
     * @return
     */
    public static String getFgBinFileFullPath(String seqDir, Integer cnt) {
        String binFileName;
        if(cnt != null) {
            String strCnt = getCountStr3(cnt);
            binFileName = BIN_FILE_HEADER + strCnt + BIN_FILE_FOOTER;
        } else {
            binFileName = BIN_FILE_HEADER + BIN_FILE_FOOTER;            
        }
        String binFileFullPath;
        binFileFullPath = Environment.getExternalStorageDirectory() 
                        + "/" + APP_DIR_ON_SD 
                        + "/" + seqDir
                        + "/" + BIN_FILE_DIR 
                        + "/" + binFileName;
        return binFileFullPath;
    }
    

    /**
     * 2, 背景画像をバイナリ保存するファイルのフルパスを取得する
     * @param con
     * @param cnt  
     * @return
     */
    public static String getBgBinFileFullPath(String seqDir, Integer cnt) {
        String binFileName;
        String strCnt = getCountStr3(cnt);
        binFileName = BG_BIN_FILE_HEADER + strCnt + BIN_FILE_FOOTER;
        String binFileFullPath;
        binFileFullPath = Environment.getExternalStorageDirectory()
                        + "/" + APP_DIR_ON_SD 
                        + "/" + seqDir
                        + "/" + BIN_FILE_DIR 
                        + "/" + binFileName;
        return binFileFullPath;
    }

    
    /**
     * 3, jpg保存するファイルのフルパスを取得する
     * @param cnt
     * @return
     */
    public static String getFgJpgFileFullPath(String seqDir, Integer cnt) {
        String jpgFileName;
        if(cnt != null) {
            String strCnt = getCountStr3(cnt);            
            jpgFileName = JPG_FILE_HEADER + strCnt + JPG_FILE_FOOTER;        
        } else {
            jpgFileName = JPG_FILE_HEADER + JPG_FILE_FOOTER;        
        }
        String jpgFileFullPath;
        jpgFileFullPath = Environment.getExternalStorageDirectory()
                        + "/" + APP_DIR_ON_SD 
                        + "/" + seqDir
                        +"/" + JPG_FILE_DIR
                        +"/" + jpgFileName;
        return jpgFileFullPath;
    }

    /**
     * 4, 背景をjpg保存するファイルのフルパスを取得する
     * @param cnt
     * @return
     */
    public static String getBgJpgFileFullPath(String seqDir, Integer cnt) {
        String jpgFileName;
        if(cnt != null) {
            String strCnt = getCountStr3(cnt);            
            jpgFileName = BG_JPG_FILE_HEADER + strCnt + JPG_FILE_FOOTER;        
        } else {
            jpgFileName = BG_JPG_FILE_HEADER + JPG_FILE_FOOTER;        
        }
        String jpgFileFullPath;
        jpgFileFullPath = Environment.getExternalStorageDirectory()
                        + "/" + APP_DIR_ON_SD 
                        + "/" + seqDir
                        + "/" + BG_JPG_FILE_DIR 
                        + "/" + jpgFileName;
        return jpgFileFullPath;
    }

    /**
     * 5, 差分抽出したpngファイルを格納するパスを取得する
     * @param cnt
     * @return
     */
    public static String getTargetPngFileFullPath(String seqDir, Integer cnt) {
        String pngFileName;
        if(cnt != null) {
            String strCnt = getCountStr3(cnt);            
            pngFileName = PNG_FILE_HEADER + strCnt + PNG_FILE_FOOTER;        
        } else {
            pngFileName = PNG_FILE_HEADER + PNG_FILE_FOOTER;        
        }
        String pngFileFullPath = Environment.getExternalStorageDirectory()
                                + "/" + APP_DIR_ON_SD 
                                + "/" + seqDir
                                + "/" + PNG_FILE_DIR 
                                + "/" + pngFileName;
        return pngFileFullPath;
    }

    /**
     * 6, マスクファイルを格納するパスを取得する
     * @param cnt
     * @return
     */
    public static String getMaskFileFullPath(String seqDir, Integer cnt) {
        String pngFileName;
        if(cnt != null) {
            String strCnt = getCountStr3(cnt);            
            pngFileName = MASK_FILE_HEADER + strCnt + MASK_FILE_FOOTER;        
        } else {
            pngFileName = MASK_FILE_HEADER + MASK_FILE_FOOTER;        
        }
        String pngFileFullPath = Environment.getExternalStorageDirectory() 
                                + "/" + APP_DIR_ON_SD 
                                + "/" + seqDir
                                + "/" + MASK_FILE_DIR 
                                + "/" + pngFileName;
        return pngFileFullPath;
    }

    
    /**
     * データベースのサムネイルパスを更新する
     * @param con
     * @param seqDir
     * @param path
     */
    public static void updateDatabaseThumbPath(Context con, String seqDir, String path) {
        Camera72SQLiteOpenHelper helper = new Camera72SQLiteOpenHelper(con);
        SQLiteDatabase db = helper.getReadableDatabase();
        
        // 更新する値 fullpathを更新
        ContentValues values = new ContentValues();
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_THUMBNAIL, path);
        
        // 更新する対象　seqDirが該当の行
        String where = Camera72SQLiteOpenHelper.COLUMN_NAME_DIRECTORY + " = ?";
        String[] args = {seqDir};

        // 更新する
        db.update(Camera72SQLiteOpenHelper.TABLE_NAME, 
                  values, 
                  where, 
                  args);
        // 閉じる
        db.close();
    }

    /**
     * データベースのwidth, heightを更新する
     * @param con
     * @param seqDir
     * @param width
     * @param height
     */
    public static void updateDatabasePictureSize(Context con, String seqDir, int width, int height) {
        Camera72SQLiteOpenHelper helper = new Camera72SQLiteOpenHelper(con);
        SQLiteDatabase db = helper.getReadableDatabase();
        Log.i(TAG,"updateDatabaseSize seqDir="+seqDir+", width="+width+", height="+height);
        // 更新する値 widthを更新
        ContentValues values = new ContentValues();
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_WIDTH,  width);
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_HEIGHT, height);
        
        // 更新する対象　seqDirが該当の行
        String where = Camera72SQLiteOpenHelper.COLUMN_NAME_DIRECTORY + " = ?";
        String[] args = {seqDir};

        // 更新する
        db.update(Camera72SQLiteOpenHelper.TABLE_NAME, 
                  values, 
                  where, 
                  args);
        // 閉じる
        db.close();
    }
    
    /**
     * データベースのwidth, heightを更新する
     * @param con
     * @param seqDir
     * @param width
     * @param height
     */
    public static void updateDatabaseBgCnt(Context con, String seqDir, int bgCnt) {
        Camera72SQLiteOpenHelper helper = new Camera72SQLiteOpenHelper(con);
        SQLiteDatabase db = helper.getReadableDatabase();
        
        // 更新する値 widthを更新
        ContentValues values = new ContentValues();
        values.put(Camera72SQLiteOpenHelper.COLUMN_NAME_BG_COUNT, bgCnt);
        
        // 更新する対象　seqDirが該当の行
        String where = Camera72SQLiteOpenHelper.COLUMN_NAME_DIRECTORY + " = ?";
        String[] args = {seqDir};

        // 更新する
        db.update(Camera72SQLiteOpenHelper.TABLE_NAME, 
                  values, 
                  where, 
                  args);
        // 閉じる
        db.close();
    }
    
    
    /**
     * データベースを一行消去する
     * @param con
     * @param directory
     */
    public static void deleteDatabaseRow(Context con, String directory) {
        // DBひらく
        Camera72SQLiteOpenHelper helper = new Camera72SQLiteOpenHelper(con);
        SQLiteDatabase db = helper.getReadableDatabase();
        
        // 更新する対象　seqDirが該当の行
        String where = Camera72SQLiteOpenHelper.COLUMN_NAME_DIRECTORY + " = "+directory;

        // 更新する
        db.delete(Camera72SQLiteOpenHelper.TABLE_NAME, where, null);

        // 閉じる
        db.close();
    }

}
