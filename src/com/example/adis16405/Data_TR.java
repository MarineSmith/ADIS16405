package com.example.adis16405;

import java.io.UnsupportedEncodingException;
import java.util.List;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.widget.EditText;

public class Data_TR {
	
	private static final String TAG = "com.example.adis16405";
	private static final double process_noise = 1.5;
	private static final double measurement_noise = 1.5;
	private static double[] estimate_error = new double[12];
	private UsbManager mUsbManager;
	private UsbDevice mUsbDevice;
	private List<EditText> mEditText_List;
	private UsbInterface mUsbInterface;
	private UsbDeviceConnection mUsbDeviceConnection;
	private UsbEndpoint mUE00,mUE01,mUE02;
	byte[] setup_ = new byte[7];
	boolean SND = true;
	private StringBuilder mStringBuilder = new StringBuilder();
	private double[] kg = new double[12];
	private double[] x = new double[12];
	
	public Data_TR(UsbManager mUsbManager,UsbDevice mUsbDevice,List<EditText> mEditText_List){
		this.mUsbManager=mUsbManager;
		this.mUsbDevice=mUsbDevice;
		this.mEditText_List=mEditText_List;
	}
	
	public boolean initialize(){
		if(!mUsbManager.hasPermission(mUsbDevice))return false;
		mUsbInterface = mUsbDevice.getInterface(0); if(mUsbInterface==null)return false;
		mUE00 = mUsbInterface.getEndpoint(0); if((mUE00.getType()!=UsbConstants.USB_ENDPOINT_XFER_INT)||(mUE00.getDirection()!=UsbConstants.USB_DIR_IN))return false; //interrupt
		mUE01 = mUsbInterface.getEndpoint(1); if((mUE01.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)||(mUE01.getDirection()!=UsbConstants.USB_DIR_OUT))return false; //TX
		mUE02 = mUsbInterface.getEndpoint(2); if((mUE02.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)||(mUE02.getDirection()!=UsbConstants.USB_DIR_IN))return false; //RX
		mUsbDeviceConnection = mUsbManager.openDevice(mUsbDevice);if(mUsbDeviceConnection==null)return false;
		if(!mUsbDeviceConnection.claimInterface(mUsbInterface, true))return false;
		
		new Thread(new Runnable(){

			@Override
			public void run() {
				// TODO Auto-generated method stub
				try{
					byte[] buffer = new byte[1];
					vendor_read(0x8484,0,buffer,1);
					vendor_write(0x0404,0,null,0);
					vendor_read(0x8484,0,buffer,1);
					vendor_read(0x8383,0,buffer,1);
					vendor_read(0x8484,0,buffer,1);
					vendor_write(0x0404,1,null,0);
					vendor_read(0x8484,0,buffer,1);
					vendor_read(0x8383,0,buffer,1);
					vendor_write(0,1,null,0);
					vendor_write(1,0,null,0);
					vendor_write(2,0x44,null,0);
				}catch(Exception e){
					e.printStackTrace();
					SND = false;
				}
			}}).start();
		
		setup_[0] = (byte)(9600 & 0xff);
		setup_[1] = (byte)((9600 >> 8) & 0xff);
		setup_[2] = (byte)((9600 >> 16) & 0xff);
		setup_[3] = (byte)((9600 >> 24) & 0xff);
		setup_[4] = (byte)0;
		setup_[5] = (byte)0;
		setup_[6] = (byte)8;
		
		new Thread(new Runnable(){

			@Override
			public void run() {
				// TODO Auto-generated method stub
				try{
					int ret0 =mUsbDeviceConnection.controlTransfer(0xa1, 0x21, 0, 0, new byte[7], 7, 100);if(ret0<7)SND=false;
					int ret = mUsbDeviceConnection.controlTransfer(0x21, 0x20, 0, 0, setup_, 7, 100);if(ret<7)SND=false;
					int ret1 = mUsbDeviceConnection.controlTransfer(0x21, 0x23, 0x0000, 0, null, 0, 100);if(ret1<0)SND=false;
					vendor_write(0x0,0x0,null,0);
					int ret2 = mUsbDeviceConnection.controlTransfer(0x21, 0x22, (0-0x01), 0, null, 0, 100);if(ret2<0)SND=false;
					int ret3 = mUsbDeviceConnection.controlTransfer(0x21, 0x22, (0-0x02), 0, null, 0, 100);if(ret3<0)SND=false;
				}catch(Exception e){
					e.printStackTrace();
					SND = false;
				}
			}}).start();
			
		return SND;
	}
	
	private void vendor_write(int value,int index,byte[] buffer,int length)throws Exception {
		int ret = mUsbDeviceConnection.controlTransfer(0x40, 0x01, value, index, buffer, length, 100);
		if(ret<0)throw new Exception("Vendor write request failed! Value: 0x"+ String.format("%04X", value) + " Index: " + index + "Length: " + length +" Return: " + ret);
	}
	
