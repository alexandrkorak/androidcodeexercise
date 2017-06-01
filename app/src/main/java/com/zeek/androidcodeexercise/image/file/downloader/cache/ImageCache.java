package com.zeek.androidcodeexercise.image.file.downloader.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.zeek.androidcodeexercise.image.file.downloader.ImageUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Images caches. Consist of disk level LRU cache and RAM LRU memory cache.
 *
 * @author Alexandr Sarapulov
 */
public class ImageCache implements Closeable {
    private static final String TAG = "ImageCache";

    private static final int MESSAGE_CLEAR = 0;
    private static final int MESSAGE_INIT_DISK_CACHE = 1;
    private static final int MESSAGE_FLUSH = 2;
    private static final int MESSAGE_CLOSE = 3;

    private LruCache<String, BitmapDrawable> memoryCache;
    private volatile boolean diskCacheStarting = true;
    private DiskLruCache diskCache;
    private final Object diskCacheLock = new Object();
    private final File diskCacheDir;
    private static final String DISK_CACHE_DIR_NAME = "cache";

    // Default memory cache size in kilobytes
    private static final int DEFAULT_MEM_CACHE_SIZE = 1024 * 5; // 5MB
    private static final int DISK_CACHE_SIZE = 25 * 1024 * 1024; // 20MB
    private static final int DISK_CACHE_INDEX = 0;

    private static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;
    private static final int DEFAULT_COMPRESS_QUALITY = 100;

    /**
     * Constructs several layers cache.
     *
     * @param context required to get directory.
     */
    public ImageCache(Context context) {
        Log.d(TAG, "ImageCache(" + context + ")");
        diskCacheDir = CacheUtils.getDiskCacheDir(context, DISK_CACHE_DIR_NAME);
    }

    /**
     * Initialises RAM LRU memory cache.
     */
    private void initMemoryCache() {
        Log.d(TAG, "initMemoryCache()");
        memoryCache = new LruCache<String, BitmapDrawable>(DEFAULT_MEM_CACHE_SIZE) {

            /**
             * Measure item size in kilobytes rather than units which is more practical
             * for a bitmap cache
             */
            @Override
            protected int sizeOf(String key, BitmapDrawable value) {
                final int bitmapSize = ImageUtils.getBitmapSize(value) / 1024;
                return bitmapSize == 0 ? 1 : bitmapSize;
            }
        };
    }

