package org.remoteandroid.ui.connect.qrcode;

import static org.remoteandroid.Constants.QRCODE_AUTOFOCUS;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.Constants.TAG_QRCODE;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;
import java.util.List;

import org.remoteandroid.internal.Compatibility;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Yohann Melo
 * 
 */
public final class CameraManager
{

	public static int camera = Camera.CameraInfo.CAMERA_FACING_BACK; // Default
																		// camera

	public static int camera_rotation; // rotation of the device measured in
										// degrees
	public static int default_orientation;
	
	public static float density;

	static final int[] sOrientation =
	{ 90, 0, 270, 180 };

	private static final int MIN_FRAME_WIDTH = 50;

	private static final int MIN_FRAME_HEIGHT = MIN_FRAME_WIDTH;

	/*
	 * MAX height and width are calculated at runtime and are equal to the min
	 * of cam resolution.x and cam resolution.y
	 */
	private static int MAX_FRAME_HEIGHT;// = 320;

	private static int MAX_FRAME_WIDTH;// = MAX_FRAME_HEIGHT;

	private static final float DESIRED_FRAME_SIZE_BASELINE = 250f; // theorical
																	// size

	private static CameraManager sCameraManager;

	private final CameraConfigurationManager mConfigManager;

	private Camera mCamera;

	private Rect mFramingRect;

	private Rect mFramingRectInPreview;

	private boolean mInitialized;

	private boolean mPreviewing;

	private boolean mReverseImage;

	private boolean mRotate;

	/**
	 * Preview frames are delivered here, which we pass on to the registered
	 * handler. Make sure to clear the handler so it will only receive one
	 * message.
	 */
	private final PreviewCallback mPreviewCallback;

	/**
	 * Autofocus callbacks arrive here, and are dispatched to the Handler which
	 * requested them.
	 */
	private final AutoFocusCallback mAutoFocusCallback;

	/**
	 * Initializes this static object with the Context of the calling Activity.
	 * 
	 * @param context The Activity which wants to use the camera.
	 */
	public static void init(Context context)
	{
		if (sCameraManager == null)
		{
			sCameraManager = new CameraManager(context);
		}
	}

	/**
	 * Gets the CameraManager singleton instance.
	 * 
	 * @return A reference to the CameraManager singleton.
	 */
	public static CameraManager get()
	{
		return sCameraManager;
	}

	public int getOrientation()
	{

		return 0;
	}

	/*
	 * Set orientation based on rotation in degrees
	 */

