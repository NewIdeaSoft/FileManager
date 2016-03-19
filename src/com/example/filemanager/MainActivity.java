package com.example.filemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * @author 杨涛 @category
 * 
 *         功能： 1.显示文件列表 2.点击列表，打开文件或进入下一及目录 3.长按列表，对文件重命名，移动，复制，删除 复制：支持文件夹复制
 *         4.文件搜索，后台进行,在通知栏显示通知，点击通知取消搜索 5.文件夹创建
 *         6.返回键功能：位于根目录以外目录，返回上一级目录，显示搜索结果时，返回显示结果前的列表，位于根目录时退出应用 需完善：
 *         1.优化搜索过程中退出应用的解决方案 2.增加双击返回，退出应用功能 3.修复部分文件类型显示不正确的bug4.禁止屏幕旋转
 * 
 */
public class MainActivity extends Activity implements OnItemClickListener, OnItemLongClickListener {
	// 文件路径
	private TextView path;
	// 文件列表
	private ListView fileListView;
	// 菜单选项
	private GridView menu;
	// 适配器
	private FileListAdapter fileListAdapter;
	private MenuAdapter menuAdapter;
	private List<File> filesList;
	// 是否启动复制或移动
	private int operateId;
	// 移动或复制的源文件
	private File sourceFile;
	// 菜单数据
	private final String[] MENU_NAME = { "手机", "SD卡", "搜索", "创建", "粘贴", "退出" };
	private final int[] MENU_ICON = { R.drawable.menu_phone, R.drawable.menu_sdcard, R.drawable.menu_search,
			R.drawable.menu_create, R.drawable.menu_palse, R.drawable.menu_exit };
	private FileBroadCastReceiver receiver;
	private FileBroadCastReceiver notificationRecerver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		// 注册广播接收器
		receiver = new FileBroadCastReceiver();
		IntentFilter filter = new IntentFilter("com.example.filemanager.FileBroadCastReceiver");
		MainActivity.this.registerReceiver(receiver, filter);
		notificationRecerver = new FileBroadCastReceiver();
		IntentFilter notificationFilter = new IntentFilter(FileSearchService.FILE_SEARCHING_NOTIFICATION);
		registerReceiver(notificationRecerver, notificationFilter);
		// 初始化
		exit = false;
		isSearching = false;
		path = (TextView) findViewById(R.id.path);
		fileListView = (ListView) findViewById(R.id.filelist);
		menu = (GridView) findViewById(R.id.menu);
		menuAdapter = new MenuAdapter();
		menu.setAdapter(menuAdapter);
		fileListAdapter = new FileListAdapter();
		// 默认复制未启动
		operateId = 0;
		// 源文件为null
		sourceFile = null;
		progress = null;
		// 初始化文件列表
		// fileName = new ArrayList<String>();
		filesList = new ArrayList<File>();
		// 默认显示SD卡目录
		File file = Environment.getExternalStorageDirectory();
		path.setText(file.getAbsolutePath());
		// 获取目录下的文件列表
		File[] files = file.listFiles();
		for (int i = 0; i < files.length; i++) {
			filesList.add(files[i]);
		}
		Collections.sort(filesList);
		fileListView.setAdapter(fileListAdapter);
		menu.setOnItemClickListener(this);
		fileListView.setOnItemLongClickListener(this);
		fileListView.setOnItemClickListener(new OnItemClickListener() {
			// 文件列表项的点击监听
			/**
			 * 点击，打开文件 1.点击文件夹，进入下级目录 2.点击文件打开文件
			 * 打开文件意味着要启动Activity,通过Intent传递数据，获得Uri，Intent的隐式使用
			 */
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// 1.点击文件夹，进入下级目录
				// 问题，当path为搜索结果时，点击文件打开文件功能不可用
				File file = filesList.get(position);
				// 获取文件扩展名
				String fileExpandedName = MainActivity.getFileExpandedName(file.getName());
				// 如果时文件夹，点击进入下级目录
				if (file.isDirectory()) {
					if (setFileList(file)) {
						path.setText(file.getAbsolutePath());
						fileListAdapter.notifyDataSetChanged();
					}
				} else {
					// 其他类型文件，启动选择应用程序对话框
					Intent intent = new Intent();
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					intent.setAction(Intent.ACTION_VIEW);
					// 获取文件的MimeType
					String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExpandedName);
					if (type != null) {

						intent.setDataAndType(Uri.fromFile(file), type);
						startActivity(intent);
					}
				}
			}
		});
	}

	/**
	 * 
	 * @param dir
	 *            文件目录
	 * @return true 显示目录下文件列表 false目录不存在或无法访问，显示空列表
	 */
	private boolean setFileList(File dir) {
		if (!dir.exists()) {
			Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
			return false;
		} else if (dir.canRead()) {
			File[] files = dir.listFiles();
			filesList.clear();
			if (files != null) {
				for (int i = 0; i < files.length; i++) {
					filesList.add(files[i]);
				}
				Collections.sort(filesList);
			}
			return true;
		} else {
			Toast.makeText(this, "没有权限访问", Toast.LENGTH_SHORT).show();
			return false;
		}
	}

	private ProgressDialog progress;
	private String searchKeyWord;
	private void copyFiles(File target){

		new AsyncTask<File, Integer, Void>() {
			/**
			 * 
			 * @param msourceFile
			 *            源文件
			 * @param targetFile
			 *            目标文件
			 */
			private void copyFile(File msourceFile, File targetFile) {
				FileInputStream fis = null;
				FileOutputStream fos = null;
				try {
					fis = new FileInputStream(msourceFile);
					fos = new FileOutputStream(targetFile);
					byte[] buffer = new byte[8192];
					int len = -1;
					while ((len = fis.read(buffer)) != -1) {
						fos.write(buffer, 0, len);
					}
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, "复制失败，目标不是文件类型",
							Toast.LENGTH_SHORT).show();
				} finally {
					try {
						if (fos != null) {
							fos.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						if (fis != null) {
							fis.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}

				}
			}

			private void copyFileInbackground(File msourceFile, File targetFile) {
				Log.d("TAG", msourceFile.getAbsolutePath());
				if (msourceFile.canRead()) {// 当源文件是文件夹时,在目标路径创建文件名为源文件夹文件名的文件夹
					if (msourceFile.isDirectory()) {
						targetFile.mkdir();
						File[] files = msourceFile.listFiles();
						for (File f : files) {
							File nextTargetFile = new File(
									targetFile.getAbsoluteFile() + "/" + f.getName());
							copyFileInbackground(f, nextTargetFile);
						}
					} else if (!msourceFile.isDirectory()) {
						copyFile(msourceFile, targetFile);
					}
				} else {
					Toast.makeText(MainActivity.this, "文件不可读", Toast.LENGTH_SHORT)
							.show();
				}

			}

			protected void onPreExecute() {
				super.onPreExecute();
				progress = new ProgressDialog(MainActivity.this);
				progress.setTitle("正在复制...");
				progress.show();
				
			};


			@Override
			protected Void doInBackground(File... params) {
				copyFileInbackground(sourceFile, params[0]);
				return null;
			}

			protected void onPostExecute(Void result) {
				progress.dismiss();
				sourceFile = null;
				operateId = 0;
				
			};

		}.execute(new File[]{target});
	
	}

	// 菜单项的点击监听
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

		switch (position) {
		case 0:
			// 获取手机目录路径
			// 打开手机目录
			// 获取目录下的文件列表
			// 更新列表
			if (!path.getText().toString().equals("/")) {
				File bootFileDir = new File("/");
				if (setFileList(bootFileDir)) {
					path.setText("/");
					fileListAdapter.notifyDataSetChanged();
				}
			}
			break;
		case 1:
			// 获取sd目录
			File externalStorageDir = Environment.getExternalStorageDirectory();
			if (!path.getText().toString().equals(externalStorageDir.getAbsolutePath())) {
				if (setFileList(externalStorageDir)) {
					path.setText(Environment.getExternalStorageDirectory().getAbsolutePath());
					fileListAdapter.notifyDataSetChanged();
				}
			}
			// 更新列表
			break;
		/*
		 * 搜索功能：
		 */
		case 2:
			View searchView = View.inflate(this, R.layout.search_dialog, null);
			final EditText searchWord = (EditText) searchView.findViewById(R.id.searchword);
			// 显示搜所对话框
			new AlertDialog.Builder(this).setTitle("文件搜索").setView(searchView)
					.setNegativeButton("搜索", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							// 获取搜索词条
							searchKeyWord = searchWord.getText().toString();
							if (searchKeyWord.equals("")) {
								Toast.makeText(MainActivity.this, "搜索关键字不能为空", Toast.LENGTH_SHORT).show();
							} else {
								isSearching = true;
								// 启动服务
								Intent intent = new Intent(MainActivity.this, FileSearchService.class);
								intent.putExtra("keyword", searchKeyWord);
								Toast.makeText(MainActivity.this, "要取消搜索请点击搜索通知", Toast.LENGTH_SHORT).show();
								startService(intent);
							}
						}
					}).setPositiveButton("取消", null).show();
			break;
		case 3:
			if (path.getText().toString().equals("/")) {
				Toast.makeText(MainActivity.this, "不能操作根目录", Toast.LENGTH_SHORT).show();
			} else {
				final View viewCreate = View.inflate(this, R.layout.createdialog, null);
				// 显示创建文件夹对话框
				new AlertDialog.Builder(this).setTitle("创建新文件夹").setView(viewCreate)
						.setNegativeButton("确定", new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {
								// 从对话框中获取创建的文件名字符串
								String newFileName = ((EditText) viewCreate.findViewById(R.id.filename)).getText()
										.toString();
								// 创建文件
								File file = new File(path.getText().toString() + "/" + newFileName);
								if (!file.exists()) {
									file.mkdir();
									filesList.add(file);
									Collections.sort(filesList);
									fileListAdapter.notifyDataSetChanged();
								} else {
									Toast.makeText(MainActivity.this, "命名重复，创建失败!", Toast.LENGTH_SHORT).show();
								}
							}
						}).setPositiveButton("取消", null).show();
			}
			break;
		case 4:
			// 粘贴
			/*
			 * 完成移动，直接改变所移动文件的路径 获取文件对象 重新设置文件路径，使用reNameTo(File newPath)
			 *
			 *
			 * 完成复制，复制文件 第一步获得所要复制的文件的路径,发出消息，携带原始路径信息 之后的步骤在粘贴中进行
			 * 第二步点击粘贴后，接收消息，原始路径信息，获得目标路径 第三步文件读写 第四步打开目标路径
			 */

			if (sourceFile != null) {
				final File targetDir = new File(path.getText().toString());
				final File targetFile = new File(path.getText().toString() + "/" + sourceFile.getName());

				switch (operateId) {
				case 1:
					if (sourceFile.canRead() && targetDir.canWrite()) {
						sourceFile.renameTo(targetFile);
						filesList.add(targetFile);
						Collections.sort(filesList);
						fileListAdapter.notifyDataSetChanged();
					}
					sourceFile = null;
					operateId = 0;
					break;
				case 2:
					if (targetFile.exists()) {
						new AlertDialog.Builder(MainActivity.this).setTitle("文件名重复").setNegativeButton("取消", null)
								.setPositiveButton("确定", new DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog, int which) {
										copyFiles(targetFile);
									}
								}).setMessage("点击确认覆盖，点击取消结束复制").show();
					} else {
						copyFiles(targetFile);
						filesList.add(targetFile);
					}
					fileListAdapter.notifyDataSetChanged();
					break;
				default:
					break;

				}
			}
			break;
		case 5:
			finish();
			break;
		default:
			break;
		}
	}

	// 文件列表的长按监听,显示菜单(或对话框)，删除、重命名、复制、移动

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {

		String[] items = { "删除", "重命名", "移动至", "复制" };
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				final File file = filesList.get(position);
				switch (which) {
				case 0:
					// 基本删除功能，需完善防误删的功能
					if (path.getText().toString().equals("/")) {
						Toast.makeText(MainActivity.this, "不能操作根目录", Toast.LENGTH_SHORT).show();
					} else if (file.canRead()) {
						new AlertDialog.Builder(MainActivity.this).setTitle("删除文件").setMessage("文件删除后不可恢复，确定删除吗？")
								.setPositiveButton("删除", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								deleteFile(file);
								filesList.remove(position);
								fileListAdapter.notifyDataSetChanged();
							}

							private void deleteFile(File mfile) {
								if (mfile.isDirectory()) {
									File[] files = mfile.listFiles();
									if (files.length == 0) {
										mfile.delete();
									} else {
										for (File f : files) {
											deleteFile(f);
										}
										deleteFile(mfile);
									}
								} else if (!mfile.isDirectory()) {
									mfile.delete();
								}
							}
						}).setNegativeButton("取消", null).show();
					}
					break;
				case 1:
					// 重命名功能
					if (path.getText().toString().equals("/")) {
						Toast.makeText(MainActivity.this, "不能操作根目录", Toast.LENGTH_SHORT).show();
					} else if (file.canRead()) {
						View view = View.inflate(MainActivity.this, R.layout.createdialog, null);
						final EditText newFileName = (EditText) view.findViewById(R.id.filename);
						new AlertDialog.Builder(MainActivity.this).setTitle("文件重命名").setView(view)
								.setPositiveButton("确定", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								File newFile = new File(
										path.getText().toString() + "/" + newFileName.getText().toString());
								if (!newFile.exists()) {
									file.renameTo(newFile);
									filesList.set(position, newFile);
									Collections.sort(filesList);
									fileListAdapter.notifyDataSetChanged();

								} else {
									Toast.makeText(MainActivity.this, "重命名失败，文件已存在。", Toast.LENGTH_SHORT).show();
								}
							}
						}).setNegativeButton("取消", null).show();
					}
					break;
				case 2:
					operateId = 1;
					sourceFile = file;
					Toast.makeText(MainActivity.this, "请转至目标文件夹下，按粘贴完成移动", Toast.LENGTH_SHORT).show();
					break;
				case 3:
					operateId = 2;
					sourceFile = file;
					Toast.makeText(MainActivity.this, "请转至目标文件夹下，按粘贴完成复制", Toast.LENGTH_SHORT).show();
					break;
				default:
					break;
				}
			}

		};
		Builder dialog = new AlertDialog.Builder(this).setItems(items, listener);
		dialog.show();
		return true;
	}

	class MenuAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return MENU_NAME.length;
		}

		@Override
		public Object getItem(int position) {
			return MENU_NAME[position];
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = View.inflate(MainActivity.this, R.layout.menu_item, null);
			}
			ImageView menuIcon = (ImageView) convertView.findViewById(R.id.menuIcon);
			TextView menuName = (TextView) convertView.findViewById(R.id.menuName);
			menuIcon.setImageResource(MENU_ICON[position]);
			menuName.setText(MENU_NAME[position]);
			return convertView;
		}

	}

	class FileListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return filesList.size();
		}

		@Override
		public Object getItem(int position) {
			return filesList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = View.inflate(MainActivity.this, R.layout.file_item, null);
			}
			ImageView fileIcon = (ImageView) convertView.findViewById(R.id.fileIcon);
			TextView fileName = (TextView) convertView.findViewById(R.id.fileName);
			TextView fileSize = (TextView) convertView.findViewById(R.id.fileSize);
			File file = filesList.get(position);
			// String name = (String) getItem(position);
			String name = filesList.get(position).getName();
			fileName.setText(name);
			String size = "";
			// 如果是文件夹，size显示包含文件的个数,字符串格式化
			if (file.isDirectory()) {
				if (file.canRead()) {
					size = file.listFiles().length + "个文件";
				} else {
					size = "没有权限查看";
				}

			} else {
				long length = file.length();
				if (length < 1024) {
					size = length + " B";
				} else if (length < 1024*1024) {
					size = String.format("%.2f Kb", length / 1024.00);
				} else if (length < 1024*1024*1024) {
					size = String.format("%.2f Mb", length / (1024.00*1024.00));
				} else {
					size = String.format("%.2f Gb", length / (1024.00*1024.00*1024.00));
				}
			}
			fileSize.setText(size);
			if (file.isDirectory()) {
				fileIcon.setImageResource(R.drawable.folder);
			} else {
				fileIcon.setImageResource(getImageResource(name));
			}
			return convertView;
		}
	}

	private int getImageResource(String name) {
		int imageResource = 0;
		String expandedName = getFileExpandedName(name);

		if (expandedName.equals("txt")) {
			imageResource = R.drawable.txt;
		} else if (expandedName.equals("zip") || expandedName.equals("rar")) {
			imageResource = R.drawable.zip7;

		} else if (expandedName.equals("mp3")) {
			imageResource = R.drawable.music;
		} else if (expandedName.equals("mp4") || expandedName.equals("wmv") || expandedName.equals("avi")) {
			imageResource = R.drawable.play86;
		} else if (expandedName.equals("apk")) {
			imageResource = R.drawable.apk;
		} else if (expandedName.equals("html")) {
			imageResource = R.drawable.chrome;
		} else if (expandedName.equals("jpg") || expandedName.equals("png") || expandedName.equals("bmp")) {
			imageResource = R.drawable.jpg3;
		} else if (expandedName.equals("pdf")) {
			imageResource = R.drawable.pdf;
		} else {
			imageResource = R.drawable.others;
		}
		return imageResource;
	}

	/**
	 * 
	 * @param name
	 *            文件名
	 * @return 文件的扩展名
	 */
	protected static String getFileExpandedName(String name) {
		String expandedName = "";
		int index = -1;
		index = name.lastIndexOf(".");
		if (index != -1) {
			expandedName = name.substring(index + 1, name.length()).toLowerCase();
		}
		return expandedName;
	}

	private boolean exit;
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == 1) {
				exit = false;
			}
		}
	};

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			String dir = path.getText().toString();
			if (!dir.equals("/") && !dir.contains("搜索结果:")) {
				File fileDir = new File(dir);
				File parentFile = fileDir.getParentFile();
				if (setFileList(parentFile)) {
					fileListAdapter.notifyDataSetChanged();
					path.setText(parentFile.getAbsolutePath());
				}
				return true;
			} else if (dir.contains("搜索结果:")) {
				File dirBeforeSearch = new File(pathBeforeSearch);
				if (setFileList(dirBeforeSearch)) {
					fileListAdapter.notifyDataSetChanged();
					path.setText(dirBeforeSearch.getAbsolutePath());
					pathBeforeSearch = null;
				}
				return true;
			} else {
				// 根目录下，双击back退出应用
				if (!exit) {
					exit = true;
					handler.sendEmptyMessageDelayed(1, 2000);
					Toast.makeText(this, "再次点击退出应用", Toast.LENGTH_SHORT).show();
					return true;
				}
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	private String pathBeforeSearch;

	/**
	 * 
	 * @author Administrator 搜索完成的广播接收器
	 */
	class FileBroadCastReceiver extends BroadcastReceiver {

		private ArrayList<String> searchResultPath;

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.e("TAG", "onReceive");
			if (intent.getAction().equals("com.example.filemanager.FileBroadCastReceiver")) {
				pathBeforeSearch = path.getText().toString();
				Bundle extras = intent.getExtras();
				searchResultPath = extras.getStringArrayList("ResultPath");
				isSearching = false;
				final int resultCount = searchResultPath.size();
				new AlertDialog.Builder(context).setTitle("搜索完成").setMessage("现在显示查询结果吗？")
						.setNegativeButton("确定", new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {
								path.setText("搜索结果: " + resultCount);
								filesList.clear();
								for (int i = 0; i < resultCount; i++) {
									filesList.add(new File(searchResultPath.get(i)));
								}
								Collections.sort(filesList);
								fileListAdapter.notifyDataSetChanged();
								Toast.makeText(MainActivity.this, "点击返回键，回到之前的目录", Toast.LENGTH_SHORT).show();
							}
						}).setPositiveButton("取消", null).show();
			} else if (intent.getAction().equals(FileSearchService.FILE_SEARCHING_NOTIFICATION)) {
				Intent serviceIntent = new Intent(MainActivity.this, FileSearchService.class);
				getApplicationContext().stopService(serviceIntent);
				isSearching = false;
			}
		}

	}

	private boolean isSearching;

	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
		unregisterReceiver(notificationRecerver);
		if (isSearching) {
			Intent intent = new Intent(MainActivity.this, FileSearchService.class);
			stopService(intent);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, 1, 0, "清理空文件");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == 1) {
			Intent intent = new Intent(MainActivity.this, DeleteEmptyFileDirsActivity.class);
			startActivity(intent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
