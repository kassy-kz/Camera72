package orz.kassy.camera72.view;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import orz.kassy.camera72.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class RecordListArrayAdapter extends BaseAdapter{

	private LayoutInflater inflater;
	private int mViewResourceId;
	private List<RecordListArrayItem> mItems;

	/**
	 * コンストラクタ
	 * @param context
	 * @param viewResourceId
	 * @param items
	 */
	public RecordListArrayAdapter(Context context,int viewResourceId , ArrayList<RecordListArrayItem> items){
		super();
		mViewResourceId = viewResourceId;
		mItems = items;
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	/**
	 * Viewを返す
	 */
	@Override
	public View getView(int position,View convertView, ViewGroup parent){
		View view;
		if(convertView !=null){
			view = convertView;
		}else{
			view = inflater.inflate(mViewResourceId,null);
		}

		RecordListArrayItem item = mItems.get(position);
		
		ImageView imageView = (ImageView)view.findViewWithTag("icon");
		imageView.setImageBitmap(item.getIconBitmap());
		
		TextView textView = (TextView)view.findViewById(R.id.db_row_name);
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(item.getDateMillis());
		String strDate = cal.get(Calendar.YEAR)+"年"+(cal.get(Calendar.MONTH)+1)+"月"+cal.get(Calendar.DAY_OF_MONTH)+"日  "+cal.get(Calendar.HOUR_OF_DAY)+":"+cal.get(Calendar.MINUTE);
		textView.setText(strDate);

        TextView textWidth = (TextView)view.findViewById(R.id.db_row_width);
        textWidth.setText(item.getWidth()+"");
        TextView textHeight = (TextView)view.findViewById(R.id.db_row_height);
        textHeight.setText(item.getHeight()+"");
		
		TextView textFgCnt = (TextView)view.findViewById(R.id.db_row_fgcnt);
        textFgCnt.setText(item.getFgCnt()+"枚");
        TextView textBgCnt = (TextView)view.findViewById(R.id.db_row_bgcnt);
        textBgCnt.setText(item.getBgCnt()+"枚");

		return view;
	}

	@Override
	public int getCount() {
		return mItems.size();
	}

	@Override
	public Object getItem(int position) {
		return mItems.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
}