	public void setOrientation(final int rotation)
	{

		// FIXME : works only for android >= 9
		if (Compatibility.VERSION_SDK_INT >= Compatibility.VERSION_GINGERBREAD)
		{
			new Runnable()
			{
				@Override
				public void run()
				{
					android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
					android.hardware.Camera.getCameraInfo(
						camera, info);
					int degrees = 0;
					switch (rotation)
					{
						case Surface.ROTATION_0:
							degrees = 0;
							break;
						case Surface.ROTATION_90:
							degrees = 90;
							break;
						case Surface.ROTATION_180:
							degrees = 180;
							break;
						case Surface.ROTATION_270:
							degrees = 270;
							break;
					}

					int result;
					if (camera == Camera.CameraInfo.CAMERA_FACING_FRONT)
					{
						result = (info.orientation + degrees) % 360;
						result = (360 - result) % 360; // compensate the mirror
					}
					else
					{ // back-facing
						result = (info.orientation - degrees + 360) % 360;
					}
					mCamera.setDisplayOrientation(result);
					camera_rotation = result;

				}

			}.run();

		}
		else
		{
			final Camera.Parameters p = mCamera.getParameters();

			if (Compatibility.VERSION_SDK_INT == Compatibility.VERSION_FROYO)
			{
				if(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180){
					
					Point surface = mConfigManager.getScreenResolution();
					if(surface.x > surface.y){
						sOrientation[0] = 0;
						sOrientation[1] = 270;
						sOrientation[2] = 180;
						sOrientation[3] = 90;
					}
				}
				new Runnable()
				{
					@Override
					public void run()
					{
					
						mCamera.setDisplayOrientation(sOrientation[rotation]);
						camera_rotation = sOrientation[rotation];
//						if (rotation == Surface.ROTATION_0)
//						{
//							// p.set("orientation", "portrait");
//							// p.set("rotation", 90);
//
//							camera_rotation = sOrientation[0];
//							mCamera.setDisplayOrientation(sOrientation[0]);
//						}
//						else if (rotation == Surface.ROTATION_90)
//						{
//							// p.set("orientation", "landscape");
//							// p.set("rotation", 90);
//							camera_rotation = sOrientation[1];
//							mCamera.setDisplayOrientation(sOrientation[1]);
//						}
//						else if (rotation == Surface.ROTATION_180)
//						{
//							camera_rotation = sOrientation[2];
//							mCamera.setDisplayOrientation(sOrientation[2]);
//						}
//						else if (rotation == Surface.ROTATION_270)
//						{
//							camera_rotation = sOrientation[3];
//							mCamera.setDisplayOrientation(sOrientation[3]);
//						}
					}
				}.run();
			}
			else
			{
				if (rotation == Surface.ROTATION_0)
				{
					p.set("orientation", "portrait");
					p.set("rotation", 90);
					camera_rotation = 90;
				}
				else
				// if (rotation == Configuration.ORIENTATION_LANDSCAPE)
				{
					p.set("orientation", "landscape");
					p.set("rotation", 90);
					camera_rotation = 0;
				}

				mCamera.setParameters(p);
			}

		}
		// if (Build.VERSION.SDK_INT >= 8)
		// mCamera.setDisplayOrientation(orientation);
		// else
		// // FIXME: Incompatibilité < 8
		// ;
	}
	
	private void setCameraParameters(){
		if(this.mPreviewing)
			mCamera.stopPreview();
		
		Camera.Parameters p = mCamera.getParameters();
//		List<String> effects = p.getSupportedColorEffects();
//		for(int i = effects.size(); i-- > 0;){
//			Log.e("camera", "effect : " + effects.get(i).toString());
//			if(effects.get(i).contains("mono"))
//				p.setColorEffect(effects.get(i));
//		}
		p.setZoom(7);
		
		mCamera.setParameters(p);
		if(this.mPreviewing)
			mCamera.startPreview();
	}

	private CameraManager(Context context)
	{

		this.mConfigManager = new CameraConfigurationManager(context);

		mPreviewCallback = new PreviewCallback(mConfigManager);
		mAutoFocusCallback = new AutoFocusCallback();
	}

	/**
	 * Opens the camera driver and initializes the hardware parameters.
	 * 
	 * @param holder The surface object which the camera will draw preview
	 *            frames into.
	 * @throws IOException Indicates the camera driver failed to open.
	 */
	public void openDriver(SurfaceHolder holder, int rotation) throws IOException
	{
		if (mCamera == null)
		{
			if (Compatibility.VERSION_SDK_INT >= Compatibility.VERSION_GINGERBREAD)
			{
				new Runnable()
				{

					@Override
					public void run()
					{
						mCamera = Camera.open(camera);
					}

				}.run();
			}
			else
				mCamera = Camera.open();

			if (mCamera == null)
			{
				throw new IOException();
			}
		}

		
		// ORIENTATION[rotation]);
		this.setCameraParameters();
		mCamera.setPreviewDisplay(holder);
		if (!mInitialized)
		{
			mInitialized = true;
			mConfigManager.initFromCameraParameters(mCamera);
		}
		CameraManager.get().setOrientation(
			rotation);
		mConfigManager.setDesiredCameraParameters(mCamera);
		MAX_FRAME_HEIGHT = Math.max(
			mConfigManager.getCameraResolution().y, mConfigManager.getCameraResolution().x);
		MAX_FRAME_WIDTH = MAX_FRAME_HEIGHT;
		Point p = mConfigManager.getCameraResolution();
		MAX_FRAME_HEIGHT = p.y;
		MAX_FRAME_WIDTH = p.x;
		if (D)
			Log.d(
				TAG_QRCODE, PREFIX_LOG + " MAX_FRAME_WIDTH/HEIGHT size: " + p.x + "," + p.y);
		// FIXME
		// SharedPreferences prefs =
		// PreferenceManager.getDefaultSharedPreferences(context);
		// reverseImage =
		// prefs.getBoolean(PreferencesActivity.KEY_REVERSE_IMAGE, false);
		// if (prefs.getBoolean(PreferencesActivity.KEY_FRONT_LIGHT, false)) {
		// FlashlightManager.enableFlashlight();
		// }
	}

