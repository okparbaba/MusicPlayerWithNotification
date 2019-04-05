package com.example.android.mediasession.client

import android.content.ComponentName
import android.content.Context
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaControllerCompat.Callback
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import java.util.*

/**
 * Helper class for a MediaBrowser that handles connecting, disconnecting,
 * and basic browsing with simplified callbacks.
 */
open class MediaBrowserHelper(private val mContext: Context,
                              private val mMediaBrowserServiceClass: Class<out MediaBrowserServiceCompat>) {

    private val mCallbackList = ArrayList<Callback>()

    private val mMediaBrowserConnectionCallback: MediaBrowserConnectionCallback
    private val mMediaControllerCallback: MediaControllerCallback
    private val mMediaBrowserSubscriptionCallback: MediaBrowserSubscriptionCallback

    private var mMediaBrowser: MediaBrowserCompat? = null

    private var mMediaController: MediaControllerCompat? = null

    protected val mediaController: MediaControllerCompat
        get() {
            if (mMediaController == null) {
                throw IllegalStateException("MediaController is null!")
            }
            return mMediaController as MediaControllerCompat
        }

    val transportControls: MediaControllerCompat.TransportControls
        get() {
            if (mMediaController == null) {
                Log.d(TAG, "getTransportControls: MediaController is null!")
                throw IllegalStateException("MediaController is null!")
            }
            return mMediaController!!.transportControls
        }

    init {

        mMediaBrowserConnectionCallback = MediaBrowserConnectionCallback()
        mMediaControllerCallback = MediaControllerCallback()
        mMediaBrowserSubscriptionCallback = MediaBrowserSubscriptionCallback()
    }

    fun onStart() {
        if (mMediaBrowser == null) {
            mMediaBrowser = MediaBrowserCompat(
                    mContext,
                    ComponentName(mContext, mMediaBrowserServiceClass),
                    mMediaBrowserConnectionCallback, null)
            mMediaBrowser!!.connect()
        }
        Log.d(TAG, "onStart: Creating MediaBrowser, and connecting")
    }

    fun onStop() {
        if (mMediaController != null) {
            mMediaController!!.unregisterCallback(mMediaControllerCallback)
            mMediaController = null
        }
        if (mMediaBrowser != null && mMediaBrowser!!.isConnected) {
            mMediaBrowser!!.disconnect()
            mMediaBrowser = null
        }
        resetState()
        Log.d(TAG, "onStop: Releasing MediaController, Disconnecting from MediaBrowser")
    }

    protected open fun onConnected(mediaController: MediaControllerCompat) {}

    protected open fun onChildrenLoaded(parentId: String,
                                        children: List<MediaBrowserCompat.MediaItem>) {
    }

    protected fun onDisconnected() {}

    private fun resetState() {
//        performOnAllCallbacks{ callback ->
//            callback.onPlaybackStateChanged(null)
//        }
        Log.d(TAG, "resetState: ")
    }

    fun registerCallback(callback: Callback?) {
        if (callback != null) {
            mCallbackList.add(callback)

            // Update with the latest metadata/playback state.
            if (mMediaController != null) {
                val metadata = mMediaController!!.metadata
                if (metadata != null) {
                    callback.onMetadataChanged(metadata)
                }

                val playbackState = mMediaController!!.playbackState
                if (playbackState != null) {
                    callback.onPlaybackStateChanged(playbackState)
                }
            }
        }
    }

    private fun performOnAllCallbacks(command: CallbackCommand) {
        for (callback in mCallbackList) {
            if (callback != null) {
                command.perform(callback)
            }
        }
    }

    /**
     * Helper for more easily performing operations on all listening clients.
     */
    private interface CallbackCommand {
        fun perform(callback: Callback)
    }

    // Receives callbacks from the MediaBrowser when it has successfully connected to the
    // MediaBrowserService (MusicService).
    private inner class MediaBrowserConnectionCallback : MediaBrowserCompat.ConnectionCallback() {

        // Happens as a result of onStart().
        override fun onConnected() {
            try {
                // Get a MediaController for the MediaSession.
                mMediaController = MediaControllerCompat(mContext, mMediaBrowser!!.sessionToken)
                mMediaController!!.registerCallback(mMediaControllerCallback)

                // Sync existing MediaSession state to the UI.
                mMediaControllerCallback.onMetadataChanged(mMediaController!!.metadata)
                mMediaControllerCallback.onPlaybackStateChanged(
                        mMediaController!!.playbackState)

                this@MediaBrowserHelper.onConnected(mMediaController!!)
            } catch (e: RemoteException) {
                Log.d(TAG, String.format("onConnected: Problem: %s", e.toString()))
                throw RuntimeException(e)
            }

            mMediaBrowser!!.subscribe(mMediaBrowser!!.root, mMediaBrowserSubscriptionCallback)
        }
    }

    // Receives callbacks from the MediaBrowser when the MediaBrowserService has loaded new media
    // that is ready for playback.
    inner class MediaBrowserSubscriptionCallback : MediaBrowserCompat.SubscriptionCallback() {

        override fun onChildrenLoaded(parentId: String,
                                      children: List<MediaBrowserCompat.MediaItem>) {
            this@MediaBrowserHelper.onChildrenLoaded(parentId, children)
        }
    }

    // Receives callbacks from the MediaController and updates the UI state,
    // i.e.: Which is the current item, whether it's playing or paused, etc.
    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            performOnAllCallbacks(object : CallbackCommand {
                override fun perform(callback: Callback) {
                    callback.onMetadataChanged(metadata)
                }
            })
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            performOnAllCallbacks(object : CallbackCommand {
                override fun perform(callback: Callback) {
                    callback.onPlaybackStateChanged(state)
                }
            })
        }

        // This might happen if the MusicService is killed while the Activity is in the
        // foreground and onStart() has been called (but not onStop()).
        override fun onSessionDestroyed() {
            resetState()
            onPlaybackStateChanged(null)

            this@MediaBrowserHelper.onDisconnected()
        }
    }

    companion object {

        private val TAG = MediaBrowserHelper::class.java!!.getSimpleName()
    }
}