	private void vendor_read(int value,int index,byte[] buffer,int length)throws Exception {
		int ret = mUsbDeviceConnection.controlTransfer(0xc0,0x01, value, index, buffer, length, 100);
		if(ret<0)throw new Exception("Vendor read request failed! Value: 0x"+ String.format("%04X", value) + " Index: " + index + "Length: " + length +" Return: " + ret);
	}
	
	public void receive_data(){
		new Thread(new Runnable(){
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				for(int i=0;i<12;i++){
					estimate_error[i] = Math.sqrt(process_noise*process_noise+measurement_noise*measurement_noise);
				}
				while(true){
				int max_size = mUE02.getMaxPacketSize();
				byte [] buffer = new byte[max_size];
				int ret = mUsbDeviceConnection.bulkTransfer(mUE02, buffer, max_size, 100);
				if(ret>0){
					//Log.e(TAG,""+new String(buffer,"US-ASCII").substring(0, 1));
					Message msg = new Message();
					msg.obj = buffer;
					mHandler.sendMessage(msg);
				}
				}
				}}).start();
	}
				
	private Handler mHandler = new Handler(){
		public void handleMessage(Message msg){
			super.handleMessage(msg);
			byte[] buffer = (byte[])msg.obj;
			try {
				String result_ = new String(buffer,"US-ASCII").substring(0,1);
				if(result_.equals("@")){
					mStringBuilder.append(result_);
				}else if(result_.equals("$")){
					mStringBuilder.append(result_);
					//Log.e(TAG,mStringBuilder.toString());
					splitMessage(mStringBuilder);
					mStringBuilder.setLength(0);
				}else{
					mStringBuilder.append(result_);
				}
				
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};
	
	private void splitMessage(StringBuilder mStringBuilder){
		int end = mStringBuilder.length();
		if(mStringBuilder.length()<0)return;
		if(!mStringBuilder.substring(1,2).equals("@"))return;
		if(!mStringBuilder.substring(end-1,end).equals("$"))return;
		
		mStringBuilder.delete(1, 2);
		mStringBuilder.delete(end-2,end);
		String result = mStringBuilder.toString();
		String[] result_ = result.split(":");
		if(result_.length!=13)return;
		int[] result_num = change2int(result_);
		if(!(result_num[0]>2000&&result_num[0]<2066))return;
		kalman_filter(result_num);
		fill_in();
	}
	
	private void fill_in(){
		mEditText_List.get(0).setText(""+(double)(x[0]*0.00242));
		mEditText_List.get(1).setText(""+(double)(x[1]*0.05));
		mEditText_List.get(2).setText(""+(double)(x[2]*0.05));
		mEditText_List.get(3).setText(""+(double)(x[3]*0.05));
		mEditText_List.get(4).setText(""+(double)(x[4]*0.01));
		mEditText_List.get(5).setText(""+(double)(x[5]*0.01));
		mEditText_List.get(6).setText(""+(double)(x[6]*0.01));
		mEditText_List.get(7).setText(""+(double)(x[7]*0.5));
		mEditText_List.get(8).setText(""+(double)(x[8]*0.5));
		mEditText_List.get(9).setText(""+(double)(x[9]*0.5));
		mEditText_List.get(10).setText(""+(double)(x[10]*0.14+25));
		mEditText_List.get(11).setText(""+(double)(x[11]*0.00081));
	}
	
	private void kalman_filter(int[] result){
		for(int i=0;i<12;i++){
			estimate_error[i] += process_noise;
			kg[i] = estimate_error[i]/(estimate_error[i]+measurement_noise);
			x[i] += kg[i]*(result[i]-x[i]);
			estimate_error[i] *= (1-kg[i]);
		}
	}
	
	private int[] change2int(String[] result){
		int temp = 0;
		boolean minus = false;
		int[] result_ = new int[13];
		for(int i=0;i<13;i++){
			int len = result[i].length();
			for(int j=0;j<len;j++){
				switch(result[i].charAt(j)){
				case '-':
					minus = true;
					break;
				case '0':
					temp = temp + (int) (0*Math.pow(10, len-(j+1)));
					break;
				case '1':
					temp = temp + (int) (1*Math.pow(10, len-(j+1)));
					break;
				case '2':
					temp = temp + (int) (2*Math.pow(10, len-(j+1)));
					break;
				case '3':
					temp = temp + (int) (3*Math.pow(10, len-(j+1)));
					break;
				case '4':
					temp = temp + (int) (4*Math.pow(10, len-(j+1)));
					break;
				case '5':
					temp = temp + (int) (5*Math.pow(10, len-(j+1)));
					break;
				case '6':
					temp = temp + (int) (6*Math.pow(10, len-(j+1)));
					break;
				case '7':
					temp = temp + (int) (7*Math.pow(10, len-(j+1)));
					break;
				case '8':
					temp = temp + (int) (8*Math.pow(10, len-(j+1)));
					break;
				case '9':
					temp = temp + (int) (9*Math.pow(10, len-(j+1)));
					break;
				}
			}
			if(minus == true){
				temp = temp*-1;
				result_[i] = temp;
				minus = false;
			}else{
				result_[i] = temp;
			}
			temp = 0;
		}
		return result_;
	}
}
