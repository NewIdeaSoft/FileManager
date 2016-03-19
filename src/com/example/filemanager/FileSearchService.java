package com.example.filemanager;

import java.io.File;
import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
/**
 * 
 * @author Administrator
 *搜索算法:递归遍历文件
 *广播
 *通知
 */

public class FileSearchService extends Service {
	public static final String FILE_SEARCHING_NOTIFICATION = "com.example.filemanager.FILE_NOTIFICATION";
	//通知
	private NotificationManager manager;
	private Notification notification;
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	private void fileSearchNotification(){
		Intent intent = new Intent(FILE_SEARCHING_NOTIFICATION);
		PendingIntent pendingintent = PendingIntent.getBroadcast(this, 0, intent, 0);
		notification = new Notification.Builder(this).setSmallIcon(R.drawable.logo)
				.setContentTitle("后台搜索中...")
				.setContentText("点击取消搜索")
				.setWhen(System.currentTimeMillis())
				.setAutoCancel(true)
				.setContentIntent(pendingintent)
				.build();
		if(manager==null){
			manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
		manager.notify(R.string.app_name, notification);
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.e("TAG", "onStartCommand");
		final String keyword = intent.getStringExtra("keyword");
		files = new ArrayList<String>();
		//固定搜索的范围为SD卡
		final File dir = Environment.getExternalStorageDirectory();
		fileSearchNotification();
		//需要在分线程执行
		new AsyncTask<Void, Integer, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				search(keyword,dir);
				return null;
			}
			protected void onPostExecute(Void result) {
				
				Intent result1 = new Intent("com.example.filemanager.FileBroadCastReceiver");
				result1.putStringArrayListExtra("ResultPath", files);
				manager.cancelAll();
				sendBroadcast(result1);
//				Log.e("TAG", files.size()+"");
			};
		}.execute();
		return super.onStartCommand(intent, flags, startId);
	}
	@Override
	public void onDestroy() {
		manager.cancelAll();
		super.onDestroy();
	}
	private ArrayList<String> files;
	/**
	 * 
	 * @param keyword,搜索关键字
	 * @param dir 搜索的目录
	 */
	private void search(String keyword,File dir){
		//文件夹，匹配后，进入下级目录，文件则直接匹配
		if(dir.canRead()){
//			Log.e("TAG", "searching "+dir.getName());
			File[] listFiles = dir.listFiles();
			if(listFiles!=null){
				for(File file:listFiles){
					if(file.getName().indexOf(keyword)!=-1){
						files.add(file.getAbsolutePath());
					}
					if(file.isDirectory()){
						search(keyword,file);
					}
				}
			}
		}
	}

}
