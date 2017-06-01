package com.zeek.androidcodeexercise.ui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.zeek.androidcodeexercise.R;
import com.zeek.androidcodeexercise.image.file.downloader.ImageFileDownloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter to process image loading into {@link ImageView} views.
 *
 * @author Alexandr Sarapulov
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

    /** Holds URL references to images to be loaded into views. */
    private final List<String> urls = new ArrayList<>();

    /** Width of {@link ImageView} view to be shown. */
    private int imgWidth;

    /** Height of {@link ImageView} view to be shown. */
    private int imgHeight;

    /**
     * Instantiates image adapter.
     *
     * @param urls to load images from
     * @param imgWidth target image view's width
     * @param imgHeight target image view's height
     */
    public ImageAdapter(String[] urls, int imgWidth, int imgHeight) {
        Collections.addAll(this.urls, urls);
        this.imgWidth = imgWidth;
        this.imgHeight = imgHeight;
    }

    @Override
    public ImageAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ImageView v = (SquareImageView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.image_view, parent, false);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String url = urls.get(position);
        ImageView squareImageView = holder.imageView;
        Context context = squareImageView.getContext();
        ImageFileDownloader.getInstance(context).load(url, squareImageView, imgWidth, imgHeight);
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    /**
     * Process view holder pattern flow for views recycling.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;

        public ViewHolder(ImageView imageView) {
            super(imageView);
            this.imageView = imageView;
        }
    }
}
