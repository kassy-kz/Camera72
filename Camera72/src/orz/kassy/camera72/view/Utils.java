package orz.kassy.camera72.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory.Options;
import android.media.MediaScannerConnection;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

public class Utils {
	
	private static final String TAG = null;
    private static NotificationManager sNM;
    public static final String PREF_FILE_NAME = "pref_file";
	
	/**
	 * IPアドレスを返す
	 * @param con
	 * @return
	 */
	public static int geIntAddress(Context con){
	    WifiManager wfm = (WifiManager)con.getSystemService(Context.WIFI_SERVICE);
	    int ip = wfm.getConnectionInfo().getIpAddress();
	    return ip;
	}

	/**
	 * IPアドレスを文字列に変換する
	 * @param ip
	 * @return
	 */
	public static String getStrAddress(int ip){
		String ipStr = ((ip>>0) &0xFF)+"."+
					   ((ip>>8) &0xFF)+"."+
					   ((ip>>16)&0xFF)+"."+
					   ((ip>>24)&0xFF);
		return ipStr;
	}
	
	/**
	 * IPアドレス（文字列状態）を取得する
	 * @param con
	 * @return
	 */
	public static String geStrAddress(Context con){
		WifiManager wfm = (WifiManager)con.getSystemService(Context.WIFI_SERVICE);
		int ip = wfm.getConnectionInfo().getIpAddress();
		String ipStr = ((ip>>0) &0xFF)+"."+
		   ((ip>>8) &0xFF)+"."+
		   ((ip>>16)&0xFF)+"."+
		   ((ip>>24)&0xFF);
		return ipStr;
	}
	

	public static void showToast(Context con, String message){
	    Toast.makeText(con, message, Toast.LENGTH_LONG).show();
	}
	
	/**
     * トーストを表示する
     * @param con　コンテキスト
     * @param resId メッセージのリソースID
     */
    public static void showToast(Context con, int resId){
        Toast.makeText(con, resId, Toast.LENGTH_LONG).show();
    }

    /**
     * シンプルアラートダイアログを出す
     * @param con
     * @param title
     * @param message
     * @param okListener
     * @param cancelListener
     */
    public static void showSimpleAlertDialog(Context con, 
                                                String title, 
                                                String message, 
                                                OnClickListener okListener, 
                                                OnClickListener cancelListener ){

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(con);
        // アラートダイアログのタイトル、メッセージを設定
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setMessage(message);
        
        // ボタン押しコールバックリスナーを登録します
        alertDialogBuilder.setPositiveButton("OK", okListener);
        alertDialogBuilder.setNegativeButton("キャンセル", cancelListener);

        // アラートダイアログのキャンセルが可能かどうかを設定
        alertDialogBuilder.setCancelable(true);

        // アラートダイアログを表示
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();     
    }

    /**
     * シンプルOKダイアログを出す
     * @param con
     * @param title
     * @param message
     * @param okListener
     */
	public static void showSimpleOkDialog(Context con, 
                        	        String title, 
                        	        String message, 
                        	        OnClickListener okListener){

	    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(con);
	    // アラートダイアログのタイトル、メッセージを設定
	    alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setMessage(message);

	    // ボタン押しコールバックリスナーを登録します
	    alertDialogBuilder.setPositiveButton("OK", okListener);

	    // アラートダイアログのキャンセルが可能かどうかを設定
	    alertDialogBuilder.setCancelable(true);

	    // アラートダイアログを表示
	    AlertDialog alertDialog = alertDialogBuilder.create();
	    alertDialog.show();     
	}
	
	
	/**
	 * SDカードにコピー
	 * @param context
	 * @param dbName
	 * @throws IOException
	 */
    public static void sdCopy(Context context, String dbName) throws IOException{
        //保存先(SDカード)のディレクトリを確保
        String pathSd = new StringBuilder()
                            .append(Environment.getExternalStorageDirectory().getPath())
                            .append("/")
                            .append(context.getPackageName())
                            .toString();
        Log.e(TAG,"pathsd = "+pathSd);
        File filePathToSaved = new File(pathSd);
        
        if (!filePathToSaved.exists() && !filePathToSaved.mkdirs()) {
            throw new IOException("FAILED_TO_CREATE_PATH_ON_SD");
        }

        final String fileDb = context.getDatabasePath(dbName).getPath();
        final String fileSd = new StringBuilder()
                                .append(pathSd)
                                .append("/")
                                .append(dbName)
                                .append(".")
                                .append((new SimpleDateFormat("yyyyMMddHHmmss")).format(new Date()))
                                .toString();

        Log.i(TAG, "copy from(DB): "+fileDb);
        Log.i(TAG, "copy to(SD)  : "+fileSd);

        FileChannel channelSource = new FileInputStream(fileDb).getChannel();
        FileChannel channelTarget = new FileOutputStream(fileSd).getChannel();

        channelSource.transferTo(0, channelSource.size(), channelTarget);

        channelSource.close();
        channelTarget.close();
    }

