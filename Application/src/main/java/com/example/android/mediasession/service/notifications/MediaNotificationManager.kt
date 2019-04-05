package com.example.android.mediasession.service.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.os.BuildCompat
import android.util.Log

import com.example.android.mediasession.R
import com.example.android.mediasession.service.MusicService
import com.example.android.mediasession.service.PlaybackInfoListener
import com.example.android.mediasession.service.contentcatalogs.MusicLibrary
import com.example.android.mediasession.ui.MainActivity


/**
 * Keeps track of a notification and updates it automatically for a given MediaSession. This is
 * required so that the music service don't get killed during playback.
 */
class MediaNotificationManager(private val mService: MusicService) {

    private val mPlayAction: NotificationCompat.Action
    private val mPauseAction: NotificationCompat.Action
    private val mNextAction: NotificationCompat.Action
    private val mPrevAction: NotificationCompat.Action
    val notificationManager: NotificationManager?

    private val isAndroidOOrHigher: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    init {

        notificationManager = mService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        mPlayAction = NotificationCompat.Action(
                R.drawable.ic_play_arrow_white_24dp,
                mService.getString(R.string.label_play),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_PLAY))
        mPauseAction = NotificationCompat.Action(
                R.drawable.ic_pause_white_24dp,
                mService.getString(R.string.label_pause),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_PAUSE))
        mNextAction = NotificationCompat.Action(
                R.drawable.ic_skip_next_white_24dp,
                mService.getString(R.string.label_next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
        mPrevAction = NotificationCompat.Action(
                R.drawable.ic_skip_previous_white_24dp,
                mService.getString(R.string.label_previous),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        assert(notificationManager != null)
        notificationManager.cancelAll()
    }

    fun onDestroy() {
        Log.d(TAG, "onDestroy: ")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getNotification(metadata: MediaMetadataCompat,
                        state: PlaybackStateCompat,
                        token: MediaSessionCompat.Token): Notification {
        val isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
        val description = metadata.description
        val builder = buildNotification(state, token, isPlaying, description)
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildNotification(state: PlaybackStateCompat,
                                  token: MediaSessionCompat.Token,
                                  isPlaying: Boolean,
                                  description: MediaDescriptionCompat): NotificationCompat.Builder {

        if (isAndroidOOrHigher) {
            createChannel()
        }

        val builder = NotificationCompat.Builder(mService, CHANNEL_ID)
        builder.setStyle(
                MediaStyle()
                        .setMediaSession(token)
                        .setShowActionsInCompactView(0, 1, 2)
                        // For backwards compatibility with Android L and earlier.
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(
                                MediaButtonReceiver.buildMediaButtonPendingIntent(
                                        mService,
                                        PlaybackStateCompat.ACTION_STOP)))
                .setColor(ContextCompat.getColor(mService, R.color.notification_bg))
                .setSmallIcon(R.drawable.ic_stat_image_audiotrack)
                // Pending intent that is fired when user clicks on notification.
                .setContentIntent(createContentIntent())
                // Title - Usually Song name.
                .setContentTitle(description.title)
                // Subtitle - Usually Artist name.
                .setContentText(description.subtitle)
                .setLargeIcon(MusicLibrary.getAlbumBitmap(mService, description.mediaId!!))
                // When notification is deleted (when playback is paused and notification can be
                // deleted) fire MediaButtonPendingIntent with ACTION_STOP.
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService, PlaybackStateCompat.ACTION_STOP))
                // Show controls on lock screen even when user hides sensitive content.
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // If skip to next action is enabled.
        if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L) {
            builder.addAction(mPrevAction)
        }

        builder.addAction(if (isPlaying) mPauseAction else mPlayAction)

        // If skip to prev action is enabled.
        if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L) {
            builder.addAction(mNextAction)
        }

        return builder
    }

    // Does nothing on versions of Android earlier than O.
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        if (notificationManager!!.getNotificationChannel(CHANNEL_ID) == null) {
            // The user-visible name of the channel.
            val name = "MediaSession"
            // The user-visible description of the channel.
            val description = "MediaSession and MediaPlayer"
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            // Configure the notification channel.
            mChannel.description = description
            mChannel.enableLights(true)
            // Sets the notification light color for notifications posted to this
            // channel, if the device supports this feature.
            mChannel.lightColor = Color.RED
            mChannel.enableVibration(true)
            mChannel.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            notificationManager.createNotificationChannel(mChannel)
            Log.d(TAG, "createChannel: New channel created")
        } else {
            Log.d(TAG, "createChannel: Existing channel reused")
        }
    }

    private fun createContentIntent(): PendingIntent {
        val openUI = Intent(mService, MainActivity::class.java)
        openUI.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
                mService, REQUEST_CODE, openUI, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    companion object {

        val NOTIFICATION_ID = 412

        private val TAG = MediaNotificationManager::class.java!!.getSimpleName()
        private val CHANNEL_ID = "com.example.android.musicplayer.channel"
        private val REQUEST_CODE = 501
    }

}