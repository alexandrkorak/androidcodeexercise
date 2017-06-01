package com.zeek.androidcodeexercise.image.file.downloader;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles image processing.
 *
 * @author Alexandr Sarapulov
 */
public final class ImageUtils {
    /**
     * Prevents from object instantiation.
     */
    private ImageUtils() {
        throw new RuntimeException("Utility class instantiation is prohibited.");
    }

    /**
     * Evaluated param to sample bitmap of required size.
     *
     * @param options   to calculate base on
     * @param reqWidth  of expected image
     * @param reqHeight of expected image
     * @return
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).
            long totalPixels = width * height / inSampleSize;

            // Anything more than 2x the requested pixels we'll sample down further
            final long totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels > totalReqPixelsCap) {
                inSampleSize *= 2;
                totalPixels /= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Samples bitmap of required size from file descriptor.
     *
     * @param fileDescriptor to fetch image data from
     * @param reqWidth       of expected image
     * @param reqHeight      of expected image
     * @return image bitmap
     */
    public static Bitmap decodeSampledBitmapFromDescriptor(FileDescriptor fileDescriptor, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
    }

    /**
     * Samples bitmap of required size from resource.
     *
     * @param res       to fetch image data from
     * @param resId     to fetch data of
     * @param reqWidth  of expected image
     * @param reqHeight of expected image
     * @return image bitmap
     */
    static Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    /**
     * Establishes connection to specified URL.
     *
     * @param urlStr to establish connection with
     * @return connection
     * @throws IOException
     */
    private static InputStream getHttpConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        return urlConnection.getInputStream();
    }

    /**
     * Load image from URL and sample it in {@link Bitmap} of required size ({@code width}; {@code height}).
     *
     * @param url    to load image from
     * @param width  of expected bitmap
     * @param height of expected bitmap
     * @return image bitmap
     */
    static Bitmap downloadImage(String url, int width, int height) {
        InputStream in = null;
        try {
            in = getHttpConnection(url);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            options.inSampleSize = calculateInSampleSize(options, width, height);
            options.inJustDecodeBounds = false;
            in.close();
            in = getHttpConnection(url);
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException e1) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                }
            }
        }
        return null;
    }

    /**
     * Check if current device Android version KITKAT or higher.
     *
     * @return false if Android OS version is lower than KITKAT, true otherwise
     */
    public static boolean hasKitKat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * Get the size in bytes of a bitmap in a BitmapDrawable. Note that from Android 4.4 (KitKat)
     * onward this returns the allocated memory size of the bitmap which can be larger than the
     * actual bitmap data byte count (in the case it was re-used).
     *
     * @param value
     * @return size in bytes
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static int getBitmapSize(BitmapDrawable value) {
        Bitmap bitmap = value.getBitmap();

        // From KitKat onward use getAllocationByteCount() as allocated bytes can potentially be
        // larger than bitmap byte count.
        if (hasKitKat()) {
            return bitmap.getAllocationByteCount();
        }

        return bitmap.getByteCount();
    }
}
