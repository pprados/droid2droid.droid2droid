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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;

import com.google.zxing.LuminanceSource;

/**
 * This object extends LuminanceSource around an array of YUV data returned from
 * the camera driver, with the option to crop to a rectangle within the full
 * data. This can be used to exclude superfluous pixels around the perimeter and
 * speed up decoding. It works for any pixel format where the Y channel is
 * planar and appears first, including YCbCr_420_SP and YCbCr_422_SP.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Philippe Prados
 */
final class PlanarYUVLuminanceSource extends LuminanceSource
{
	private final byte[] mYuvData;
	private final int mDataWidth;
	private final int mDataHeight;
	private final Rect mScanningRectInYuvData;
	
	public PlanarYUVLuminanceSource(
			byte[] yuvData, int dataWidth,int dataHeight, 
			Rect scanningRectInYuvData,
			boolean reverseHorizontal)
	{
		super(scanningRectInYuvData.width(), scanningRectInYuvData.height());
		mScanningRectInYuvData=scanningRectInYuvData;
		if (mScanningRectInYuvData.width() > dataWidth || mScanningRectInYuvData.height() > dataHeight)
		{
			throw new IllegalArgumentException(
					"Crop rectangle does not fit within image data.");
		}

		mYuvData = yuvData;
		mDataWidth = dataWidth;
		mDataHeight = dataHeight;
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
		int offset = (y + mScanningRectInYuvData.top) * mScanningRectInYuvData.width() + mScanningRectInYuvData.left; // TODO: opt
		System.arraycopy(mYuvData, offset, row, 0, width);
		return row;
	}

	@Override
	public byte[] getMatrix()
	{
		int width = getWidth();
		int height = getHeight();

		// If the caller asks for the entire underlying image, save the copy and give them the
		// original data. The docs specifically warn that result.length must be ignored.
		if (width == mDataWidth && height == mDataHeight)
		{
			return mYuvData;
		}

		int area = width * height;
		byte[] matrix = new byte[area];
		int inputOffset = mScanningRectInYuvData.top * mDataWidth + mScanningRectInYuvData.left;

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

		int i = 0;

		Matrix matrix = new Matrix();

		int startY=mScanningRectInYuvData.top;
		int stopY=mScanningRectInYuvData.bottom;
		int startX=mScanningRectInYuvData.left;
		int stopX=mScanningRectInYuvData.right;
		matrix.setRotate(rotation, width/2f, height/2f);
		i = 0;
		for (int y = startY; y != stopY; ++y)
		{
			for (int x=startX;x != stopX; ++x)
			{
				int grey = yuv[y * mDataWidth + x] & 0xff;
				pixels[i++] = 0xFF000000 | (grey * 0x00010101);

			}
		}
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		return bitmap;
	}

//	private void reverseHorizontal(int width, int height)
//	{
//		byte[] yuvData = mYuvData;
//		for (int y = 0, rowStart = mScanningRectInYuvData.top * mDataWidth + mScanningRectInYuvData.left; y < height; y++, rowStart += mDataWidth)
//		{
//			int middle = rowStart + width / 2;
//			for (int x1 = rowStart, x2 = rowStart + width - 1; x1 < middle; x1++, x2--)
//			{
//				byte temp = yuvData[x1];
//				yuvData[x1] = yuvData[x2];
//				yuvData[x2] = temp;
//			}
//		}
//	}

}