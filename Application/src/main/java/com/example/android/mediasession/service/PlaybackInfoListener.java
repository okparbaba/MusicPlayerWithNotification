package com.example.android.mediasession.service;

import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.example.android.mediasession.service.players.MediaPlayerAdapter;

public abstract class PlaybackInfoListener {

    public abstract void onPlaybackStateChange(PlaybackStateCompat state);

    public void onPlaybackCompleted() {
    }
}