	/**
	 * Closes the camera driver if still in use.
	 */
	public void closeDriver()
	{
		if (mCamera != null)
		{
			FlashlightManager.disableFlashlight();
			mCamera.release();
			mCamera = null;

			// Make sure to clear these each time we close the camera, so that
			// any scanning rect
			// requested by intent is forgotten.
			mFramingRect = null;
			mFramingRectInPreview = null;
			mInitialized = false;
		}
	}

	/**
	 * Asks the camera hardware to begin drawing preview frames to the screen.
	 */
	public void startPreview()
	{
		if (mCamera != null && !mPreviewing)
		{
			mCamera.startPreview();
			mPreviewing = true;
		}
	}

	/**
	 * Tells the camera to stop drawing preview frames.
	 */
	public void stopPreview()
	{
		if (mCamera != null && mPreviewing)
		{
			mCamera.stopPreview();
			mPreviewCallback.setHandler(
				null, 0);
			mAutoFocusCallback.setHandler(
				null, 0);
			mPreviewing = false;
		}
	}

	/**
	 * A single preview frame will be returned to the handler supplied. The data
	 * will arrive as byte[] in the message.obj field, with width and height
	 * encoded as message.arg1 and message.arg2, respectively.
	 * 
	 * @param handler The handler to send the message to.
	 * @param message The what field of the message to be sent.
	 */
	public void requestPreviewFrame(Handler handler, int message)
	{
		if (mCamera != null && mPreviewing)
		{
			mPreviewCallback.setHandler(
				handler, message);
			mCamera.setOneShotPreviewCallback(mPreviewCallback);
		}
	}

	/**
	 * Asks the camera hardware to perform an autofocus.
	 * 
	 * @param handler The Handler to notify when the autofocus completes.
	 * @param message The message to deliver.
	 */
	public void requestAutoFocus(Handler handler, int message)
	{
		if (mCamera != null && mPreviewing)
		{
			mAutoFocusCallback.setHandler(
				handler, message);
			if (V)
				Log.v(
					TAG_CONNECT, "Requesting auto-focus callback");
			if (QRCODE_AUTOFOCUS)
				mCamera.autoFocus(mAutoFocusCallback);
			else
				mAutoFocusCallback.onAutoFocus(
					true, mCamera);
		}
	}

	boolean isRotate()
	{
		return mRotate;
	}

	/**
	 * Like {@link #getFramingRect} but coordinates are in terms of the preview
	 * frame, not UI / screen.
	 */
	static Rect rotateRect(Rect r)
	{
		return new Rect(r.top, r.left, r.bottom, r.right);
	}

	static Point rotatePoint(Point p)
	{
		return new Point(p.y, p.x);
	}

	public Rect scaledRotateRect(Rect r)
	{
		Rect tmp;
		Point s = mConfigManager.getSurfaceResolution();
		tmp = new Rect();
		tmp.left = s.y - r.bottom;
		tmp.right = s.y - r.top;
		tmp.top = r.left;
		tmp.bottom = r.right;
		return tmp;
	}

