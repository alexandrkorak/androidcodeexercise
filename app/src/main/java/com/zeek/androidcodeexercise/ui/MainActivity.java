package com.zeek.androidcodeexercise.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;

import com.zeek.androidcodeexercise.Data;
import com.zeek.androidcodeexercise.R;
import com.zeek.androidcodeexercise.image.file.downloader.ImageFileDownloader;

/**
 * Main application UI element.
 *
 * @author Alexandr Sarapulo
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Amount of {@link GridLayoutManager} spans in portrait orientation.
     */
    private static final int PORTRAIT_ORIENTATION_SPAN_COUNT = 2;

    /**
     * Amount of {@link GridLayoutManager} spans in landscape orientation.
     */
    private static final int LANDSCAPE_ORIENTATION_SPAN_COUNT = PORTRAIT_ORIENTATION_SPAN_COUNT * 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initializing image loader cache.
        if (savedInstanceState == null) {
            ImageFileDownloader.getInstance(this).initCache();
        }

        initToolbar();
        initRecyclerView();
    }

    /**
     * Initiates current {@link AppCompatActivity} {@link Toolbar}.
     */
    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    /**
     * Initiates {@link RecyclerView} to display images.
     */
    private void initRecyclerView() {
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);

        // Changing {@link GridLayoutManager} spans count depending on screen orientation.
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ?
                PORTRAIT_ORIENTATION_SPAN_COUNT : LANDSCAPE_ORIENTATION_SPAN_COUNT;
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));

        // Setting it, as we has fixed amount of images to display to optimize performance.
        recyclerView.setHasFixedSize(true);

        int width = getScreenWidth();
        int imageSize = width / spanCount;
        recyclerView.setAdapter(new ImageAdapter(Data.URLS, imageSize, imageSize));
    }

    /**
     * Fetches screen width in pixels.
     *
     * @return screen width in pixels.
     */
    private int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    @Override
    protected void onPause() {
        ImageFileDownloader.getInstance(this).flushCache();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // As activity won't be recreated, we need to close image loader cache.
        if (isFinishing()) {
            ImageFileDownloader.getInstance(this).closeCache();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_clear_cache) {
            ImageFileDownloader.getInstance(this).clearCache();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
