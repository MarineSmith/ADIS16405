package com.example.adis16405;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {
	
	private static final String ACTION_USB_PERMISSION = "com.example.adis16405.USB_PERMISSION";
	private List<EditText> mEditText_List = new ArrayList<EditText>();
	private UsbManager mUsbManager;
	private Data_TR mData_TR;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Item mItem = new Item(MainActivity.this);
		setContentView(mItem.item_initial());
		mEditText_List = mItem.mEditText_List;
		initial();
	}
	
	private void initial(){
		for(EditText temp:mEditText_List){
			temp.setEnabled(false);
		}
	}
	@Override
	protected void onResume(){
		super.onResume();
	}
	
	private void USB_object(){
		mUsbManager = (UsbManager)this.getSystemService(Context.USB_SERVICE);
		this.registerReceiver(mBroadcastReceiver,new IntentFilter(ACTION_USB_PERMISSION));
		getDeviceList();
	}
	
	private void getDeviceList(){
		PendingIntent mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		HashMap<String,UsbDevice> mHashMap = mUsbManager.getDeviceList();
		Iterator<UsbDevice> mIterator = mHashMap.values().iterator();
		while(mIterator.hasNext()){
			UsbDevice mUsbDevice = mIterator.next();
			mUsbManager.requestPermission(mUsbDevice, mPendingIntent);
		}
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		//this.unregisterReceiver(mBroadcastReceiver);
	}
	
	@Override
	protected void onStop(){
		super.onStop();
		this.unregisterReceiver(mBroadcastReceiver);
		//mData_TR.rec_thread_latch = false;
		//mData_TR.rec_thread.interrupt();
	}
	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			String action = intent.getAction();
			if(ACTION_USB_PERMISSION.equals(action)){
				synchronized(this){
					UsbDevice mUsbDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)&&(mUsbDevice!=null)){
						/***/
						mData_TR = new Data_TR(mUsbManager,mUsbDevice,mEditText_List);
						if(!mData_TR.initialize()){
							Toast.makeText(MainActivity.this, "Sorry! USB device may not support your mobile", Toast.LENGTH_LONG).show();
						}else{
							mData_TR.receive_data();
						}
						
					}
				}
			}
		}
		
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		if(id==R.id.usb1){
			USB_object();
		}
		return super.onOptionsItemSelected(item);
	}
}
