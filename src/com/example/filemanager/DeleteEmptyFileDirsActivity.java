package com.example.filemanager;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class DeleteEmptyFileDirsActivity extends Activity implements OnClickListener {
	private Button search_delete;
	private ProgressBar progressBar;
	private TextView infoTitle;
	private TextView infoContent;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_delete_empty_file_dirs);
		search_delete = (Button) findViewById(R.id.search_delete);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		infoTitle = (TextView) findViewById(R.id.infotitle);
		infoContent = (TextView) findViewById(R.id.infocontent);
		search_delete.setOnClickListener(this);
		emptyFileDirs = new ArrayList<File>();
	}
	private ArrayList<File> emptyFileDirs;

	// 一键扫描和清理空文件
	/*
	 * 遍历全部文件夹，检查文件夹是否为空，如为空记录路径 扫描完成，显示对话框，提示记录数目和是否删除 点击删除，遍历记录，进行删除
	 */
	private void findAllEmptyFileDir(File dir) {
		if (dir.isDirectory() && dir.canRead()) {
			File[] listFiles = dir.listFiles();
			if (listFiles.length == 0) {
				emptyFileDirs.add(dir);
			} else {
				for (File file : listFiles) {
					findAllEmptyFileDir(file);
				}
			}
		}

	}

	
	@Override
	public void onClick(View v) {
		new AsyncTask<Void, String, Integer>() {
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				progressBar.setVisibility(View.VISIBLE);
				infoContent.setText("正在搜索...");
			}
			@Override
			protected void onPostExecute(Integer result) {
				progressBar.setVisibility(View.GONE);
				infoContent.setText("共删除了"+result+"个空文件夹");
				emptyFileDirs.clear();
			}
			@Override
			protected void onProgressUpdate(String[] values) {
				infoContent.setText(values[0]);
			}
			@Override
			protected Integer doInBackground(Void... params) {
				
				File dir = Environment.getExternalStorageDirectory();
				findAllEmptyFileDir(dir);
				publishProgress(new String[]{"共找到了"+emptyFileDirs.size()+"个空文件夹.\n开始删除"});
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				for (int i = 0; i < emptyFileDirs.size(); i++) {
					publishProgress(new String[]{"正在删除：\n"+emptyFileDirs.get(i).getAbsolutePath()});
					emptyFileDirs.get(i).delete();
				}
				
				return emptyFileDirs.size();
			}
		}.execute();
	}
}