	public Rect scaledRotateRect2(Rect r)
	{
		Rect tmp;
		Point s = mConfigManager.getSurfaceResolution();
		tmp = new Rect();
		tmp.left = s.x - r.right;
		tmp.right = s.x - r.left;
		tmp.top = s.y - r.bottom;
		tmp.bottom = s.y - r.top;
		return tmp;
	}

	public Rect scaledRotateRect3(Rect r)
	{
		Rect tmp;
		Point s = mConfigManager.getSurfaceResolution();
		tmp = new Rect();
		tmp.left = r.top;
		tmp.right = r.bottom;
		tmp.top = s.x - r.right;
		tmp.bottom = s.x - r.left;
		return tmp;
	}

	public Rect getFramingRectInPreview()
	{
		Point cameraResolution = mConfigManager.getCameraResolution();
		Point screenResolution = mConfigManager.getScreenResolution();
		Point surfaceResolution = mConfigManager.getSurfaceResolution();
		if (mFramingRectInPreview == null)
		{

			Rect rect = getFramingRect();
			if (rect == null)
				return null;
			mFramingRectInPreview = new Rect(rect);
			mFramingRectInPreview.left = mFramingRectInPreview.left * surfaceResolution.x / cameraResolution.x;
			mFramingRectInPreview.top = mFramingRectInPreview.top * surfaceResolution.y / cameraResolution.y;
			mFramingRectInPreview.right = mFramingRectInPreview.right * surfaceResolution.x / cameraResolution.x;
			mFramingRectInPreview.bottom = mFramingRectInPreview.bottom * surfaceResolution.y / cameraResolution.y;

			if (camera == Camera.CameraInfo.CAMERA_FACING_BACK)
			{
				if (camera_rotation == 90)
					mFramingRectInPreview = scaledRotateRect(mFramingRectInPreview);
				if (camera_rotation == 180)
					mFramingRectInPreview = scaledRotateRect2(mFramingRectInPreview);
				if (camera_rotation == 270)
					mFramingRectInPreview = scaledRotateRect3(mFramingRectInPreview);

			}
			else
			{
				if (camera_rotation == 90)
					mFramingRectInPreview = scaledRotateRect3(mFramingRectInPreview);
				if (camera_rotation == 270)
					mFramingRectInPreview = scaledRotateRect(mFramingRectInPreview);
				if (camera_rotation == 0)
					mFramingRectInPreview = scaledRotateRect2(mFramingRectInPreview);

			}

			if (V)
				Log.d(
					TAG_CONNECT, "cameraResolution=" + cameraResolution);
			if (V)
				Log.d(
					TAG_CONNECT, "surfaceResolution=" + surfaceResolution);
			if (V)
				Log.d(
					TAG_CONNECT, "framingRect=" + rect + " (w:" + rect.width() + ",h:" + rect.height() + ")");
			if (V)
				Log.d(
					TAG_CONNECT, "framingRect in previous=" + mFramingRectInPreview.toShortString() + " (w:"
							+ mFramingRectInPreview.width() + ",h:" + mFramingRectInPreview.height() + ")");

		}
		// return new Rect(0, 0, surfaceResolution.y,surfaceResolution.x);
		return mFramingRectInPreview;
	}

	/**
	 * Allows third party apps to specify the scanning rectangle dimensions,
	 * rather than determine them automatically based on screen resolution.
	 * 
	 * @param width The width in pixels to scan.
	 * @param height The height in pixels to scan.
	 */
	// public void setManualFramingSize(int width, int height) // FIXME: a
	// garder ?
	// {
	// Point screenResolution = mConfigManager.getScreenResolution();
	// if (width > screenResolution.x) {
	// width = screenResolution.x;
	// }
	// if (height > screenResolution.y) {
	// height = screenResolution.y;
	// }
	// int leftOffset = (screenResolution.x - width) / 2;
	// int topOffset = (screenResolution.y - height) / 2;
	// mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width,
	// topOffset + height);
	// if (D)
	// Log.d(TAG_CONNECT, "Calculated manual framing rect: "
	// + mFramingRect);
	// mFramingRectInPreview = null;
	// }

