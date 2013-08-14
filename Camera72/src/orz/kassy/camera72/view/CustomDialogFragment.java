package orz.kassy.camera72.view;

import orz.kassy.camera72.R;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class CustomDialogFragment extends DialogFragment {
    
    private static int mMode = 0; 
    
    
    @Override  
    public void onActivityCreated(Bundle savedInstanceState) {  
        super.onActivityCreated(savedInstanceState);  
          
        Dialog dialog = getDialog();  
          
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();  
          
        DisplayMetrics metrics = getResources().getDisplayMetrics();  
        int dialogWidth = (int) (metrics.widthPixels * 0.9);  
        int dialogHeight = (int) (metrics.heightPixels * 0.9);  
          
        lp.width = dialogWidth;  
        lp.height = dialogHeight;  
        dialog.getWindow().setAttributes(lp);  
    }  
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        
        final Dialog dialog = new Dialog(getActivity());
        // タイトル非表示
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        // フルスクリーン
        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        dialog.setContentView(R.layout.dialog_custom);
        // 背景を透明にする
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        // OK ボタンのリスナ
        dialog.findViewById(R.id.positive_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mMode) {
                    case 0:
                        mMode =1;
                        TextView tv = (TextView) dialog.findViewById(R.id.custom_dialog_message);
                        tv.setText(getActivity().getString(R.string.inst_string2));
                        Button btn = (Button)dialog.findViewById(R.id.positive_button);
                        btn.setText("Close");
                        break;
                    case 1:
                        dismiss();
                        break;
                    default:
                        dismiss();
                        break;
                }
            }
        });
        // Close ボタンのリスナ
        dialog.findViewById(R.id.close_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        
        return dialog;
    }

}