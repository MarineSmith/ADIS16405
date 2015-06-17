package com.example.adis16405;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.EditText;

public class Data_TR {
	
	private static final String TAG = "com.example.adis16405";
	private static final float pi = (float) Math.PI;
	private static final float process_noise = (float) 1.5;
	private static final float measurement_noise = (float) 1.5;
	private static double[] estimate_error = new double[3];
	private UsbManager mUsbManager;
	private UsbDevice mUsbDevice;
	private List<EditText> mEditText_List;
	private UsbInterface mUsbInterface;
	private UsbDeviceConnection mUsbDeviceConnection;
	private UsbEndpoint mUE00,mUE01,mUE02;
	byte[] setup_ = new byte[7];
	boolean SND = true;
	private StringBuilder mStringBuilder = new StringBuilder();
	private float[] kg = new float[3];
	private float[] kal_euler = new float[3]; 
	private static final float REG2DEG = (float) 57.2957795;
	private float[] spl_result = new float[12];
	private float[] euler_gyro_1;
	public boolean reboot_latch = true;
	private Thread t1,t2;
	private Long starttime;
	private int newtime;
	float[] pre_gyro = new float[3];
	public Thread rec_thread;
	public boolean rec_thread_latch = true;

	
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
		
		t1 = new Thread(new Runnable(){

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
			}});
		t1.start();
		
		
		setup_[0] = (byte)(9600 & 0xff);
		setup_[1] = (byte)((9600 >> 8) & 0xff);
		setup_[2] = (byte)((9600 >> 16) & 0xff);
		setup_[3] = (byte)((9600 >> 24) & 0xff);
		setup_[4] = (byte)0;
		setup_[5] = (byte)0;
		setup_[6] = (byte)8;
		t2 = new Thread(new Runnable(){

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
			}});
		t2.start();
		
		t1.interrupt();
		t2.interrupt();
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
		starttime = System.currentTimeMillis();
		rec_thread = new Thread(new Runnable(){
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				for(int i=0;i<2;i++){
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
				}});
		rec_thread.start();
	}
				
	public Handler mHandler = new Handler(){
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
		if(!(result_num[0]>1980&&result_num[0]<2066))return;
		
		for(int i=0;i<result_num.length-1;i++){
			spl_result[i] = result_num[i];
		}
		fill_in();
		/*int end = mStringBuilder.length();
		if(!(mStringBuilder.length()<0)){
			if(mStringBuilder.substring(1,2).equals("@")){
				if(mStringBuilder.substring(end-1,end).equals("$")){
					mStringBuilder.delete(1, 2);
					mStringBuilder.delete(end-2,end);
					String result = mStringBuilder.toString();
					String[] result_ = result.split(":");
					if(result_.length==13){
						int[] result_num = change2int(result_);
						if((result_num[0]>1980&&result_num[0]<2066)){
							for(int i=0;i<result_num.length-1;i++){
								spl_result[i] = result_num[i];
							}
						}
					}
				}
			}
		}
		fill_in();*/
	}
	
	
	
	
	private void fill_in(){
		float psi = 0f;
		mEditText_List.get(0).setText(""+(float)(spl_result[0]*0.00242));
		mEditText_List.get(1).setText(""+(float)(spl_result[1]*0.05));
		mEditText_List.get(2).setText(""+(float)(spl_result[2]*0.05));
		mEditText_List.get(3).setText(""+(float)(spl_result[3]*0.05));
		mEditText_List.get(4).setText(""+(float)(spl_result[4]*0.01));
		mEditText_List.get(5).setText(""+(float)(spl_result[5]*0.01));
		mEditText_List.get(6).setText(""+(float)(spl_result[6]*0.01));
		mEditText_List.get(7).setText(""+(float)(spl_result[7]*0000.5/10000));
		mEditText_List.get(8).setText(""+(float)(spl_result[8]*0000.5/10000));
		mEditText_List.get(9).setText(""+(float)(spl_result[9]*0000.5/10000));
		//psi = calculateH((float)(spl_result[7]*0.005/10000),(float)(spl_result[8]*0.005/10000),(float)(spl_result[9]*0000.5/10000),(float)(spl_result[4]*0.01),(float)(spl_result[5]*0.01),(float)(spl_result[6]*0.01));
		if(reboot_latch){
			psi = calculateH((float)(spl_result[7]*0.005),(float)(spl_result[8]*0.005),(float)(spl_result[9]*0.005),(float)(spl_result[4]*0.01),(float)(spl_result[5]*0.01),(float)(spl_result[6]*0.01));
			initializeR(psi);
			reboot_latch = false;
		}else{
			//psi = calculateH((float)(spl_result[7]*0.005/10000),(float)(spl_result[8]*0.005/10000),(float)(spl_result[9]*0000.5/10000),euler_gyro_1[0],euler_gyro_1[1],euler_gyro_1[2],true);
			psi = calculateH((float)(spl_result[7]*0.005),(float)(spl_result[8]*0.005),(float)(spl_result[9]*0.005),(float)(spl_result[4]*0.01),(float)(spl_result[5]*0.01),(float)(spl_result[6]*0.01));
		}
		euler_gyro_1 = calculateDgyro((float)(spl_result[1]*0.05/REG2DEG),(float)(spl_result[2]*0.05/REG2DEG),(float)(spl_result[3]*0.05/REG2DEG),psi,(float)(spl_result[4]*0.01),(float)(spl_result[5]*0.01),(float)(spl_result[6]*0.01));
		//caculateAngle((float)(x[4]*0.098),(float)(x[5]*0.098),(float)(x[6]*0.098),(float)(x[7]*0000.5),(float)(x[8]*0000.5),(float)(x[9]*0000.5));
		//calculateAngle((float)(x[4]*0.01*9.8),(float)(x[5]*0.01*9.8),(float)(x[6]*0.01*9.8),(float)(x[1]*0.05/REG2DEG),(float)(x[2]*0.05/REG2DEG),(float)(x[3]*0.05/REG2DEG),(float)(x[7]*0.005/10000),(float)(x[8]*0.005/10000),(float)(x[9]*0.005/10000));
		
		mEditText_List.get(10).setText(""+(float)(spl_result[10]*0.14+25));
		mEditText_List.get(11).setText(""+(float)(spl_result[11]*0.00081));
	}
	
	/**
	 * @param phi roll x
	 * @param theta pitch y
	 * @param psi yaw z**/
	
	private float calculateH(float x,float y,float z,float ax,float ay,float az){
		float psi = 0f;
		float norm = (float)Math.sqrt(ax*ax+ay*ay+az*az);
		float sRoll = -ay/norm;
		float cRoll = (float) Math.sqrt(1.0-(sRoll*sRoll));
		float sPitch = ax/norm;
		float cPitch = (float) Math.sqrt(1.0-(sPitch*sPitch));
		float Xh = (float) x*cPitch+z*sPitch;
		float Yh = (float) x*sRoll*sPitch+y*cRoll-y*sRoll*cPitch;
		psi = (float) Math.atan(Yh/Xh);
		mEditText_List.get(14).setText(""+psi*REG2DEG);
		return  psi;
	}
	
	
	
	
	
	private float[][] R = new float[3][3];
	
	
	private void initializeR(float psi){
		R[0][0] = (float)Math.cos(psi);	R[0][1] =(float) Math.sin(psi);		R[0][2] = 0f;
		R[1][0] = (float)-Math.sin(psi);	R[1][1] =(float) Math.cos(psi);		R[1][2] = 0f;
		R[2][0] = 0f;				R[2][1] = 0f;					R[2][2] = 1f;
	}
	
	private float[] calculateDgyro(float gx,float gy,float gz,float psi,float ax,float ay,float az){
		
		long now_t = System.currentTimeMillis();
		newtime = (int) (now_t- starttime);
		starttime = now_t;
		
		float Dt = (float)(newtime*0.001);
		//Dt = 0.22f;
		float[] nGyro = new float[3];
		float[] euler_gyro;
		float[] com_euler;
		float abs = (float)Math.sqrt(gx*gx+gy*gy+gz*gz);
		nGyro[0] =gx / abs;nGyro[1] = gy / abs;nGyro[2] = gz / abs;
		float theta = (float) (abs * Dt);
		float c = (float)Math.cos(theta);float s = (float)Math.sin(theta);
		float[][] R_M = new float[3][3];
		R_M[0][0] = (float) (c+nGyro[0]*nGyro[0]*(1-c));			R_M[0][1] = (float) (nGyro[0]*nGyro[1]*(1-c)+nGyro[2]*s);	R_M[0][2] = (float) (nGyro[0]*nGyro[2]*(1-c)-nGyro[1]*s);
		R_M[1][0] = (float) (nGyro[1]*nGyro[0]*(1-c)-nGyro[2]*s);	R_M[1][1] = (float) (c+nGyro[1]*nGyro[1]*(1-c));			R_M[1][2] = (float) (nGyro[1]*nGyro[2]*(1-c)+nGyro[0]*s);
		R_M[2][0] = (float) (nGyro[2]*nGyro[0]*(1-c)+nGyro[1]*s);	R_M[2][1] = (float) (nGyro[2]*nGyro[1]*(1-c)-nGyro[0]*s);	R_M[2][2] = (float) (c+nGyro[2]*nGyro[2]*(1-c));
		calculate_R(R_M);
		euler_gyro = calculate_Euler(psi);
		com_euler = calculate_compensation(euler_gyro,ax,ay,az);
		kalman_filter(com_euler);
		mix_kal_gyro(euler_gyro);
		
		return euler_gyro;
	}
	
	private void mix_kal_gyro(float[] euler_gyro){
		float[] com_euler = new float[3];
		for(int i=0;i<3;i++){
			com_euler[i] = euler_gyro[i] + kal_euler[i];
		}
		mEditText_List.get(12).setText(""+euler_gyro[0]*REG2DEG*-1);
		mEditText_List.get(13).setText(""+euler_gyro[1]*REG2DEG*-1);
		mEditText_List.get(14).setText(""+euler_gyro[2]*REG2DEG);
		fill_in_R(euler_gyro[0],euler_gyro[1],euler_gyro[2]);
	}
	
	private float[] calculate_compensation(float[] euler_gyro,float ax,float ay,float az){
		float[] euler_acc = new float[3];
		float[] com_euler = new float[3];
		euler_acc[0] = (float)Math.atan2(-ay,-az);
		euler_acc[1] = (float)Math.atan2(ax,Math.sqrt(ay*ay+az*az));
		euler_acc[2] = euler_gyro[2];
		/*mEditText_List.get(15).setText(""+euler_acc[0]*REG2DEG);
		mEditText_List.get(16).setText(""+euler_acc[1]*REG2DEG);
		mEditText_List.get(17).setText(""+euler_acc[2]*REG2DEG);*/
		com_euler[0] = euler_acc[0] - euler_gyro[0];
		com_euler[1] = euler_acc[1] - euler_gyro[1];
		com_euler[2] = euler_gyro[2];
		
		/*mEditText_List.get(12).setText(""+com_euler[0]*REG2DEG);
		mEditText_List.get(13).setText(""+com_euler[1]*REG2DEG);
		mEditText_List.get(14).setText(""+com_euler[2]*REG2DEG);*/
		return com_euler;
	}
	
	private void kalman_filter(float[] com_euler){
		for(int i=0;i<3;i++){
			estimate_error[i] += process_noise;
			kg[i] = (float) (estimate_error[i]/(estimate_error[i]+measurement_noise));
			kal_euler[i] += kg[i]*(com_euler[i]-kal_euler[i]);
			estimate_error[i] *= (1-kg[i]);
		}
	}
	
	private void calculate_R(float[][] RU){
		//R[0][*]
		R[0][0] = RU[0][0]*R[0][0]+RU[0][1]*R[1][0]+RU[0][2]*R[2][0];
		R[0][1] = RU[0][0]*R[0][1]+RU[0][1]*R[1][1]+RU[0][2]*R[2][1];
		R[0][2] = RU[0][0]*R[0][2]+RU[0][1]*R[1][2]+RU[0][2]*R[2][2];
		//R[1][*]
		R[1][0] = RU[1][0]*R[0][0]+RU[1][1]*R[1][0]+RU[1][2]*R[2][0];
		R[1][1] = RU[1][0]*R[0][1]+RU[1][1]*R[1][1]+RU[1][2]*R[2][1];
		R[1][2] = RU[1][0]*R[0][2]+RU[1][1]*R[1][2]+RU[1][2]*R[2][2];
		//R[2][*]
		R[2][0] = RU[2][0]*R[0][0]+RU[2][1]*R[1][0]+RU[2][2]*R[2][0];
		R[2][1] = RU[2][0]*R[0][1]+RU[2][1]*R[1][1]+RU[2][2]*R[2][1];
		R[2][2] = RU[2][0]*R[0][2]+RU[2][1]*R[1][2]+RU[2][2]*R[2][2];
	}
	
	private float[] calculate_Euler(float psi){
		float[] euler = new float[3];
		float roll,pitch;
		if(R[0][2]<=-1){
			roll = 0;
			pitch = pi/2;
			
		}else if(R[0][2]>=1){
			roll = 0;
			pitch = -pi/2;
		}else{
			roll = (float)Math.atan2(R[1][2], R[2][2]);
			pitch = (float)Math.asin(-R[0][2]);
		}
		euler[0]=roll;euler[1]=pitch;euler[2]=psi;
		/*mEditText_List.get(12).setText(""+roll*REG2DEG*-1);
		mEditText_List.get(13).setText(""+pitch*REG2DEG*-1);
		mEditText_List.get(14).setText(""+psi*REG2DEG);*/
		
		fill_in_R(roll,pitch,psi);
		return euler;
	}
	
	/**
	 * @param roll
	 * @param pitch
	 * @param psi
	 * @param C_roll cos(roll) @param S_roll sin(roll)
	 * @param C_pitch cos(pitch) @param S_pitch sin(pitch)
	 * @param C_psi cos(psi) @param S_psi sin(psi)  */
	private void fill_in_R(float roll,float pitch,float psi){
		//roll
		float C_roll = (float)Math.cos(roll); 	float S_roll = (float)Math.sin(roll);
		float C_pitch = (float)Math.cos(pitch);	float S_pitch = (float)Math.sin(pitch);
		float C_psi = (float)Math.cos(psi);		float S_psi = (float)Math.sin(psi);
		R[0][0] = C_pitch*C_psi;					R[0][1] = C_pitch*S_psi;					R[0][2] = -S_pitch;
		R[1][0] = S_roll*S_pitch*C_psi-C_roll*S_psi;R[1][1] = S_roll*S_pitch*S_psi+C_roll*C_psi;R[1][2] = S_roll*C_pitch;
		R[2][0] = C_roll*S_pitch*C_psi+S_roll*S_psi;R[2][1] = C_roll*S_pitch*S_psi-S_roll*C_psi;R[2][2] = C_roll*C_pitch;
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
