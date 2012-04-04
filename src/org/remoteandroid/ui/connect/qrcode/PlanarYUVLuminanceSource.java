/*
 * Copyright 2009 ZXing authors
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

import org.remoteandroid.BuildConfig;
import static org.remoteandroid.Constants.TAG_QRCODE;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.util.Log;

import com.google.zxing.LuminanceSource;

/**
 * This object extends LuminanceSource around an array of YUV data returned from
 * the camera driver, with the option to crop to a rectangle within the full
 * data. This can be used to exclude superfluous pixels around the perimeter and
 * speed up decoding. It works for any pixel format where the Y channel is
 * planar and appears first, including YCbCr_420_SP and YCbCr_422_SP.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Yohann Melo
 */
public final class PlanarYUVLuminanceSource extends LuminanceSource
{

	private final byte[] mYuvData;

	private final int mDataWidth;

	private final int mDataHeight;

	private final int mLeft;

	private final int mTop;

	public PlanarYUVLuminanceSource(byte[] yuvData, int dataWidth,
			int dataHeight, int left, int top, int width, int height,
			boolean reverseHorizontal)
	{
		super(width, height);
		if (BuildConfig.DEBUG && left + width > dataWidth || top + height > dataHeight)
		{
			throw new IllegalArgumentException(
					"Crop rectangle does not fit within image data.");
		}

		mYuvData = yuvData;
		mDataWidth = dataWidth;
		mDataHeight = dataHeight;
		mLeft = left;
		mTop = top;
		if (reverseHorizontal)
		{
			reverseHorizontal(width, height);
		}

		// int[] m = {mLeft, (mDataHeight - this.mHeight - mTop),
		// (this.mDataWidth - this.mWidth - mLeft), mTop, (this.mDataWidth -
		// this.mWidth - mLeft), (this.mDataHeight - this.mHeight - mTop),
		// mLeft, mTop } ;
		// matrix = m;
	}
	@Override
	public byte[] getRow(int y, byte[] row)
	{
		if (y < 0 || y >= getHeight())
		{
			throw new IllegalArgumentException("Requested row is outside the image: " + y);
		}
		int width = getWidth();
		if (row == null || row.length < width)
		{
			row = new byte[width];
		}
		int offset = (y + mTop) * mDataWidth + mLeft;
		System.arraycopy(mYuvData, offset, row, 0, width);
		return row;
	}

	@Override
	public byte[] getMatrix()
	{
		int width = getWidth();
		int height = getHeight();

		// If the caller asks for the entire underlying image, save the copy and
		// give them the
		// original data. The docs specifically warn that result.length must be
		// ignored.
		if (width == mDataWidth && height == mDataHeight)
		{
			return mYuvData;
		}

		int area = width * height;
		byte[] matrix = new byte[area];
		int inputOffset = mTop * mDataWidth + mLeft;

		// If the width matches the full width of the underlying data, perform a
		// single copy.
		if (width == mDataWidth)
		{
			System.arraycopy(mYuvData, inputOffset, matrix, 0, area);
			return matrix;
		}

		// Otherwise copy one cropped row at a time.
		byte[] yuv = mYuvData;
		for (int y = 0; y < height; y++)
		{
			int outputOffset = y * width;
			System.arraycopy(yuv, inputOffset, matrix, outputOffset, width);
			inputOffset += mDataWidth;
		}
		return matrix;
	}

	@Override
	public boolean isCropSupported()
	{
		return true;
	}

