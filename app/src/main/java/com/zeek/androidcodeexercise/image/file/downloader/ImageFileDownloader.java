package com.zeek.androidcodeexercise.image.file.downloader;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.ImageView;

import com.zeek.androidcodeexercise.R;
import com.zeek.androidcodeexercise.image.file.downloader.cache.ImageCache;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * Provides ability to load and store image {@link Bitmap} objects.
 *
 * @author Alexandr Sarapulov
 */
public class ImageFileDownloader {
    private static final long FADE_IN_TIME = TimeUnit.SECONDS.toMillis(1);
    private static ImageFileDownloader singleton;
    private ImageCache imageCache;
    private Resources resources;

    private ImageFileDownloader(Context context) {
        resources = context.getResources();
        imageCache = new ImageCache(context);
    }

    public static ImageFileDownloader getInstance(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }

        if (singleton == null) {
            synchronized (ImageFileDownloader.class) {
                if (singleton == null) {
                    singleton = new ImageFileDownloader(context);
                }
            }
        }
        return singleton;
    }

    /**
     * Initializes cache.
     */
    public void initCache() {
        imageCache.initCache();
    }

    /**
     * Clears image caches on all levels.
     */
    public void clearCache() {
        imageCache.clearCache();
    }

    /**
     * Flushed cache.
     */
    public void flushCache() {
        imageCache.flushCache();
    }

    /**
     * Closes cache.
     */
    public void closeCache() {
        imageCache.closeCache();
    }

    /**
     * Loads image from {@code urlStr}, scales it to required size ({@code width}, {@code height}) and loads it
     * to {@code imageView}.
     *
     * @param urlStr to load image from
     * @param imageView to set scaled bitmap to
     * @param width of required image to be loaded
     * @param height of required image to be loaded
     */
    public void load(String urlStr, ImageView imageView, int width, int height) {
        if (urlStr == null || TextUtils.isEmpty(urlStr.trim())) {
            throw new IllegalArgumentException("Path must not be empty.");
        }

        BitmapDrawable bitmapDrawable = imageCache.getBitmapFromMemCache(urlStr, width, height);
        if (bitmapDrawable != null) {
            imageView.setImageDrawable(bitmapDrawable);
        } else if (cancelPotentialWork(urlStr, imageView)) {
            BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask(urlStr, imageView, width, height);
            AsyncDrawable asyncDrawable = new AsyncDrawable(
                    imageView.getResources(),
                    ImageUtils.decodeSampledBitmapFromResource(resources, R.drawable.place_holder, width, height),
                    bitmapWorkerTask);
            imageView.setImageDrawable(asyncDrawable);
            bitmapWorkerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /**
     * Encapsulate asynchronous task to bind it with target image view.
     */
    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskWeakReference;

        public AsyncDrawable(Resources resources, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(resources, bitmap);
            bitmapWorkerTaskWeakReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskWeakReference.get();
        }
    }

    /**
     * Returns asynchronous task if one is bound with passed image view.
     *
     * @param imageView to check if asynchronous task bound with
     * @return asynchronous task if one is bound, null otherwise
     */
    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof ImageFileDownloader.AsyncDrawable) {
                final ImageFileDownloader.AsyncDrawable asyncDrawable = (ImageFileDownloader.AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    /**
     * Cancel work on loading image from {@code urlStr} in case it is not actual from {@code imageView} no more.
     *
     * @param urlStr to indicate new actual URL references to load image from
     * @param imageView to check assigned asynchronous task for
     * @return true if asynchronous work hasn't been assigned or has been canceled, false if asynchronous task is
     * assigned and still relevant
     */
    private boolean cancelPotentialWork(String urlStr, ImageView imageView) {
        final ImageFileDownloader.BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            final String workerUrlStr = bitmapWorkerTask.urlStr;
            if (!urlStr.equals(workerUrlStr)) {
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already being progressed
                return false;
            }
        }
        return true;
    }

    /**
     * Image fetching asynchronous task.
     */
    class BitmapWorkerTask extends AsyncTask<Void, Void, BitmapDrawable> {
        final String urlStr;
        private final WeakReference<ImageView> imageViewWeakReference;
        private int width;
        private int height;

        BitmapWorkerTask(String urlStr, ImageView imageView, int width, int height) {
            this.urlStr = urlStr;
            imageViewWeakReference = new WeakReference<>(imageView);
            this.width = width;
            this.height = height;
        }

        @Override
        protected BitmapDrawable doInBackground(Void... params) {
            Bitmap bitmap = null;

            if (!isCancelled() && getAttachedImageView() != null) {
                bitmap = imageCache.getBitmapFromDiskCache(urlStr, width, height);
            }

            if (bitmap == null && !isCancelled() && getAttachedImageView() != null) {
                bitmap = ImageUtils.downloadImage(urlStr, width, height);
            }
            if (bitmap != null) {
                BitmapDrawable drawable = new BitmapDrawable(resources, bitmap);
                imageCache.addBitmapToCache(urlStr, width, height, drawable);
                return drawable;
            }
            return null;
        }

        @Override
        protected void onPostExecute(BitmapDrawable bitmapDrawable) {
            ImageView imageView = getAttachedImageView();
            if (imageView != null && bitmapDrawable != null) {
                Drawable backgrounds[] = new Drawable[]{
                        new ColorDrawable(Color.TRANSPARENT),
                        bitmapDrawable
                };

                TransitionDrawable crossFader = new TransitionDrawable(backgrounds);
                crossFader.setCrossFadeEnabled(true);
                imageView.setImageDrawable(crossFader);
                crossFader.startTransition((int) FADE_IN_TIME);
            }
        }

        /**
         * Returns image view with which current task is bound with.
         *
         * @return image view bound to this task if it still relavant, null otherwise
         */
        private ImageView getAttachedImageView() {
            final ImageView imageView = imageViewWeakReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
            if (this == bitmapWorkerTask) {
                return imageView;
            }
            return null;
        }
    }
}