    /**
     * キーボードを表示する
     * @param act
     */
    public static void showKeyboard(Activity act) {
        act.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);        
    }

    /**
     * バイト列を任意の場所にファイル保存する
     * @param bin
     * @param dirPath ディレクトリのパス　最後のスラッシュはなし推奨
     * @param fileName ファイル名前
     * @throws IOException
     */
    public static void saveBinAsFile(byte[] bin, String dirPath, String fileName) throws IOException{
        FileOutputStream fos = null;
        File dir = new File(dirPath);
        dir.mkdirs(); // ディレクトリを作る　mkdirsは再帰的に作ることが可能
        fos = new FileOutputStream(new File(dirPath, fileName));
        fos.write(bin);
        fos.close();
    }

    /**
     * バイト列をファイル保存する
     * @param bin
     * @param fileFullPath ファイル、フルパスで
     * @throws IOException
     */
    public static void saveBinAsFile(byte[] bin, String fileFullPath) throws IOException{
        FileOutputStream fos = null;
        File file = new File(fileFullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　
        fos = new FileOutputStream(file);
        fos.write(bin);
        fos.close();
    }

    /**
     * バイト列をSDカード配下の任意の場所にファイル保存する
     * @param bin
     * @param dirPath ディレクトリのパス　/sdcard/以下のみ、最後の/は無し推奨、null可（直下保存）
     * @param fileName ファイル名前
     * @throws IOException
     */
    public static void saveBinAsFileAtSd(byte[] bin, String dirPath, String fileName) throws IOException{
        String fullPath = Environment.getExternalStorageDirectory().toString() + "/" + dirPath +"/"+ fileName;
        FileOutputStream fos = null;
        File file = new File(fullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　mkdirsは再帰的に作ることが可能
        fos = new FileOutputStream(file);
        fos.write(bin);
        fos.close();
    }

    /**
     * バイト列を内蔵メモリの任意の場所にファイル保存する
     * @param bin
     * @param dirPath ディレクトリのパス　/data/data/(package)以下のみ、最後の/は無し推奨、null可（直下保存）
     * @param fileName ファイル名前
     * @throws IOException
     */
    public static void saveBinAsFileAtInternal(Context con, byte[] bin, String dirPath, String fileName) throws IOException{
        String fullPath = "/data/data/" + con.getPackageName() + "/" + dirPath + "/" +fileName;
        FileOutputStream fos = null;
        File file = new File(fullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　mkdirsは再帰的に作ることが可能
        fos = new FileOutputStream(file);
        fos.write(bin);
        fos.close();
    }

    
    /**
     * ファイルを消す
     * @param delFilePath フルパスで 
     */
    public static void deleteFile(String delFilePath){
        File delFile = new File(delFilePath);
        delFile.delete();
    }
    
    /**
     * フォルダまとめて消す(フォルダの中身も含めて全て消す)
     * 扱い注意
     * @param folderPath フォルダのフルパス　ファイルでも可
     */
    public static void deleteFolder(String folderPath){
        File folder = new File(folderPath);
        deleteFolder(folder);
    }

    public static void deleteFolder(File folder){
        if(folder.isDirectory()){
            String[] children = folder.list();
            for (int i=0; i<children.length; i++) {  
                File child = new File(folder, children[i]);
                deleteFolder(child);
            }
        }
        folder.delete();
    }
    
    /**
     * ファイルをコピーする
     * @param srcFilePath
     * @param dstFilePath
     * @throws IOException
     */
    public static void copyFile(String srcFilePath, String dstFilePath) throws IOException {
        File srcFile = new File(srcFilePath);
        File dstFile = new File(dstFilePath);
    
        // ディレクトリを作る.(1こ上で)
        dstFile.getParentFile().mkdirs();
    
        // ファイルコピーのフェーズ
        InputStream input = null;
        OutputStream output = null;
        input = new FileInputStream(srcFile);
        output = new FileOutputStream(dstFile);
    
        int DEFAULT_BUFFER_SIZE = 1024 * 4;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            Log.i(TAG,"input " + n);
            output.write(buffer, 0, n);
        }
        input.close();
        output.close();
    }
    
    /**
     * ファイルを読み込む
     * @param srcFilePath ソースファイル　フルパスで
     * @param bin ここに入れる
     * @throws IOException
     */
    public static void readFile(String srcFilePath, byte bin[]) throws IOException {
        File srcFile = new File(srcFilePath);
        InputStream input = null;
        input = new FileInputStream(srcFile);
        input.read(bin);
        input.close();
    }


    /**
     * ビットマップを任意の名前で保存する
     * @param bitmap ビットマップ
     * @param dirPath ディレクトリのパス　/sdcard/以下のみ、最後の/は無し推奨、null可（直下保存）
     * @param fileName ファイル名
     * @throws IOException 
     */
    public static void saveBitmapAsJpgAtSd(Bitmap bitmap, String dirPath, String fileName) throws IOException {
        String fullPath = Environment.getExternalStorageDirectory().toString() + "/" + dirPath +"/"+ fileName;
        FileOutputStream fos = null;
        File file = new File(fullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　mkdirsは再帰的に作ることが可能
        fos = new FileOutputStream(file);
        bitmap.compress(CompressFormat.JPEG, 100, fos);
        fos.close();
    }

    /**
     * ビットマップを任意の名前で保存する、そしてすぐMediaScanしてギャラリーに登録する
     * @param con
     * @param bitmap
     * @param dirPath
     * @param fileName
     * @throws IOException
     */
    public static void saveBitmapAsJpgAtSdAndMediaScan(Context con, Bitmap bitmap, String dirPath, String fileName) throws IOException{
        String fullPath = Environment.getExternalStorageDirectory().toString() + "/" + dirPath +"/"+ fileName;
        FileOutputStream fos = null;
        File file = new File(fullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　mkdirsは再帰的に作ることが可能
        fos = new FileOutputStream(file);
        bitmap.compress(CompressFormat.JPEG, 100, fos);
        fos.close();
        String[] paths = {fullPath};
        String[] mimeTypes = {"image/jpeg"};
        MediaScannerConnection.scanFile(con,
                                        paths,
                                        mimeTypes,
                                        null);
    }
    
    /**
     * ビットマップを任意の名前で保存する
     * @param mBitmap ビットマップ
     * @param dirPath ディレクトリのパス　/sdcard/以下のみ、最後の/は無し推奨、null可（直下保存）
     * @param fileName ファイル名
     * @throws IOException 
     */
    public static void saveBitmapAsPngAtSd(Bitmap mBitmap, String dirPath, String fileName) throws IOException {
        String fullPath = Environment.getExternalStorageDirectory().toString() + "/" + dirPath +"/"+ fileName;
        FileOutputStream fos = null;
        File file = new File(fullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　mkdirsは再帰的に作ることが可能
        fos = new FileOutputStream(file);
        mBitmap.compress(CompressFormat.PNG, 100, fos);
        fos.close();
    }
    
    /**
     * ビットマップをファイル保存する
     * @param bin
     * @param fileFullPath ファイル、フルパスで
     * @throws IOException
     */
    public static void saveBitmapAsJpg(Bitmap mBitmap, String fileFullPath) throws IOException{
        FileOutputStream fos = null;
        File file = new File(fileFullPath);
        file.getParentFile().mkdirs(); // ディレクトリを作る　
        fos = new FileOutputStream(file);
        mBitmap.compress(CompressFormat.JPEG, 100, fos);
        fos.close();
    }
    
    /**
     * アプリ設定を保存する（整数版）
     * @param context
     * @param itemName 項目名
     * @param itemValue 項目値
     */
    public static void saveToSharedPref(Context context, String itemName, int itemValue) {
        SharedPreferences shPref = context.getSharedPreferences(PREF_FILE_NAME,Context.MODE_PRIVATE);
        Editor e = shPref.edit();
        e.putInt(itemName, itemValue);
        e.commit();
    }
    
    /**
     * アプリ設定をロードする　（整数値版）
     * @param context
     * @param itemName 項目名
     * @return
     */
    public static int loadFromSharedPref(Context context, String itemName) {
        SharedPreferences shPref = context.getSharedPreferences(PREF_FILE_NAME,Context.MODE_PRIVATE);
        int itemValue  = shPref.getInt(itemName, 0);
        return itemValue;
    }

    /**
     * ディスプレイサイズ取得用の関数
     * @param con
     * @return
     */
    public static Display getDisplayWidth(Context con){
        // ディスプレイサイズを取得します
        WindowManager wm = (WindowManager) con.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        return display;
    }
    
    /**
     * 小さいサイズでbitmapを読み込む
     * @param resId
     * @return
     */
    public static Bitmap decodeFitBitmap(Context con, int resId, int width, int height){
        
        BitmapFactory.Options options = new BitmapFactory.Options();
        //この値をtrueにすると実際には画像を読み込まず、
        //画像のサイズ情報だけを取得することができます。
        options.inJustDecodeBounds = true;

        //画像ファイル読み込み
        //ここでは上記のオプションがtrueのため実際の
        //画像は読み込まれないです。
        BitmapFactory.decodeResource(con.getResources(), resId, options);

        //読み込んだサイズはoptions.outWidthとoptions.outHeightに
        //格納されるので、その値から読み込む際の縮尺を計算します。
        int scaleW = options.outWidth / width + 1;
        int scaleH = options.outHeight / height + 1;
        Log.i(TAG,"bitmap options width ="+options.outWidth +", target width="+width);
        Log.i(TAG,"bitmap decode scaleW ="+scaleW);
        
        //縮尺は整数値で、2なら画像の縦横のピクセル数を1/2にしたサイズ。
        //3なら1/3にしたサイズで読み込まれます。
        int scale = Math.max(scaleW, scaleH);

        //今度は画像を読み込みたいのでfalseを指定
        options.inJustDecodeBounds = false;

        //先程計算した縮尺値を指定
        options.inSampleSize = scale;
        Log.i(TAG,"bitmap decode scale ="+scale);
        
        //これで指定した縮尺で画像を読み込めます。
        //もちろん容量も小さくなるので扱いやすいです。
        return BitmapFactory.decodeResource(con.getResources(), resId, options);
        //Bitmap image = BitmapFactory.decodeFile(path, options);
        //return image;
    }
    
    /**
     * 小さいサイズでビットマップ読み込む
     * @param path
     * @param options
     * @param width
     * @param height
     * @return
     */
    public static Bitmap decodeSmallBitmap(String path, Options options, int width, int height){
        
        //この値をtrueにすると実際には画像を読み込まず、
        //画像のサイズ情報だけを取得することができます。
        options.inJustDecodeBounds = true;

        //画像ファイル読み込み
        //ここでは上記のオプションがtrueのため実際の
        //画像は読み込まれないです。
        BitmapFactory.decodeFile(path, options);

        //読み込んだサイズはoptions.outWidthとoptions.outHeightに
        //格納されるので、その値から読み込む際の縮尺を計算します。
        int scaleW = options.outWidth / width + 1;
        int scaleH = options.outHeight / height + 1;

        //縮尺は整数値で、2なら画像の縦横のピクセル数を1/2にしたサイズ。
        //3なら1/3にしたサイズで読み込まれます。
        int scale = Math.max(scaleW, scaleH);

        //今度は画像を読み込みたいのでfalseを指定
        options.inJustDecodeBounds = false;

        //先程計算した縮尺値を指定
        options.inSampleSize = scale;

        //これで指定した縮尺で画像を読み込めます。
        //もちろん容量も小さくなるので扱いやすいです。
        return BitmapFactory.decodeFile(path, options);
    }
}
