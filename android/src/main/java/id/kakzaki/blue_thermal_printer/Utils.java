package id.kakzaki.blue_thermal_printer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    // UNICODE 0x23 = #
    public static final byte[] UNICODE_TEXT = new byte[] {0x23, 0x23, 0x23,
            0x23, 0x23, 0x23,0x23, 0x23, 0x23,0x23, 0x23, 0x23,0x23, 0x23, 0x23,
            0x23, 0x23, 0x23,0x23, 0x23, 0x23,0x23, 0x23, 0x23,0x23, 0x23, 0x23,
            0x23, 0x23, 0x23};

    private static String hexStr = "0123456789ABCDEF";
    private static String[] binaryArray = { "0000", "0001", "0010", "0011",
            "0100", "0101", "0110", "0111", "1000", "1001", "1010", "1011",
            "1100", "1101", "1110", "1111" };

    private static int[][] Floyd8x8 = new int[][]{{0, 32, 8, 40, 2, 34, 10, 42}, {48, 16, 56, 24, 50, 18, 58, 26}, {12, 44, 4, 36, 14, 46, 6, 38}, {60, 28, 52, 20, 62, 30, 54, 22}, {3, 35, 11, 43, 1, 33, 9, 41}, {51, 19, 59, 27, 49, 17, 57, 25}, {15, 47, 7, 39, 13, 45, 5, 37}, {63, 31, 55, 23, 61, 29, 53, 21}};

    private static int[] p0 = new int[] { 0, 128 };
    private static int[] p1 = new int[] { 0, 64 };
    private static int[] p2 = new int[] { 0, 32 };
    private static int[] p3 = new int[] { 0, 16 };
    private static int[] p4 = new int[] { 0, 8 };
    private static int[] p5 = new int[] { 0, 4 };
    private static int[] p6 = new int[] { 0, 2 };
    

    public static byte[] decodeBitmap(Bitmap bmp, boolean useGrayscale, boolean moreContrast){
        int bmpWidth = bmp.getWidth();
        int bmpHeight = bmp.getHeight();

        List<String> list = new ArrayList<String>(); //binaryString list
        List<String> bmpHexList = new ArrayList<String>();
        StringBuffer sb;

        if (!useGrayscale) {
            int bitLen = bmpWidth / 8;
            int zeroCount = bmpWidth % 8;
    
            String zeroStr = "";
            if (zeroCount > 0) {
                bitLen = bmpWidth / 8 + 1;
                for (int i = 0; i < (8 - zeroCount); i++) {
                    zeroStr = zeroStr + "0";
                }
            }
    
            for (int i = 0; i < bmpHeight; i++) {
                sb = new StringBuffer();
                for (int j = 0; j < bmpWidth; j++) {
                    int color = bmp.getPixel(j, i);
    
                    int r = (color >> 16) & 0xff;
                    int g = (color >> 8) & 0xff;
                    int b = color & 0xff;
    
                    // if color close to white，bit='0', else bit='1'
                    if (r > 160 && g > 160 && b > 160)
                        sb.append("0");
                    else
                        sb.append("1");
                }
                if (zeroCount > 0) {
                    sb.append(zeroStr);
                }
                list.add(sb.toString());
            }
    
           bmpHexList = binaryListToHexStringList(list);
        }

        String commandHexString = "1D763000";
        String widthHexString = Integer
                .toHexString(bmpWidth % 8 == 0 ? bmpWidth / 8
                        : (bmpWidth / 8 + 1));
        if (widthHexString.length() > 10) {
            Log.e("decodeBitmap error", " width is too large");
            return null;
        } else if (widthHexString.length() == 1) {
            widthHexString = "0" + widthHexString;
        }
        widthHexString = widthHexString + "00";

        String heightHexString = Integer.toHexString(bmpHeight);
        if (heightHexString.length() > 10) {
            Log.e("decodeBitmap error", " height is too large");
            return null;
        } else if (heightHexString.length() == 1) {
            heightHexString = "0" + heightHexString;
        }
        heightHexString = heightHexString + "00";

        List<String> commandList = new ArrayList<String>();
        commandList.add(commandHexString+widthHexString+heightHexString);
        
        if (useGrayscale) {
            byte[] src = bitmapToBWPix(bmp, moreContrast);
            byte[] byteArray = pixToEscRastBitImageCmd(src);
            return hexList2Byte(commandList, byteArray);
        } else {
            commandList.addAll(bmpHexList);
            return hexList2Byte(commandList);
        }
    }

    public static byte[] bitmapToBWPix(Bitmap mBitmap, boolean moreContrast) {
        int[] pixels = new int[mBitmap.getWidth() * mBitmap.getHeight()];
        byte[] data = new byte[mBitmap.getWidth() * mBitmap.getHeight()];
        Bitmap grayBitmap = toGrayscale(mBitmap, moreContrast);
        grayBitmap.getPixels(pixels, 0, mBitmap.getWidth(), 0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        format_K_dither8x8(pixels, grayBitmap.getWidth(), grayBitmap.getHeight(), data);
        return data;
    }

    public static Bitmap toGrayscale(Bitmap bmpOriginal, boolean moreContrast) {
        int width = bmpOriginal.getWidth();
        int height = bmpOriginal.getHeight();
    
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
    
        cm.setSaturation(0);
    
        float brightness = moreContrast ? 0.8f : 1.8f; // change this value to set the brightness
        float contrast = moreContrast ? 1.3f : 0.8f; // change this value to set the contrast
    
        float[] brightnessMatrix = new float[]
                {
                        brightness, 0, 0, 0, 0,
                        0, brightness, 0, 0, 0,
                        0, 0, brightness, 0, 0,
                        0, 0, 0, 1, 0,
                };
    
        cm.postConcat(new ColorMatrix(brightnessMatrix));
    
        float scale = contrast + 1.f;
        float translate = (-.5f * scale + .5f) * 255.f;
    
        float[] contrastMatrix = new float[]
                {
                        scale, 0, 0, 0, translate,
                        0, scale, 0, 0, translate,
                        0, 0, scale, 0, translate,
                        0, 0, 0, 1, 0,
                };
    
        cm.postConcat(new ColorMatrix(contrastMatrix));
    
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
    
        return bmpGrayscale;
    }

    private static void format_K_dither8x8(int[] orgpixels, int xsize, int ysize, byte[] despixels) {
        int k = 0;
    
        for(int y = 0; y < ysize; ++y) {
            for(int x = 0; x < xsize; ++x) {
                if ((orgpixels[k] & 255) >> 2 > Floyd8x8[x & 7][y & 7]) {
                    despixels[k] = 0;
                } else {
                    despixels[k] = 1;
                }
    
                ++k;
            }
        }
    
    }

    public static byte[] pixToEscRastBitImageCmd(byte[] src) {
        byte[] data = new byte[src.length / 8];
        for (int i = 0, k = 0; i < data.length; i++) {
            data[i] = (byte)(p0[src[k]] + p1[src[k + 1]] + p2[src[k + 2]] + p3[src[k + 3]] + p4[src[k + 4]] + p5[src[k + 5]] + p6[src[k + 6]] + src[k + 7]);
            k += 8;
        } 
        return data;
    }

    public static List<String> binaryListToHexStringList(List<String> list) {
        List<String> hexList = new ArrayList<String>();
        for (String binaryStr : list) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < binaryStr.length(); i += 8) {
                String str = binaryStr.substring(i, i + 8);

                String hexString = myBinaryStrToHexString(str);
                sb.append(hexString);
            }
            hexList.add(sb.toString());
        }
        return hexList;

    }

    public static String myBinaryStrToHexString(String binaryStr) {
        String hex = "";
        String f4 = binaryStr.substring(0, 4);
        String b4 = binaryStr.substring(4, 8);
        for (int i = 0; i < binaryArray.length; i++) {
            if (f4.equals(binaryArray[i]))
                hex += hexStr.substring(i, i + 1);
        }
        for (int i = 0; i < binaryArray.length; i++) {
            if (b4.equals(binaryArray[i]))
                hex += hexStr.substring(i, i + 1);
        }

        return hex;
    }

    public static byte[] hexList2Byte(List<String> list) {
        List<byte[]> commandList = new ArrayList<byte[]>();

        for (String hexStr : list) {
            commandList.add(hexStringToBytes(hexStr));
        }

        byte[] bytes = sysCopy(commandList);
        return bytes;
    }

    public static byte[] hexList2Byte(List<String> list, byte[] byteArray) {
        List<byte[]> commandList = new ArrayList<byte[]>();

        for (String hexStr : list) {
            commandList.add(hexStringToBytes(hexStr));
        }
        commandList.add(byteArray);

        byte[] bytes = sysCopy(commandList);
        return bytes;
    }

    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    public static byte[] sysCopy(List<byte[]> srcArrays) {
        int len = 0;
        for (byte[] srcArray : srcArrays) {
            len += srcArray.length;
        }
        byte[] destArray = new byte[len];
        int destLen = 0;
        for (byte[] srcArray : srcArrays) {
            System.arraycopy(srcArray, 0, destArray, destLen, srcArray.length);
            destLen += srcArray.length;
        }
        return destArray;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }
}