	public Bitmap renderCroppedGreyscaleBitmap(int rotation)
	{

		int width = getWidth();
		int height = getHeight();
		int[] pixels = new int[width * height];
		byte[] yuv = mYuvData;
		int offsetX, offsetY;

		int i = 0;

		offsetX = mLeft;
		offsetY = mTop;
//		if (rotation == 90)
//		{	
//			i = 0;
//			for (int x = offsetY; x < height + offsetY; ++x)
//			{
//				for (int y=mDataHeight-offsetX;y>mDataHeight-width-offsetX;--y)
//				{
//					final int outputOffset = y * mDataWidth;
//					Log.d(TAG_QRCODE,x+","+y);
//					int grey = yuv[outputOffset + x] & 0xff;
//					pixels[i++] = 0xFF000000 | (grey * 0x00010101);
//
//				}
//			}
//		}
//		else
		{
			for (int y = offsetY; y < height + offsetY; ++y)
			{
				final int outputOffset = y * mDataWidth;
				for (int x = offsetX; x < width + offsetX; ++x)
				{
					int grey = yuv[outputOffset + x] & 0xff;
					pixels[i++] = 0xFF000000 | (grey * 0x00010101);

				}
			}

		}
		/*
		if (CameraManager.camera == Camera.CameraInfo.CAMERA_FACING_BACK)
		{

			offsetX = (mLeft);
			offsetY = mTop;
//			if (false && CameraManager.camera_orientation == 1
//					&& CameraManager.camera_orientation == 3)
//			{// || (Application.CAMERA == Camera.CameraInfo.CAMERA_FACING_FRONT
//				// && (Application.CAMERA_ORIENTATION == 0 ||
//				// Application.CAMERA_ORIENTATION == 3))){
//				// i = width * height - 1;
//				i = 0;
//				for (int y = offsetY; y < height + offsetY; y++)
//				{
//					//int outputOffset = y * mDataWidth;
//					
//					for (int x = offsetX; x < width + offsetX; x++)
//					{
//						int grey = yuv[y * mDataWidth + x] & 0xff;
//						pixels[i++] = 0xFF000000 | (grey * 0x00010101);
//
//					}
//				}
//			}
//			else
			{
				for (int y = offsetY; y < height + offsetY; y++)
				{
					int outputOffset = y * mDataWidth;
					for (int x = offsetX; x < width + offsetX; x++)
					{
						int grey = yuv[outputOffset + x] & 0xff;
						pixels[i++] = 0xFF000000 | (grey * 0x00010101);

					}
				}

			}

		}
		else
		{// camera facing front

			offsetX = mLeft;
			offsetY = (mTop);
			if (true  ||(CameraManager.mCameraRotation == 90 || CameraManager.mCameraRotation == 0))
			{
				i = 0;
				for (int y = offsetY; y < (height + offsetY); y++)
				{
					int outputOffset = y * mDataWidth;
					for (int x = width + offsetX; (x - offsetX) > 0; x--)
					{
						int grey = yuv[outputOffset + x] & 0xff;
						pixels[i++] = 0xFF000000 | (grey * 0x00010101);

					}
				}
			}
			else if (CameraManager.mCameraRotation == 180 || CameraManager.mCameraRotation == 270)
			{
				i = width * height;
				for (int y = (height + offsetY); (y - offsetY) > 0; y--)
				{
					int outputOffset = y * mDataWidth;
					for (int x = (width + offsetX); (x - offsetX) > 0; x--)
					{
						int grey = yuv[outputOffset + x] & 0xff;
						pixels[--i] = 0xFF000000 | (grey * 0x00010101);

					}
				}
			}
			else
			{
				i = 0;
				for (int y = (height + offsetY); (y - offsetY) > 0; y--)
				{
					int outputOffset = y * mDataWidth;
					for (int x = (width + offsetX); (x - offsetX) > 0; x--)
					{
						int grey = yuv[outputOffset + x] & 0xff;
						pixels[i++] = 0xFF000000 | (grey * 0x00010101);

					}
				}
			}
		} */

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
//		Matrix matrix = new Matrix();
//		matrix.setRotate(rotation, width/2, height/2);
//		bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		return bitmap;
	}

	private void reverseHorizontal(int width, int height)
	{
		byte[] yuvData = mYuvData;
		for (int y = 0, rowStart = mTop * mDataWidth + mLeft; y < height; y++, rowStart += mDataWidth)
		{
			int middle = rowStart + width / 2;
			for (int x1 = rowStart, x2 = rowStart + width - 1; x1 < middle; x1++, x2--)
			{
				byte temp = yuvData[x1];
				yuvData[x1] = yuvData[x2];
				yuvData[x2] = temp;
			}
		}
	}

}