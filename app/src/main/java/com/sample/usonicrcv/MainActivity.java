package com.sample.usonicrcv;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.icu.text.MessageFormat;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.jtransforms.fft.DoubleFFT_1D;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainActivity extends AppCompatActivity {
	private static final int SAMPLING_FREQ = 48000;
	private static final short THRESHOLD_AMP = 0x00ff;

	private AudioRecord		mAudioRecord = null;
	private boolean			mInRecording = false;
	private short[]			mRecordBuf = null;
	private DoubleFFT_1D	mFFT = null;
	private double[]		mFFTBuffer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		TLog.d("");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/* 権限チェック アプリにAudio権限がなければ要求する。 */
		if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			/* RECORD_AUDIOの実行権限を要求 */
			requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2222);
		}

		int bufferSize = AudioRecord.getMinBufferSize(SAMPLING_FREQ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		mAudioRecord= new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLING_FREQ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
		mRecordBuf	= new short[bufferSize/2];
		mFFT		= new DoubleFFT_1D(bufferSize/2);
		mFFTBuffer	= new double[bufferSize/2];

		findViewById(R.id.btnstartstop).setOnClickListener(v -> {
			Button btnstartstop = (Button)v;
			if( btnstartstop.getText().equals("開始") ) {
				btnstartstop.setText("終了");
				mInRecording = true;
				new Thread(() -> {
					/* 集音開始 */
					TLog.d("集音開始");
					mAudioRecord.startRecording();
					TLog.d("集音Loop開始");
					while (mInRecording) {
						mAudioRecord.read(mRecordBuf, 0, bufferSize/2);
						System.currentTimeMillis();
						/* 沈黙判定 */
						boolean bSilence = true;
						for(short s : mRecordBuf) {
							if(s > THRESHOLD_AMP) {
								bSilence = false;
								break;
							}
						}

						if (bSilence) {
							TLog.d("沈黙...");
							runOnUiThread(() -> {
								((TextView)findViewById(R.id.txtfreq)).setText("-");
							});
							continue;
						}
						int freq = doFFT(mRecordBuf);
						TLog.d(MessageFormat.format( "見つかった。{0}Hz", freq));
						runOnUiThread(() -> {
							((TextView)findViewById(R.id.txtfreq)).setText( MessageFormat.format("{0}Hz",freq));
						});
					}
					// 集音終了
					mAudioRecord.stop();
				}).start();
			}
			else {
				TLog.d("集音Loop終了");
				btnstartstop.setText("開始");
				mInRecording = false;
			}
		});

	}

	private int doFFT(short[] data) {
		for (int i = 0; i < data.length; i++) {
			mFFTBuffer[i] = (double)data[i];
		}
		/* FFT 実行 */
		mFFT.realForward(mFFTBuffer);

		/* 処理結果の複素数配列から各周波数成分の振幅値を求めピーク分の要素番号を得る */
		double maxAmp = 0;
		int index = 0;
		for (int i = 0; i < data.length/2; i++) {
			double a = mFFTBuffer[i*2];		/* 実部 */
			double b = mFFTBuffer[i*2 + 1];	/* 虚部 */
			/* a+ib の絶対値 √ a^2 + b^2 = r が振幅値 */
			double r = Math.sqrt(a*a + b*b);
			if (r > maxAmp) {
				maxAmp = r;
				index = i;
			}
		}
		/* 要素番号・サンプリングレート・FFT サイズからピーク周波数を求める */
		return index * SAMPLING_FREQ / data.length;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		TLog.d("");

		if (mAudioRecord != null) {
			if (mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
				mAudioRecord.stop();
			}
			mAudioRecord = null;
		}
	}

	/* 権限取得コールバック */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		TLog.d("s");
		/* 権限リクエストの結果を取得する. */
		if (requestCode == 2222) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				/* RECORD_AUDIOの権限を得た */
				TLog.d("RECORD_AUDIOの実行権限を取得!! OK.");
			} else {
				ErrPopUp.create(MainActivity.this).setErrMsg("失敗しました。\n\"許可\"を押下して、このアプリにAUDIO録音の権限を与えて下さい。\n終了します。").Show(MainActivity.this);
			}
		}
		/* 知らん応答なのでスルー。 */
		else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
		TLog.d("e");
	}

	/* ログ出力 */
	public static class TLog {
		public static void d(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.d("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void d(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.d("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		public static void i(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.i("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void i(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.i("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		public static void w(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.w("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void w(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.w("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		public static void e(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.e("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void e(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.e("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}
	}

	/* エラーpopup */
	public static class ErrPopUp extends PopupWindow {
		/* コンストラクタ */
		private ErrPopUp(Activity activity) {
			super(activity);
		}

		/* windows生成 */
		public static ErrPopUp create(Activity activity) {
			ErrPopUp retwindow = new ErrPopUp(activity);
			View popupView = activity.getLayoutInflater().inflate(R.layout.popup_layout, null);
			popupView.findViewById(R.id.btnClose).setOnClickListener(v -> {
				android.os.Process.killProcess(android.os.Process.myPid());
			});
			retwindow.setContentView(popupView);
			/* 背景設定 */
			retwindow.setBackgroundDrawable(ResourcesCompat.getDrawable(activity.getResources(), R.drawable.popup_background, null));

			/* タップ時に他のViewでキャッチされないための設定 */
			retwindow.setOutsideTouchable(true);
			retwindow.setFocusable(true);

			/* 表示サイズの設定 今回は幅300dp */
			float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, activity.getResources().getDisplayMetrics());
			retwindow.setWidth((int)width);
			retwindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
			return retwindow;
		}

		/* 文字列設定 */
		public ErrPopUp setErrMsg(String errmsg) {
			((TextView)this.getContentView().findViewById(R.id.txtErrMsg)).setText(errmsg);
			return this;
		}

		/* 表示 */
		public void Show(Activity activity) {
			View anchor = ((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
			this.showAtLocation(anchor, Gravity.CENTER,0, 0);
		}
	}
}