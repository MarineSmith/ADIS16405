package com.example.adis16405;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class Item {
	
	private String[] item_name = {"SUPPLY_OUT","XGYRO_OUT","YGYRO_OUT","ZGYRO_OUT","XACCL_OUT","YACCL_OUT","ZACCL_OUT","XMAGN_OUT","YMAGN_OUT","ZMAGN_OUT","TEMP_OUT","AUX_ADC","roll","pitch","yaw"};
	private String[] item_unit = {"V","degree/sec","degree/sec","degree/sec","g","g","g","TELSA","TELSA","TELSA","CELSIUS","V","degree","degree","degree"};
	public List<EditText> mEditText_List = new ArrayList<EditText>();
	private Context mContext;
	
	public Item(Context mContext){
		this.mContext = mContext;
	}
	
	public LinearLayout item_initial(){
		LinearLayout mLinearLayout = new LinearLayout(mContext);
		ViewGroup.LayoutParams mLayoutParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		mLinearLayout.setLayoutParams(mLayoutParams);
		mLinearLayout.setOrientation(LinearLayout.VERTICAL);
		
		mLinearLayout.addView(mTableLayout(18));
		
		return mLinearLayout;
		
	}
	
	private TableLayout mTableLayout(int count_){
		TableLayout mTableLayout = new TableLayout(mContext);
		mTableLayout.setStretchAllColumns(true);
		for(int i=0;i<15;i++){
			mTableLayout.addView(mTableRow(i));
		}
		return mTableLayout;
	}
	
	private TableRow mTableRow(int index){
		TableRow mTableRow = new TableRow(mContext);
		mTableRow.setPadding(0, 0, 0, 0);
		mTableRow.addView(mTextView(index));
		mTableRow.addView(mEditText(index));
		mTableRow.addView(m1TextView(index));
		return mTableRow;
	}
	
	private TextView mTextView(int index){
		TextView mTextView = new TextView(mContext);
		mTextView.setText(item_name[index]);
		return mTextView;
	}
	
	private EditText mEditText(int index){
		EditText mEditText = new EditText(mContext);
		mEditText.setId(index);
		mEditText_List.add(mEditText);
		return mEditText;
	}
	
	private TextView m1TextView(int index){
		TextView mTextView = new TextView(mContext);
		mTextView.setText(item_unit[index]);
		return mTextView;
	}
	
}