	// public void setManualFramingPos(Rect rect) // FIXME: a garder ?
	// {
	// // rect=new Rect(10,10,400,400); // FIXME: REMOVE
	// Point cameraResolution = mConfigManager.getCameraResolution();
	// Point screenResolution = mConfigManager.getScreenResolution();
	// if (CameraManager.HACK_ROTATE) // screenResolution.x<screenResolution.y)
	// {
	// // rect=rotateRect(rect);
	// // screenResolution=rotatePoint(screenResolution);
	// }
	//
	// mFramingRect = new Rect();
	// // mFramingRect.left=rect.left * cameraResolution.x /
	// // screenResolution.x;
	// // mFramingRect.top=rect.top * cameraResolution.y / screenResolution.y;
	// // mFramingRect.right=rect.right * cameraResolution.x /
	// // screenResolution.x;
	// // mFramingRect.bottom=rect.bottom * cameraResolution.y /
	// // screenResolution.y;
	// mFramingRect = rect;
	// if (D)
	// Log.d(TAG_CONNECT, "Calculated manual framing rect: "
	// + mFramingRect);
	// if (D)
	// Log.d(TAG_CONNECT, "rect=" + getFramingRectInPreview());
	// mFramingRectInPreview = getFramingRectInPreview();
	// }

	/**
	 * Calculates the framing rect which the UI should draw to show the user
	 * where to place the barcode. This target helps with alignment as well as
	 * forces the user to hold the device far enough away to ensure the image
	 * will be in focus.
	 * 
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public Rect getFramingRect()
	{
		if (mFramingRect == null)
		{
			if (mCamera == null)
			{
				return null;
			}
			Point cameraResolution = mConfigManager.getCameraResolution();
			Point surfaceResolution = mConfigManager.getSurfaceResolution();
			Point size = getSize();
			int leftOffset = (cameraResolution.x - size.x) / 2;
			int topOffset = (cameraResolution.y - size.y) / 2;

			// C'est naze sur le motorola
			// mFramingRect = new Rect(40, 0, 160, 240); // top right
			mFramingRect = new Rect(leftOffset, topOffset, size.x + leftOffset, size.y + topOffset); // TOP
																										// RIGHT
																										// corner
																										// corresponds
																										// to
																										// the
			// // bottom right point in LANDSCAPE
			// even in portrait

			// Attention, je ne modifie pas la frame, mais je la place sur la
			// résolution ecran et non sur la résolution camera

			// mFramingRect = new Rect(screenResolution.x-size.x,
			// screenResolution.y-size.y, screenResolution.x,
			// screenResolution.y); // bottom left
			// mFramingRect = new Rect(cameraResolution.x-size.x,
			// cameraResolution.y-size.y, cameraResolution.x,
			// cameraResolution.y); // bottom left
			// mFramingRect = new Rect(leftOffset, topOffset, leftOffset +
			// size.x, topOffset + size.y); // Center
			if (V)
				Log.d(
					TAG_CONNECT, "cam Resolution: " + cameraResolution);
			if (V)
				Log.d(
					TAG_CONNECT, "surface Resolution: " + surfaceResolution);
			if (V)
				Log.d(
					TAG_CONNECT, "Calculated framing rect: " + mFramingRect + " (w:" + mFramingRect.width() + ",h:"
							+ mFramingRect.height() + ")");
			Log.d(
				TAG_CONNECT, "framing : " + mFramingRect.toShortString());
		}
		return mFramingRect;
	}

	public Point getSize()
	{
		Point surfaceResolution = mConfigManager.getSurfaceResolution();
		Point cameraResolution = mConfigManager.getCameraResolution();

		// FIXME : remove
		// app.x and app.y are bounded to the device orientation
		// if the device orientation is not landscape we have to reverse it's
		// size to match the "unchanged" camera orientation
		// otherwize the larger edge of the camera would be paired with the
		// smaller resolution of the device
		// if (camera_orientation == 0 || camera_orientation == 2)
		// {
		// scaleX = scaleY;
		// scaleY = deviceSizeX;
		// // FIXME use XOR switch to save memory?
		// int tmp;
		// tmp = screenResolution.x;
		// screenResolution.x = screenResolution.y;
		// screenResolution.y = tmp;
		//
		// }

		int width, height;

		width = (int) (density * DESIRED_FRAME_SIZE_BASELINE * ((float) ((float) cameraResolution.x / (float) surfaceResolution.x)));
		height = (int) (density * DESIRED_FRAME_SIZE_BASELINE * ((float) ((float) cameraResolution.y / (float) surfaceResolution.y)));

		if (D)
			Log.d(
				"size", "display size: " + width + "," + height);
		if (width < MIN_FRAME_WIDTH)
		{
			width = MIN_FRAME_WIDTH;
		}
		else if (width > MAX_FRAME_WIDTH)
		{
			width = MAX_FRAME_WIDTH;
		}

		if (height < MIN_FRAME_HEIGHT)
		{
			height = MIN_FRAME_HEIGHT;
		}
		else if (height > MAX_FRAME_HEIGHT)
		{
			height = MAX_FRAME_HEIGHT;
		}

		// return new Point(cameraResolution.x,cameraResolution.y);
		return new Point(width, height);
	}

	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 * 
	 * @param data A preview frame.
	 * @param width The width of the image.
	 * @param height The height of the image.
	 * @return A PlanarYUVLuminanceSource instance.
	 */
	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height)
	{
		Rect rect = getFramingRect();
		// FIXME: remove Log.e("camera", "buildluminancesource w,h: " + width +
		// "," + height);
		int previewFormat = mConfigManager.getPreviewFormat();
		String previewFormatString = mConfigManager.getPreviewFormatString();

		switch (previewFormat)
		{
		// This is the standard Android format which all devices are
		// REQUIRED to
		// support.
		// In theory, it's the only one we should ever care about.
			case PixelFormat.YCbCr_420_SP:
				// This format has never been seen in the wild, but is
				// compatible as
				// we only care
				// about the Y channel, so allow it.
			case PixelFormat.YCbCr_422_SP:

				// new Rect(r.top, r.left, r.bottom, r.right);
				return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(),
						rect.height(), mReverseImage);
				// return new PlanarYUVLuminanceSource(data, height, width,
				// rect.top, rect.left, rect.height(), rect.width(),
				// mReverseImage);

			default:
				// The Samsung Moment incorrectly uses this variant instead of
				// the
				// 'sp' version.
				// Fortunately, it too has all the Y data up front, so we can
				// read
				// it.
				if ("yuv420p".equals(previewFormatString))
				{
					return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(),
							rect.height(), mReverseImage);
				}
		}
		throw new IllegalArgumentException("Unsupported picture format: " + previewFormat + '/' + previewFormatString);
	}

	public void setScreenResolutionValues(Point p)
	{
		this.mConfigManager.setSurfaceResolutionValues(p);
	}
	
	public void setParameters(int zoom, String flashmode){
//		Camera.Parameters p = mCamera.getParameters();
//		List<String> list = p.getSupportedFlashModes();
//		if(list.contains(flashmode))
//			p.setFlashMode(flashmode);
//		p.setZoom(zoom);
		
	}
	public List<String> getSupportedFlashModes(){
		if(mCamera != null)
			return mCamera.getParameters().getSupportedFlashModes();
		return null;
	}
	
}