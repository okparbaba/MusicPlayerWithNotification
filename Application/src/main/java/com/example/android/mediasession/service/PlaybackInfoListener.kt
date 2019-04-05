package com.example.android.mediasession.service

import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.MediaSessionCompat

import com.example.android.mediasession.service.players.MediaPlayerAdapter

abstract class PlaybackInfoListener {

    abstract fun onPlaybackStateChange(state: PlaybackStateCompat)

    fun onPlaybackCompleted() {}
}