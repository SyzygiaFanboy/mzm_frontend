package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.model.Song;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MusicDetailActivity extends AppCompatActivity {
    private MusicPlayer musicPlayer;

    private ImageView ivCover;
    private ImageView ivBgBlur;
    private View vBgTint;
    private TextView tvSongName;
    private TextView tvArtistName;
    private TextView tvTime;
    private SeekBar seekBar;

    private ImageButton btnMode;
    private ImageButton btnPrev;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnPlaylist;

    private boolean isTracking = false;
    private int selectedPosition = -1;

    private BottomSheetDialog playQueueDialog;
    private RecyclerView playQueueRv;
    private PlayQueueAdapter playQueueAdapter;

    private final MusicPlayer.ProgressListener progressListener = (currentPosition, totalDuration) -> runOnUiThread(() -> {
        if (!isTracking) {
            seekBar.setMax(Math.max(totalDuration, 0));
            seekBar.setProgress(Math.max(currentPosition, 0));
        }
        tvTime.setText(formatTime(currentPosition) + " / " + formatTime(totalDuration));
    });

    private final MusicPlayer.OnPlaybackStateChangeListener stateListener = new MusicPlayer.OnPlaybackStateChangeListener() {
        @Override
        public void onPlaybackStateChanged() {
            runOnUiThread(() -> {
                updatePlayPauseIcon();
                updateModeIcon();
            });
        }

        @Override
        public void onSongChanged() {
            runOnUiThread(() -> {
                updateSongInfo();
                updatePlayPauseIcon();
                updateModeIcon();
                updatePlayQueueDialogIfShowing();
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_detail);

        musicPlayer = ((MyApp) getApplication()).getMusicPlayer();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationIcon(R.drawable.ic_nav_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ivCover = findViewById(R.id.detail_album_cover);
        ivBgBlur = findViewById(R.id.detail_bg_blur);
        vBgTint = findViewById(R.id.detail_bg_tint);
        tvSongName = findViewById(R.id.detail_song_name);
        tvArtistName = findViewById(R.id.detail_artist_name);
        tvTime = findViewById(R.id.detail_time);
        seekBar = findViewById(R.id.detail_progress);

        btnMode = findViewById(R.id.btn_mode);
        btnPrev = findViewById(R.id.btn_prev);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);
        btnPlaylist = findViewById(R.id.btn_playlist);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvTime.setText(formatTime(progress) + " / " + formatTime(seekBar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTracking = false;
                musicPlayer.seekTo(seekBar.getProgress());
            }
        });

        setBouncyClick(btnMode, this::cyclePlayMode);
        setBouncyClick(btnPrev, this::playPrevious);
        setBouncyClick(btnPlayPause, this::togglePlayPause);
        setBouncyClick(btnNext, this::playNext);
        setBouncyClick(btnPlaylist, this::showPlayQueueBottomSheet);

        updateSongInfo();
        updatePlayPauseIcon();
        updateModeIcon();
    }

    @Override
    protected void onResume() {
        super.onResume();
        musicPlayer.addProgressListener(progressListener);
        musicPlayer.addOnPlaybackStateChangeListener(stateListener);
        Song current = musicPlayer.getCurrentSong();
        if (current != null) {
            tvTime.setText(formatTime(musicPlayer.getCurrentPosition()) + " / " + formatTime(musicPlayer.getDuration()));
        } else {
            tvTime.setText("00:00 / 00:00");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        musicPlayer.removeProgressListener(progressListener);
        musicPlayer.removeOnPlaybackStateChangeListener(stateListener);
    }

    private void updateSongInfo() {
        Song currentSong = musicPlayer.getCurrentSong();
        if (currentSong == null) {
            tvSongName.setText("暂无歌曲");
            tvArtistName.setText("未知艺术家");
            ivCover.setImageResource(R.drawable.default_cover);
            applyDefaultBackground();
            seekBar.setMax(0);
            seekBar.setProgress(0);
            tvTime.setText("00:00 / 00:00");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
            btnPlayPause.setEnabled(false);
            return;
        }

        String fullName = currentSong.getName();
        String songName = fullName;
        String artistName = "未知艺术家";
        if (fullName != null && fullName.contains(" - ")) {
            String[] parts = fullName.split(" - ", 2);
            if (parts.length == 2) {
                artistName = parts[0].trim();
                songName = parts[1].trim();
            }
        }
        tvSongName.setText(songName != null ? songName : "暂无歌曲");
        tvArtistName.setText(artistName);

        MusicCoverUtils.loadCoverSmart(currentSong.getFilePath(), currentSong.getCoverUrl(), this, ivCover, this::applyDynamicBackground);

        btnPrev.setEnabled(true);
        btnNext.setEnabled(true);
        btnPlayPause.setEnabled(true);
    }

    private void applyDefaultBackground() {
        if (ivBgBlur != null) {
            ivBgBlur.setImageDrawable(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivBgBlur.setRenderEffect(null);
            }
        }
        if (vBgTint != null) {
            vBgTint.setBackgroundColor(0x33c69bc5);
        }
    }

    private void applyDynamicBackground(Bitmap coverBitmap) {
        if (coverBitmap == null || coverBitmap.isRecycled()) {
            return;
        }

        new Thread(() -> {
            Bitmap paletteSource = scaleBitmap(coverBitmap, 64);
            Palette palette = Palette.from(paletteSource).generate();
            int baseColor = palette.getVibrantColor(palette.getDominantColor(0xFF2E2E2E));

            Bitmap blurSource = scaleBitmap(coverBitmap, 240);
            Bitmap blurred = null;
            boolean useRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
            if (!useRenderEffect) {
                blurred = fastBlur(blurSource, 18);
            }

            Bitmap finalBlurred = blurred;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (ivBgBlur != null) {
                    if (useRenderEffect) {
                        ivBgBlur.setImageBitmap(blurSource);
                        ivBgBlur.setRenderEffect(RenderEffect.createBlurEffect(26f, 26f, Shader.TileMode.CLAMP));
                    } else {
                        ivBgBlur.setImageBitmap(finalBlurred != null ? finalBlurred : blurSource);
                    }
                }

                if (vBgTint != null) {
                    vBgTint.setBackground(createBackgroundGradient(baseColor));
                }
            });
        }).start();
    }

    private GradientDrawable createBackgroundGradient(int baseColor) {
        int top = adjustAlpha(baseColor, 0.48f);
        int mid = adjustAlpha(baseColor, 0.26f);
        int bottom = adjustAlpha(Color.BLACK, 0.10f);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, mid, bottom});
        drawable.setDither(true);
        return drawable;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.min(255, Math.max(0, (int) (255 * factor)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private Bitmap scaleBitmap(Bitmap source, int targetMinEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= 0 || h <= 0) {
            return source;
        }
        int minEdge = Math.min(w, h);
        if (minEdge <= targetMinEdge) {
            return source;
        }
        float scale = targetMinEdge / (float) minEdge;
        int outW = Math.max(1, Math.round(w * scale));
        int outH = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(source, outW, outH, true);
    }

    private Bitmap fastBlur(Bitmap source, int radius) {
        if (radius < 1) {
            return source;
        }
        Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888, true);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum;
        int gsum;
        int bsum;
        int x;
        int y;
        int i;
        int p;
        int yp;
        int yi;
        int yw;
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int routsum;
        int goutsum;
        int boutsum;
        int rinsum;
        int ginsum;
        int binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = radius + 1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }

        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = radius + 1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + radius + 1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }

    private void updatePlayPauseIcon() {
        if (musicPlayer.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void updateModeIcon() {
        MusicPlayer.PlayMode mode = musicPlayer.getPlayMode();
        if (mode == MusicPlayer.PlayMode.SINGLE_LOOP) {
            btnMode.setImageResource(R.drawable.ic_repeat_one);
        } else if (mode == MusicPlayer.PlayMode.SHUFFLE) {
            btnMode.setImageResource(R.drawable.ic_shuffle);
        } else {
            btnMode.setImageResource(R.drawable.ic_repeat);
        }
    }

    private void cyclePlayMode() {
        MusicPlayer.PlayMode mode = musicPlayer.getPlayMode();
        if (mode == MusicPlayer.PlayMode.SEQUENTIAL) {
            musicPlayer.setPlayMode(MusicPlayer.PlayMode.SINGLE_LOOP);
        } else if (mode == MusicPlayer.PlayMode.SINGLE_LOOP) {
            musicPlayer.setPlayMode(MusicPlayer.PlayMode.SHUFFLE);
        } else {
            musicPlayer.setPlayMode(MusicPlayer.PlayMode.SEQUENTIAL);
        }
        updateModeIcon();
    }

    private void togglePlayPause() {
        Song currentSong = musicPlayer.getCurrentSong();
        if (currentSong == null) {
            Toast.makeText(this, "暂无歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        if (musicPlayer.isPlaying()) {
            musicPlayer.pause();
        } else {
            if (musicPlayer.getPlayStatus() == PlayerStatus.STOPPED) {
                try {
                    musicPlayer.loadMusic(currentSong);
                } catch (IOException e) {
                    Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                musicPlayer.play();
            }
        }
        updatePlayPauseIcon();
    }

    private void playNext() {
        if (!ensurePlayQueueInitialized()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            musicPlayer.playNextInQueue();
        } catch (IOException e) {
            Toast.makeText(this, "切歌失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void playPrevious() {
        if (!ensurePlayQueueInitialized()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            musicPlayer.playPrevInQueue();
        } catch (IOException e) {
            Toast.makeText(this, "切歌失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean ensurePlayQueueInitialized() {
        if (musicPlayer.getPlayQueueSize() > 0) {
            return true;
        }
        Song currentSong = musicPlayer.getCurrentSong();
        if (currentSong == null) {
            return false;
        }
        String playlist = currentSong.getPlaylist();
        if (playlist == null || playlist.isEmpty()) {
            playlist = "默认歌单";
        }
        List<Map<String, Object>> songs = MusicLoader.loadSongs(this, playlist);
        if (songs.isEmpty()) {
            return false;
        }
        List<Song> queue = new ArrayList<>();
        int currentIndex = 0;
        for (int i = 0; i < songs.size(); i++) {
            Song s = Song.fromMap(songs.get(i));
            if (s.getPlaylist() == null || s.getPlaylist().isEmpty()) {
                Song fixed = new Song(s.getTimeDuration(), s.getName(), s.getFilePath(), playlist);
                fixed.setCoverUrl(s.getCoverUrl());
                fixed.setOnlineSongId(s.getOnlineSongId());
                s = fixed;
            }
            queue.add(s);
            if (s.equals(currentSong)) {
                currentIndex = i;
            }
        }
        musicPlayer.setPlayQueue(playlist, queue, currentIndex);
        return true;
    }

    private void showPlayQueueBottomSheet() {
        if (!ensurePlayQueueInitialized()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Song> queue = musicPlayer.getPlayQueueSnapshot();
        int currentIndex = musicPlayer.getQueueIndex();
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.bottom_sheet_play_queue, null);
        RecyclerView rv = content.findViewById(R.id.play_queue_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        PlayQueueAdapter adapter = new PlayQueueAdapter(queue, currentIndex, pos -> {
            if (pos == musicPlayer.getQueueIndex()) {
//                dialog.dismiss();
                return;
            }
            try {
                musicPlayer.playAtQueueIndex(pos);
            } catch (IOException e) {
                Toast.makeText(this, "切歌失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        rv.setAdapter(adapter);
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            rv.scrollToPosition(currentIndex);
        }
        dialog.setContentView(content);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                int h = getResources().getDisplayMetrics().heightPixels;
                ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
                lp.height = (int) (h * 0.66f);
                bottomSheet.setLayoutParams(lp);
            }
        });
        dialog.setOnDismissListener(d -> {
            if (playQueueDialog == dialog) {
                playQueueDialog = null;
                playQueueRv = null;
                playQueueAdapter = null;
            }
        });
        playQueueDialog = dialog;
        playQueueRv = rv;
        playQueueAdapter = adapter;
        dialog.show();
    }

    private void updatePlayQueueDialogIfShowing() {
        if (playQueueDialog == null || !playQueueDialog.isShowing() || playQueueRv == null || playQueueAdapter == null) {
            return;
        }
        List<Song> queue = musicPlayer.getPlayQueueSnapshot();
        int currentIndex = musicPlayer.getQueueIndex();
        playQueueAdapter.setData(queue);
        playQueueAdapter.setActiveIndex(currentIndex);
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            playQueueRv.scrollToPosition(currentIndex);
        }
    }

    private static final class PlayQueueAdapter extends RecyclerView.Adapter<PlayQueueAdapter.VH> {
        interface OnItemClick {
            void onClick(int position);
        }

        private final List<Song> data = new ArrayList<>();
        private int activeIndex;
        private final OnItemClick onItemClick;

        PlayQueueAdapter(List<Song> data, int activeIndex, OnItemClick onItemClick) {
            if (data != null) {
                this.data.addAll(data);
            }
            this.activeIndex = activeIndex;
            this.onItemClick = onItemClick;
        }

        void setActiveIndex(int index) {
            activeIndex = index;
            notifyDataSetChanged();
        }

        void setData(List<Song> newData) {
            data.clear();
            if (newData != null) {
                data.addAll(newData);
            }
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_play_queue, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Song s = data.get(position);
            String fullName = s != null ? s.getName() : "";
            String songName = fullName;
            String artistName = "";
            int idx = fullName != null ? fullName.indexOf(" - ") : -1;
            if (idx > 0) {
                artistName = fullName.substring(0, idx).trim();
                songName = fullName.substring(idx + 3).trim();
            }
            holder.tvSong.setText(songName != null ? songName : "");
            holder.tvArtist.setText(artistName);
            holder.itemView.setActivated(position == activeIndex);
            holder.itemView.setOnClickListener(v -> {
                if (onItemClick != null) {
                    onItemClick.onClick(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView tvSong;
            final TextView tvArtist;

            VH(View itemView) {
                super(itemView);
                tvSong = itemView.findViewById(R.id.queue_song_name);
                tvArtist = itemView.findViewById(R.id.queue_artist_name);
            }
        }
    }

    private void setBouncyClick(ImageButton button, Runnable action) {
        if (button == null) {
            return;
        }
        button.setOnClickListener(v -> {
            v.animate().cancel();
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(80)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(220)
                            .setInterpolator(new OvershootInterpolator())
                            .start())
                    .start();
            if (action != null) {
                action.run();
            }
        });
    }

    private String formatTime(int ms) {
        if (ms <= 0) {
            return "00:00";
        }
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
