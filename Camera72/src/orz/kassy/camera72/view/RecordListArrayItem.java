package orz.kassy.camera72.view;

import orz.kassy.camera72.R;
import orz.kassy.camera72.view.Utils;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class RecordListArrayItem {
	
	private String mTitle;
	private String mThumbPath;
	private Context mContext;
    private String mDirectory;
	private int mFgCnt;
    private int mBgCnt;
    private int mWidth;
    private long mTimeMillis;
    private int mHeight;
    
	public RecordListArrayItem(Context con, String directory, String thumbPath, String title, long date, int fgCnt, int bgCnt, int width, int height){
		mThumbPath = thumbPath;
		mTitle = title;
		mContext = con;
		mFgCnt = fgCnt;
		mBgCnt = bgCnt;
		mWidth = width;
		mHeight= height;
		mTimeMillis = date;
		mDirectory = directory;
	}
	
	public Bitmap getIconBitmap(){
	    try{
	        BitmapFactory.Options opt = new BitmapFactory.Options();
	        Bitmap bmp = Utils.decodeSmallBitmap(mThumbPath, opt, 50, 50);
	        return bmp;
	    }catch(Exception e){
            return BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_launcher);
	    }
	}
	
	public String getTitle(){
		return mTitle;
	}

	public String getDirectory(){
	    return mDirectory;
	}

    public long getDateMillis(){
        return mTimeMillis;
    }

    public void setTitle(String title){
		this.mTitle = title;
	}

	public int getFgCnt(){
        return mFgCnt;
    }

    public int getBgCnt() {
        return mBgCnt;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getWidth() {
        return mWidth;
    }
}
