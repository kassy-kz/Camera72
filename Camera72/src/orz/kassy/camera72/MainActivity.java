package orz.kassy.camera72;

import org.opencv.android.OpenCVLoader;
import com.actionbarsherlock.app.SherlockListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends SherlockListActivity {

    public static final String INTENT_EXTRA = "extra";
    public static final String INTENT_EXTRA_FROM_MAIN = "from_main";
    public static final String INTENT_EXTRA_FROM_RECORD = "from_record";

    private static final String TAG = "Main";
    private ArrayAdapter<String> mAdapter;
    private Object[] activities = {
            "Record Figure",      RecordActivity.class,
            "Record List",        RecordListActivity.class,
    };

    /** 
     * Called when the activity is first created. 
     * */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock); 
        super.onCreate(savedInstanceState);
        
        if (!OpenCVLoader.initDebug()) {
            // Report initialization error
            Log.i(TAG,"OpenCV INit Error!!!!!!!");
        }
        
        setContentView(R.layout.main);
        String[] list = new String[activities.length/2];
//        for (int i = 0; i < list.length; i++) {
//            list[i] = (String)activities[i * 2];
//        }
        list[0] = getString(R.string.record_figure);
        list[1] = getString(R.string.record_list);
        mAdapter = new ArrayAdapter<String>(this, R.layout.main_list_row, list);
        setListAdapter(mAdapter);
    }

    /**
     * リスト部品がクリックされた
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // 遷移先アクティビティを取得
        Intent intent = new Intent(MainActivity.this, (Class<?>)activities[position * 2 + 1]);
        intent.putExtra(INTENT_EXTRA, INTENT_EXTRA_FROM_MAIN);
        startActivity(intent);
    }
}