    /**
     * Initialises disk level cache.
     */
    private void initDiskCache() {
        Log.d(TAG, "initDiskCache()");
        if (!diskCacheDir.exists()) {
            Log.d(TAG, "initDiskCache().mkDirs");
            diskCacheDir.mkdirs();
        }
        synchronized (diskCacheLock) {
            if (diskCacheDir.getUsableSpace() >= DISK_CACHE_SIZE) {
                try {
                    diskCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            diskCacheStarting = false;
            diskCacheLock.notifyAll();
        }
    }

    /**
     * Initialises cache on all levels.
     */
    public void initCache() {
        initMemoryCache();
        new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
    }

    /**
     * Clears RAM LRU memory cache.
     */
    private void clearMemoryCache() {
        Log.d(TAG, "clearMemoryCache()");
        memoryCache.evictAll();
    }

    /**
     * Clears LRU disk level cache.
     */
    private void clearDiskCache() {
        Log.d(TAG, "clearDiskCache()");
        synchronized (diskCacheLock) {
            if (diskCache != null && !diskCache.isClosed()) {
                try {
                    diskCache.delete();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                diskCache = null;
                diskCacheStarting = true;
                initDiskCache();
            }

        }
    }

    /**
     * Clears caches. Contains asynchronous processing.
     */
    public void clearCache() {
        Log.d(TAG, "clearCache()");
        clearMemoryCache();
        new CacheAsyncTask().execute(MESSAGE_CLEAR);
    }

    /**
     * Flushes disk cache.
     */
    private void flushDiskCache() {
        synchronized (diskCacheLock) {
            if (diskCache != null) {
                try {
                    diskCache.flush();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Flushes asynchronously disk level cache.
     */
    public void flushCache() {
        new CacheAsyncTask().execute(MESSAGE_FLUSH);
    }

    private void closeDiskCache() {
        synchronized (diskCacheLock) {
            if (diskCache != null) {
                try {
                    if (!diskCache.isClosed()) {
                        diskCache.close();
                        diskCache = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "closeCacheInternal - " + e);
                }
            }
        }
    }

    /**
     * Closes disk level cache asynchronously.
     */
    public void closeCache() {
        new CacheAsyncTask().execute(MESSAGE_CLOSE);
    }

    @Override
    public void close() throws IOException {
        Log.d(TAG, "close() started");
        closeDiskCache();
        Log.d(TAG, "close() finished");
    }

    /**
     * Adds image to all levels caches.
     *
     * @param urlStr where bitmap has been fetched from
     * @param width of image
     * @param height of image
     * @param drawable actual image
     */
    public void addBitmapToCache(String urlStr, int width, int height, BitmapDrawable drawable) {
        final String hashKey = CacheUtils.getHashKey(new StringBuilder(urlStr).append(width).append(height).toString());
        memoryCache.put(hashKey, drawable);

        synchronized (diskCacheLock) {
            if (diskCache != null) {
                OutputStream out = null;
                try {
                    DiskLruCache.Snapshot snapshot = diskCache.get(hashKey);
                    Log.d(TAG, "add to disk: hash: " + hashKey + "; width: " + width + "; height: " + height + "; snapshot: " + snapshot);
                    if (snapshot == null) {
                        final DiskLruCache.Editor editor = diskCache.edit(hashKey);
                        if (editor != null) {
                            out = editor.newOutputStream(DISK_CACHE_INDEX);
                            drawable.getBitmap().compress(
                                    DEFAULT_COMPRESS_FORMAT, DEFAULT_COMPRESS_QUALITY, out);
                            editor.commit();
                            out.close();
                        }
                    } else {
                        snapshot.getInputStream(DISK_CACHE_INDEX).close();
                    }
                } catch (final IOException e) {
                } catch (Exception e) {
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    /**
     * Retrieves image from memory RAM LRU cache.
     *
     * @param urlStr to identify image
     * @param width image identifier
     * @param height image identifier
     * @return image
     */
    public BitmapDrawable getBitmapFromMemCache(String urlStr, int width, int height) {
        final String hashKey = CacheUtils.getHashKey(new StringBuilder(urlStr).append(width).append(height).toString());
        BitmapDrawable bitmapDrawable = memoryCache.get(hashKey);
        return bitmapDrawable;
    }

    /**
     * Fetches image from disk LRU cache.
     *
     * @param urlStr to identify image.
     * @param width
     * @param height
     * @return image
     */
    public Bitmap getBitmapFromDiskCache(String urlStr, int width, int height) {
        Bitmap bitmap = null;
        synchronized (diskCacheLock) {
            while (diskCacheStarting) {
                try {
                    diskCacheLock.wait();
                } catch (InterruptedException e) {
                }
            }

            if (diskCache != null) {
                final String hashKey = CacheUtils.getHashKey(new StringBuilder(urlStr).append(width).append(height).toString());
                InputStream inputStream = null;
                try {
                    final DiskLruCache.Snapshot snapshot = diskCache.get(hashKey);
                    Log.d(TAG, "get from disk: hash: " + hashKey + "; width: " + width + "; height: " + height + "; snapshot: " + snapshot);
                    if (snapshot != null) {
                        inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                        if (inputStream != null) {
                            FileDescriptor fd = ((FileInputStream) inputStream).getFD();

                            bitmap = ImageUtils.decodeSampledBitmapFromDescriptor(
                                    fd, width, height);
                        }
                    }
                } catch (final IOException e) {
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (IOException e) {
                    }
                }
            }
        }

        return bitmap;
    }

    /**
     * Intended for disk cache processing.
     */
    private class CacheAsyncTask extends AsyncTask<Integer, Void, Void> {

        @Override
        protected Void doInBackground(Integer... params) {
            switch (params[0]) {
                case MESSAGE_INIT_DISK_CACHE:
                    Log.d(TAG, "CacheAsyncTask() MESSAGE_INIT_DISK_CACHE");
                    initDiskCache();
                    break;
                case MESSAGE_CLEAR:
                    Log.d(TAG, "CacheAsyncTask() MESSAGE_CLEAR");
                    clearDiskCache();
                    break;
                case MESSAGE_FLUSH:
                    flushDiskCache();
                    break;
                case MESSAGE_CLOSE:
                    closeDiskCache();
                    break;
            }
            return null;
        }
    }
}
