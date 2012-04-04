/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.remoteandroid.ui.connect.qrcode;

import static org.remoteandroid.Constants.QRCODE_SHOW_CURRENT_DECODE;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.remoteandroid.R;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Yohann Melo
 */
public final class QRCodeScannerView extends ViewGroup
implements SurfaceHolder.Callback
{
	// View with surface for camera previous
	private SurfaceView mSurfaceView;
	// The surface holder
	private SurfaceHolder mHolder;
	// View overlow of camera previous
	private AnimView mAnimView;

	// The selected camera size
	private Size mCameraSize;
	/*private*/ Point mPreviousSize;
	
	/*private*/ Camera mCamera;
	/*private*/ int mRotation;
	private boolean mOptimizeSize;

	private Rect mCameraRect;
	private Rect mRect = new Rect(); // Working rect
	

	public interface QRCodeResult
	{
		void onQRCode(Result rawResult);
	}
	private QRCodeResult mCallBack;
	
	private static final long ANIMATION_DELAY = 100L;
	private static final int CURRENT_POINT_OPACITY = 0xFF;
	private static final int MAX_RESULT_POINTS = 20;

	private final Paint mPaint=new Paint();

	/*private*/ CaptureHandler mCaptureHandler;
	
	private Bitmap mResultBitmap;

	private Bitmap mPreviousBitmap;

	private Result mLastResult;

	private final int mMaskColor;

	private final int mResultColor;

	private final int mFrameColor;

	private final int mLaserColor;

	private final int mResultPointColor;

	private int mAnimPos;

	private List<ResultPoint> mPossibleResultPoints=new ArrayList<ResultPoint>(5);

	private List<ResultPoint> mLastPossibleResultPoints;

	private Long mStartTime = 0L;

//	private Handler mHandler = new Handler();

	private BeepManager mBeepManager;
	private Runnable mUpdateTimeTask = new Runnable()
	{
		public void run()
		{
			++mAnimPos;
			mStartTime = SystemClock.uptimeMillis();
			mCaptureHandler.postAtTime(
				this, mStartTime + 300);
		}
	};

	
	private static final int[] sSurfaceRotationToCameraDegree=
		{
			90, // 0
			0, // 90°
			270,// 180°
			180 // 270°
		};
	
	class AnimView extends View
	{
		AnimView(Context context)
		{
			super(context);
		}
		protected void onDraw(Canvas canvas) 
		{
			Rect frame = getFramingRectInPreview();
			if (frame == null)
			{
				return;
			}

			int width = canvas.getWidth();
			int height = canvas.getHeight();
//			mPaint.setColor(android.R.color.white);
//			mPaint.setStyle(Style.FILL);
//			canvas.drawRect(0, 0, width/2, height/2, mPaint);
			
			// Draw the exterior (i.e. outside the framing rect) darkened
			mPaint.setStyle(Style.FILL);
			mPaint.setStrokeWidth(1);
			mPaint.setColor(mResultBitmap != null ? mResultColor : mMaskColor);
			canvas.drawRect(0, 0, width, frame.top, mPaint);
			canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, mPaint);
			canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, mPaint);
			canvas.drawRect(0, frame.bottom + 1, width, height, mPaint);
			 
			 
			if (mResultBitmap != null)
			{
				// Draw the opaque result bitmap over the scanning rectangle
				mPaint.setAlpha(CURRENT_POINT_OPACITY);
				canvas.drawBitmap(mResultBitmap, null, frame, mPaint);
			}
			else
			{
				if (QRCODE_SHOW_CURRENT_DECODE)
				{
					if (mPreviousBitmap != null)
					{
						// Draw the opaque result bitmap over the scanning rectangle
						mPaint.setAlpha(CURRENT_POINT_OPACITY);
						canvas.drawBitmap(mPreviousBitmap, null, frame, mPaint);
					}
				}
				// Draw a red "laser scanner" line through the middle to show
				// decoding is active
				mPaint.setColor(mLaserColor);
				mPaint.setAlpha(128); // FIXME
				// mPaint.setAlpha(SCANNER_ALPHA[mScannerAlpha]);
				mPaint.setStyle(Style.STROKE);
				mPaint.setStrokeWidth(5);

				// TODO: Cadre plus petit que la réalité !
				int animPos = mAnimPos % 8;
				int w = frame.width();
				int h = frame.height();
				int ww = w / 20 * (animPos);
				int hh = h / 20 * (animPos);
				mRect.set(frame.left + ww, frame.top + hh, frame.right - ww, frame.bottom- hh);
				// mScannerAlpha = (mScannerAlpha + 1) % SCANNER_ALPHA.length;
				canvas.drawRect(mRect, mPaint);

				int boxLeft = mRect.width() / 8;
				int boxTop = mRect.height() / 8;
				int widthBox = boxLeft + (mRect.width() / 8);
				int heightBox = boxTop + (mRect.height() / 8);
				int animPos2 = (mAnimPos / 8) % 4;
				if (animPos2 != 0)
					canvas.drawRect(mRect.left + boxLeft, mRect.top + boxTop, mRect.left + widthBox, mRect.top + heightBox, mPaint);
				if (animPos2 != 1)
					canvas.drawRect(mRect.left + boxLeft, mRect.bottom - boxTop, mRect.left	+ widthBox, mRect.bottom - heightBox, mPaint);
				if (animPos2 != 2)
					canvas.drawRect(mRect.right - boxLeft, mRect.bottom - boxTop, mRect.right - widthBox, mRect.bottom - heightBox, mPaint);
				if (animPos2 != 3)
					canvas.drawRect(mRect.right - boxLeft, mRect.top + boxTop, mRect.right - widthBox, mRect.top + heightBox, mPaint);

				// Draw a two pixel solid black border inside the framing rect
				mPaint.setColor(mFrameColor);
				if (!QRCODE_SHOW_CURRENT_DECODE)
				{
					canvas.drawRect(
						frame.left, frame.top, frame.right + 1, frame.top + 2,
						mPaint);
					canvas.drawRect(
						frame.left, frame.top + 2, frame.left + 2,
						frame.bottom - 1, mPaint);
					canvas.drawRect(
						frame.right - 1, frame.top, frame.right + 1,
						frame.bottom - 1, mPaint);
					canvas.drawRect(
						frame.left, frame.bottom - 1, frame.right + 1,
						frame.bottom + 1, mPaint);
				}

//				Rect previewFrame = CameraManager.get().getFramingRectInPreview();
				Rect previewFrame = frame;
				float scaleX = frame.width() / (float) previewFrame.width();
				float scaleY = frame.height() / (float) previewFrame.height();

				//Log.d("camera", "surface size in view : " +this.getWidth() + "," + this.getHeight() + "  framing " + previewFrame);
				
				
				List<ResultPoint> currentPossible = mPossibleResultPoints;
				List<ResultPoint> currentLast = mLastPossibleResultPoints;
				if (currentPossible.isEmpty())
				{
					mLastPossibleResultPoints = null;
				}
				else
				{
					mPossibleResultPoints = new ArrayList<ResultPoint>(5);
					mLastPossibleResultPoints = currentPossible;
					mPaint.setAlpha(CURRENT_POINT_OPACITY);
					mPaint.setColor(mResultPointColor);
					synchronized (currentPossible)
					{
						for (ResultPoint point : currentPossible)
						{			mAnimView.invalidate();

							Point p = new Point();
							p.x = (int) point.getX();
							p.y = (int) point.getY();
							p = scaledRotatePoint(p, mRotation , previewFrame.width(), previewFrame.height());
							canvas.drawCircle(
								frame.left + (int) (p.x * scaleX),
								frame.top + (int) (p.y * scaleY), 6.0f,
								mPaint);
						}
					}
				}
				if (currentLast != null)
				{
					mPaint.setAlpha(CURRENT_POINT_OPACITY / 2);
					mPaint.setColor(mResultPointColor);
					synchronized (currentLast)
					{
						for (ResultPoint point : currentLast)
						{
							canvas.drawCircle(
								frame.left + (int) (point.getX() * scaleX),
								frame.top + (int) (point.getY() * scaleY), 3.0f,
								mPaint);
						}
					}
				}

				// Request another update at the animation interval, but only
				// repaint the laser line,
				// not the entire viewfinder mask.
				if (!QRCODE_SHOW_CURRENT_DECODE)
					postInvalidateDelayed(
						ANIMATION_DELAY, frame.left, frame.top, frame.right,
						frame.bottom);
			}
			
		}
	}
	// This constructor is used when the class is built from an XML resource.
	public QRCodeScannerView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		// Init camera previous
		mSurfaceView = new SurfaceView(context);
		addView(mSurfaceView);
		mAnimView=new AnimView(context);
		addView(mAnimView);
		mCaptureHandler = new CaptureHandler(getContext(),this);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		// Initialize these once for performance rather than calling them every
		// time in onDraw().
		Resources resources = getResources();
		mMaskColor = resources.getColor(R.color.qrcode_mask);
		mResultColor = resources.getColor(R.color.qrcode_result_color);
		mFrameColor = resources.getColor(R.color.qrcode_frame_color);
		mLaserColor = resources.getColor(R.color.qrcode_laser);
		mResultPointColor = resources.getColor(R.color.qrcode_possible_result_points);
		mLastPossibleResultPoints = null;
		if (mStartTime == 0L)
		{
			mStartTime = System.currentTimeMillis();
			mCaptureHandler.removeCallbacks(mUpdateTimeTask);
			mCaptureHandler.postDelayed(
				mUpdateTimeTask, 100);
		}
		mBeepManager=new BeepManager(getContext());
		

	}

	public void setOnResult(QRCodeResult callback)
	{
		mCallBack=callback;
	}
	/**
	 * Optimize the previous size for camera.
	 * @param optimizeSize true or false
	 */
	public void setOptimizeSize(boolean optimizeSize)
	{
		mOptimizeSize=optimizeSize;
	}
	public boolean isOptimizeSize()
	{
		return mOptimizeSize;
	}
	@Override
	public void invalidate()
	{
		super.invalidate();
		if (mAnimView!=null) mAnimView.invalidate();
	}
	// TODO
	public void switchCamera(Camera camera)
	{
		setCamera(camera);
		try
		{
			camera.setPreviewDisplay(mHolder);
		}
		catch (IOException exception)
		{
			if (E) Log.e(TAG_QRCODE, "IOException caused by setPreviewDisplay()", exception);
		}
		Camera.Parameters parameters = camera.getParameters();
		parameters.setPreviewSize(mCameraSize.width, mCameraSize.height);
		requestLayout();

		camera.setParameters(parameters);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		// We purposely disregard child measurements because act as a
		// wrapper to a SurfaceView that centers the camera preview instead
		// of stretching it.
		int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);
		if (mCamera!=null && mCameraSize==null)
		{
			mCameraSize = getOptimalCameraPreviewSize(mCamera.getParameters().getSupportedPreviewSizes(), width, height);
			if (V) Log.d(TAG_QRCODE,"camera size="+mCameraSize.width+","+mCameraSize.height);
		}
	}

	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		if (changed && getChildCount() > 0)
		{
			final int width = r - l;
			final int height = b - t;

			int previewWidth = width;
			int previewHeight = height;
			if (mOptimizeSize && mCameraSize != null)
			{
				previewWidth = mCameraSize.width;
				previewHeight = mCameraSize.height;
			}
			// Center the child SurfaceView within the parent.
			if (width * previewHeight > height * previewWidth)
			{
				final int scaledChildWidth = previewWidth * height / previewHeight;
				mCameraRect=new Rect((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
			}
			else
			{
				final int scaledChildHeight = previewHeight * width / previewWidth;
				mCameraRect=new Rect(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
			}
			mSurfaceView.layout(mCameraRect.left,mCameraRect.top,mCameraRect.width(),mCameraRect.height());
			if (mAnimView!=null)
				mAnimView.layout(mCameraRect.left,mCameraRect.top,mCameraRect.width(),mCameraRect.height());
		}
	}
	
	public void drawViewfinder()
	{
		if (!QRCODE_SHOW_CURRENT_DECODE)
		{
			mResultBitmap = null;
			invalidate();
		}
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live
	 * scanning display.
	 * 
	 * @param barcode An image of the decoded barcode.
	 */
	private void drawResultBitmap(Bitmap barcode)
	{
		if (D) Log.d(TAG_QRCODE,"Result w:" + barcode.getWidth() + " h:" + barcode.getHeight());
		mResultBitmap = barcode;
		invalidate();
	}

	private void drawPreviousBitmap(Bitmap barcode)
	{
		mPreviousBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point)
	{
		List<ResultPoint> points = mPossibleResultPoints;
		synchronized (point)
		{
			points.add(point);
			int size = points.size();
			if (size > MAX_RESULT_POINTS)
			{
				// trim it
				points.subList(
					0, size - MAX_RESULT_POINTS / 2).clear();
			}
		}
	}
	
	
	private Point scaledRotatePoint(Point p, int rotation, int canvasW, int canvasH)
	{
		Point tmp = new Point();
		if (D)
			Log.d(
				TAG_QRCODE, "rotating result points to match the device orientation (rotation: " + rotation + "°)");
		switch(rotation){
			case 90:
				tmp.x = canvasW - p.y;
				tmp.y = p.x;
			break;
			case 0:
				tmp.x = p.x;
				tmp.y = p.y;
				break;
			case 270:
				tmp.x = p.y;
				tmp.y = canvasH - p.x;
			break;
			case 180:
				tmp.x = canvasW - p.x;
				tmp.y = canvasH - p.y; 
			break;
		}
		return tmp;
	}

	public void handlePrevious(Result rawResult, Bitmap barcode)
	{
		mLastResult = rawResult;
		drawPreviousBitmap(barcode);
	}
	
	/**
	 * A valid qrcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult The contents of the barcode.
	 * @param barcode A greyscale bitmap of the camera data which was decoded.
	 */
	public void handleDecode(Result rawResult, Bitmap barcode)
	{
		drawResultBitmap(barcode);
		drawResultPoints(barcode, rawResult);
		mBeepManager.playBeepSoundAndVibrate();
		if (mCallBack!=null)
		{
			mCallBack.onQRCode(rawResult);
		}
	}
	
	/**
	 * Superimpose a line for 1D or dots for 2D to highlight the key features of
	 * the barcode.
	 * 
	 * @param barcode A bitmap of the captured image.
	 * @param rawResult The decoded results which contains the points to draw.
	 */
	private void drawResultPoints(Bitmap barcode, Result rawResult)
	{
		ResultPoint[] points = rawResult.getResultPoints();
		if (points != null && points.length > 0)
		{
			Canvas canvas = new Canvas(barcode);
			Paint paint = new Paint();
			paint.setColor(getResources().getColor(R.color.qrcode_result_image_border));
			paint.setStrokeWidth(3.0f);
			paint.setStyle(Paint.Style.STROKE);
			Rect border = new Rect(2, 2, barcode.getWidth() - 2, barcode.getHeight() - 2);
			canvas.drawRect(border, paint);

			paint.setColor(getResources().getColor(R.color.qrcode_result_points));
			if (points.length == 2)
			{
				paint.setStrokeWidth(4.0f);
				drawLine(canvas, paint, points[0], points[1]);
			}
			else if (points.length == 4 && (rawResult.getBarcodeFormat().equals(
				BarcodeFormat.UPC_A) || rawResult.getBarcodeFormat().equals(
				BarcodeFormat.EAN_13)))
			{
				// Hacky special case -- draw two lines, for the barcode and
				// metadata
				drawLine(canvas, paint, points[0], points[1]);
				drawLine(canvas, paint, points[2], points[3]);
			}
			else
			{
				
				for (ResultPoint point : points)
				{
					paint.setStrokeWidth(10.0f);
					paint.setColor(Color.RED);
					Point p = new Point();
					p.x = (int) point.getX();
					p.y = (int) point.getY();
					p = scaledRotatePoint(p, mRotation , canvas.getWidth(), canvas.getHeight());
					canvas.drawPoint(p.x, p.y, paint);
				}
			}
		}
	}

	private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b)
	{
		canvas.drawLine(a.getX(), a.getY(), b.getX(), b.getY(), paint);
	}

	
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
	{
		Camera.Parameters parameters = mCamera.getParameters();
		final String focusMode = findSettableValue(parameters.getSupportedFocusModes(),
			Camera.Parameters.FOCUS_MODE_AUTO, 
			Camera.Parameters.FOCUS_MODE_MACRO);
		if (focusMode != null)
			parameters.setFocusMode(focusMode);
		final String whiteBalance = findSettableValue(parameters.getSupportedWhiteBalance(),
			Camera.Parameters.WHITE_BALANCE_AUTO);
		if (whiteBalance!=null)
			parameters.setWhiteBalance(whiteBalance);
		final String sceneMode = findSettableValue(parameters.getSupportedSceneModes(),
			Camera.Parameters.SCENE_MODE_BARCODE,
			Camera.Parameters.SCENE_MODE_AUTO);
		if (sceneMode!=null)
			parameters.setSceneMode(sceneMode);
		
		mCamera.setParameters(parameters);
//		mCamera.setDisplayOrientation(mOrientation);
		mCaptureHandler.mStarted=false;
		mCaptureHandler.startScan();
		requestLayout();
	}

	public void setCamera(Camera camera)
	{
		mCamera = camera;
		if (mCamera != null)
		{
			setRotation(Surface.ROTATION_0);			
			requestLayout();
		}
	}
	public void setRotation(int displayRotation)
	{
		if (mCamera!=null)
		{
			mRotation=sSurfaceRotationToCameraDegree[displayRotation];
			Camera.Parameters parameters=mCamera.getParameters();
			parameters.setRotation(mRotation);
			mCamera.setParameters(parameters);
			mCamera.setDisplayOrientation(mRotation);
			requestLayout();
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		try
		{
			if (mCamera != null)
			{
				mCamera.setPreviewDisplay(holder);
				Size s=mCamera.getParameters().getPreviewSize();
				mPreviousSize=new Point(s.width,s.height);
				mCaptureHandler.startScan();
			}
		}
		catch (IOException exception)
		{
			if (E) Log.e(TAG_QRCODE, "IOException caused by setPreviewDisplay()", exception);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		// Surface will be destroyed when we return, so stop the preview.
		if (mCamera != null)
		{
			mCamera.stopPreview();
		}
	}
	private Size getOptimalCameraPreviewSize(List<Size> sizes, int w, int h)
	{
		// Select resolution with the minimum number of pixels
		int posMin=-1;
		long pixels=Long.MAX_VALUE;
		for (int i=0;i<sizes.size();++i)
		{
			Size size=sizes.get(i);
			long p=size.height*size.height;
			if (p<pixels)
			{
				pixels=p;
				posMin=i;
			}
		}
		return sizes.get(posMin);
	}

	/*package*/Rect mFramingRectInPreview;
	
	public Rect getFramingRectInPreview()
	{
//		if (mCameraResolution==null) return null;
		if (mFramingRectInPreview == null)
		{
//			mFramingRectInPreview=new Rect(0,0,10,5);
//			mFramingRectInPreview=new Rect(0,0,getWidth(),getHeight());
//			int width=Math.min(getWidth(),getHeight())/2;
//			int height=Math.min(getWidth(),getHeight())/2;
			int width=getWidth()/2;
			int height=getHeight()/2;
//			mFramingRectInPreview=new Rect(0,0,width,height); // 0,240,320,480
//			mFramingRectInPreview=new Rect(0,height,width,height*2); // 320,240,640,480
//			mFramingRectInPreview=new Rect(width,height,width*2,height*2); // 0,240,320,480
			// Version 80%
			final int sizemax=Math.min(getWidth(),getHeight())*80/100;
			mFramingRectInPreview=new Rect();
			mFramingRectInPreview.left=(getWidth()-sizemax)/2;
			mFramingRectInPreview.top=(getHeight()-sizemax)/2;
			mFramingRectInPreview.right=mFramingRectInPreview.left+sizemax;
			mFramingRectInPreview.bottom=mFramingRectInPreview.top+sizemax;
		}
		return mFramingRectInPreview;
	}
	public Rect getCameraRect()
	{
		return mCameraRect;
	}
	private static String findSettableValue(Collection<String> supportedValues, String... desiredValues)
	{
		String result = null;
		if (supportedValues != null)
		{
			for (String desiredValue : desiredValues)
			{
				if (supportedValues.contains(desiredValue))
				{
					result = desiredValue;
					break;
				}
			}
		}
		return result;
	}

	static final class BeepManager
	{

		private static final float BEEP_VOLUME = 0.10f;

		private static final long VIBRATE_DURATION = 200L;

		private Context mContext;

		private MediaPlayer mMediaPlayer;

		private boolean mPlayBeep;

		private boolean mVibrate;

		public BeepManager(Context context)
		{
			mContext = context;
			
			mMediaPlayer = null;
			updatePrefs();
		}

		public void setActivity(Activity activity)
		{
			mContext = activity;
		}

		public void updatePrefs()
		{
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(mContext);
			mPlayBeep = shouldBeep(
				prefs, mContext);
			// vibrate = prefs.getBoolean(PreferencesActivity.KEY_VIBRATE, false);
			if (mPlayBeep && mMediaPlayer == null)
			{
				// The volume on STREAM_SYSTEM is not adjustable, and users found it too loud,
				// so we now play on the music stream.
				// FIXME: Volume
//				WindowManager wm=(WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
//				wm.getDefaultDisplay().setVolumeControlStream(AudioManager.STREAM_MUSIC);
				mMediaPlayer = buildMediaPlayer(mContext);
			}
		}

		public void playBeepSoundAndVibrate()
		{
			if (mPlayBeep && mMediaPlayer != null)
			{
				mMediaPlayer.start();
			}
			if (mVibrate)
			{
				Vibrator vibrator = (Vibrator) mContext
						.getSystemService(Context.VIBRATOR_SERVICE);
				vibrator.vibrate(VIBRATE_DURATION);
			}
		}

		private static boolean shouldBeep(SharedPreferences prefs, Context activity)
		{
			boolean shouldPlayBeep = true;
			// FIXME prefs.getBoolean(PreferencesActivity.KEY_PLAY_BEEP, true);
			if (shouldPlayBeep)
			{
				// See if sound settings overrides this
				AudioManager audioService = (AudioManager) activity
						.getSystemService(Context.AUDIO_SERVICE);
				if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL)
				{
					shouldPlayBeep = false;
				}
			}
			return shouldPlayBeep;
		}

		private static MediaPlayer buildMediaPlayer(Context activity)
		{
			MediaPlayer mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			// When the beep has finished playing, rewind to queue up another one.
			mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
			{
				public void onCompletion(MediaPlayer player)
				{
					player.seekTo(0);
				}
			});

			AssetFileDescriptor file = activity.getResources().openRawResourceFd(R.raw.beep);
			try
			{
				mediaPlayer.setDataSource(
					file.getFileDescriptor(), file.getStartOffset(),
					file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			}
			catch (IOException ioe)
			{
				if (W) Log.w(TAG_QRCODE, ioe);
				mediaPlayer = null;
			}
			return mediaPlayer;
		}

	}

}
