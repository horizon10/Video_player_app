package com.example.kotlinvideo

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerView: PlayerView

    // Landscape controls
    private lateinit var playPauseButton: ImageButton
    private lateinit var speedButton: ImageButton
    private lateinit var lockButton: ImageButton
    private lateinit var rotationLockButton: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var topControls: LinearLayout
    private lateinit var bottomControls: LinearLayout

    // Portrait controls
    private lateinit var portraitControls: LinearLayout
    private lateinit var playPauseButtonPortrait: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var seekBarPortrait: SeekBar
    private lateinit var currentTimePortrait: TextView
    private lateinit var totalTimePortrait: TextView

    // Common elements
    private lateinit var brightnessText: TextView
    private lateinit var volumeText: TextView
    private lateinit var videoTitle: TextView
    private lateinit var skipForwardIndicator: TextView
    private lateinit var skipBackwardIndicator: TextView
    private lateinit var unlockButton: ImageButton
    private var skipForwardCount = 0
    private var skipBackwardCount = 0
    private val skipResetDelay = 1000L // 1 saniye
    private val skipHandler = Handler(Looper.getMainLooper())

    private var isRotationLocked = false
    private var isFullyLocked = false

    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private var isLocked = false
    private var currentPosition: Long = 0
    private var wasPlaying = true
    private val hideControlsRunnable = Runnable { hideControls() }

    // Track current orientation
    private var isLandscape = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        initializeViews()
        setupPlayer()
        setupControls()
        setupGestureDetector()
        hideSystemUI()

        // Check initial orientation
        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateControlsForOrientation()
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)

        // Landscape controls
        playPauseButton = findViewById(R.id.playPauseButton)
        speedButton = findViewById(R.id.speedButton)
        lockButton = findViewById(R.id.lockButton)
        rotationLockButton = findViewById(R.id.rotationLockButton)
        seekBar = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        topControls = findViewById(R.id.topControls)
        bottomControls = findViewById(R.id.bottomControls)

        // Portrait controls
        portraitControls = findViewById(R.id.portraitControls)
        playPauseButtonPortrait = findViewById(R.id.playPauseButtonPortrait)
        menuButton = findViewById(R.id.menuButton)
        seekBarPortrait = findViewById(R.id.seekBarPortrait)
        currentTimePortrait = findViewById(R.id.currentTimePortrait)
        totalTimePortrait = findViewById(R.id.totalTimePortrait)

        // Common elements
        brightnessText = findViewById(R.id.brightnessText)
        volumeText = findViewById(R.id.volumeText)
        videoTitle = findViewById(R.id.videoTitle)
        skipForwardIndicator = findViewById(R.id.skipForwardIndicator)
        skipBackwardIndicator = findViewById(R.id.skipBackwardIndicator)
        unlockButton = findViewById(R.id.unlockButton)
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer

        val videoUri: Uri? = intent.data ?: Uri.parse(intent.getStringExtra("videoUri"))
        if (videoUri != null) {
            val fileName = getFileName(videoUri) ?: "Video"
            videoTitle.text = fileName

            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()

            // Play/pause butonunu güncelle
            playPauseButton.setImageResource(R.drawable.ic_pause)
            playPauseButtonPortrait.setImageResource(R.drawable.ic_pause)

            // ExoPlayer hazır olduğunda toplam süreyi güncelle
            exoPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        updateTimeAndSeekbar()
                        playPauseButton.setImageResource(R.drawable.ic_pause)
                        playPauseButtonPortrait.setImageResource(R.drawable.ic_pause)
                    } else {
                        playPauseButton.setImageResource(R.drawable.ic_play)
                        playPauseButtonPortrait.setImageResource(R.drawable.ic_play)
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        updateTimeAndSeekbar()
                    }
                }
            })

            // Update seekbar and time
            handler.post(object : Runnable {
                override fun run() {
                    if (exoPlayer.isPlaying) {
                        updateTimeAndSeekbar()
                    }
                    handler.postDelayed(this, 1000)
                }
            })
        } else {
            Toast.makeText(this, "Video URI not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    private fun getFileName(uri: Uri): String? {
        return when {
            uri.scheme == "content" -> {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
                null
            }
            uri.scheme == "file" -> {
                File(uri.path!!).nameWithoutExtension
            }
            else -> {
                uri.path?.substringAfterLast('/') // Eğer yukarıdakiler başarısız olursa, dosya adını son / işaretinden sonra al
            }
        }
    }


    private fun setupControls() {
        // Landscape controls
        playPauseButton.setOnClickListener {
            togglePlayPause()
            showControls()
        }

        speedButton.setOnClickListener {
            showSpeedMenu(it)
        }

        lockButton.setOnClickListener {
            toggleFullLock()
            showControls()
        }

        rotationLockButton.setOnClickListener {
            toggleRotationLock()
            showControls()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer.seekTo(progress.toLong())
                    updateTimeAndSeekbar()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Portrait controls
        playPauseButtonPortrait.setOnClickListener {
            togglePlayPause()
            showControls()
        }

        menuButton.setOnClickListener {
            showPortraitOptionsMenu(it)
        }

        seekBarPortrait.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer.seekTo(progress.toLong())
                    updateTimeAndSeekbar()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        unlockButton.setOnClickListener {
            disableFullLock()
        }
    }

    private fun showPortraitOptionsMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.portrait_controls_menu, popupMenu.menu)

        // Update menu item icons based on current state
        popupMenu.menu.findItem(R.id.action_rotation_lock).setTitle(
            if (isRotationLocked) "Disable Rotation Lock" else "Enable Rotation Lock"
        )

        popupMenu.menu.findItem(R.id.action_lock).setTitle(
            if (isFullyLocked) "Unlock Controls" else "Lock Controls"
        )

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_speed -> showSpeedMenu(view)
                R.id.action_rotation_lock -> toggleRotationLock()
                R.id.action_lock -> toggleFullLock()
                else -> return@setOnMenuItemClickListener false
            }
            showControls()
            true
        }
        popupMenu.show()
    }

    private fun setupGestureDetector() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (!isFullyLocked) {
                    val screenWidth = playerView.width
                    when {
                        // Middle area double tap (middle 1/3 of the screen)
                        event.x in (screenWidth / 3.0)..(screenWidth * 2 / 3.0) -> {
                            togglePlayPause()
                        }
                        // Right side double tap
                        event.x > screenWidth * 2 / 3.0 -> {
                            skipForward()
                        }
                        // Left side double tap
                        else -> {
                            skipBackward()
                        }
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                if (isFullyLocked) {
                    // In fully locked mode, only show unlock button
                    toggleUnlockButton()
                } else {
                    toggleControls()
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || isLocked) return false

                val deltaY = e1.y - e2.y
                val deltaX = e1.x - e2.x

                if (abs(deltaX) < abs(deltaY)) {
                    if (e1.x > playerView.width / 2) {
                        changeVolume(deltaY)
                    } else {
                        changeBrightness(deltaY)
                    }
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateControlsForOrientation()
        showControls()
    }

    private fun updateControlsForOrientation() {
        if (isLandscape) {
            // Show landscape controls, hide portrait controls
            portraitControls.visibility = View.GONE
            if (controlsVisible && !isFullyLocked) {
                topControls.visibility = View.VISIBLE
                bottomControls.visibility = View.VISIBLE
            }
        } else {
            // Show portrait controls, hide landscape controls
            topControls.visibility = View.VISIBLE // Keep title visible
            bottomControls.visibility = View.GONE
            if (controlsVisible && !isFullyLocked) {
                portraitControls.visibility = View.VISIBLE
            } else {
                portraitControls.visibility = View.GONE
            }
        }

        // Update play/pause button state
        val isPlaying = exoPlayer.isPlaying
        playPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        playPauseButtonPortrait.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun toggleRotationLock() {
        isRotationLocked = !isRotationLocked
        if (isRotationLocked) {
            rotationLockButton.setImageResource(R.drawable.ic_rotation_lock)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            rotationLockButton.setImageResource(R.drawable.ic_screen_rotation)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    private fun toggleFullLock() {
        isFullyLocked = !isFullyLocked
        if (isFullyLocked) {
            lockButton.setImageResource(R.drawable.ic_locked)
            hideControls()
            // Show unlock button once in fully locked mode
            showUnlockButton()
        } else {
            disableFullLock()
        }
    }

    private fun disableFullLock() {
        isFullyLocked = false
        lockButton.setImageResource(R.drawable.ic_locked)
        unlockButton.visibility = View.GONE
        showControls()
    }

    private fun showUnlockButton() {
        unlockButton.visibility = View.VISIBLE
        handler.postDelayed({
            if (isFullyLocked) {
                unlockButton.visibility = View.GONE
            }
        }, 2000)
    }

    private fun toggleUnlockButton() {
        if (unlockButton.visibility == View.VISIBLE) {
            unlockButton.visibility = View.GONE
        } else {
            showUnlockButton()
        }
    }

    private fun showControls() {
        if (!isFullyLocked) {
            if (isLandscape) {
                topControls.visibility = View.VISIBLE
                bottomControls.visibility = View.VISIBLE
                portraitControls.visibility = View.GONE
            } else {
                topControls.visibility = View.VISIBLE
                bottomControls.visibility = View.GONE
                portraitControls.visibility = View.VISIBLE
            }

            controlsVisible = true
            handler.removeCallbacks(hideControlsRunnable)
            handler.postDelayed(hideControlsRunnable, 3000)
        }
    }

    private fun skipForward() {
        skipForwardCount += 10
        val newPosition = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
        exoPlayer.seekTo(newPosition)
        showSkipForwardIndicator(skipForwardCount)

        // Reset skip count after delay
        skipHandler.removeCallbacksAndMessages(null)
        skipHandler.postDelayed({
            skipForwardCount = 0
        }, skipResetDelay)
    }

    private fun skipBackward() {
        skipBackwardCount += 10
        val newPosition = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
        exoPlayer.seekTo(newPosition)
        showSkipBackwardIndicator(skipBackwardCount)

        // Reset skip count after delay
        skipHandler.removeCallbacksAndMessages(null)
        skipHandler.postDelayed({
            skipBackwardCount = 0
        }, skipResetDelay)
    }

    private fun showSkipForwardIndicator(totalSkip: Int) {
        skipForwardIndicator.text = "+$totalSkip s"
        skipForwardIndicator.visibility = View.VISIBLE
        handler.removeCallbacks { skipForwardIndicator.visibility = View.GONE }
        handler.postDelayed({ skipForwardIndicator.visibility = View.GONE }, 500)
    }

    private fun showSkipBackwardIndicator(totalSkip: Int) {
        skipBackwardIndicator.text = "-$totalSkip s"
        skipBackwardIndicator.visibility = View.VISIBLE
        handler.removeCallbacks { skipBackwardIndicator.visibility = View.GONE }
        handler.postDelayed({ skipBackwardIndicator.visibility = View.GONE }, 500)
    }

    private fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            playPauseButton.setImageResource(R.drawable.ic_play)
            playPauseButtonPortrait.setImageResource(R.drawable.ic_play)
        } else {
            exoPlayer.play()
            playPauseButton.setImageResource(R.drawable.ic_pause)
            playPauseButtonPortrait.setImageResource(R.drawable.ic_pause)
        }
    }

    private fun showSpeedMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.speed_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.speed_0_5x -> exoPlayer.setPlaybackSpeed(0.5f)
                R.id.speed_1x -> exoPlayer.setPlaybackSpeed(1.0f)
                R.id.speed_1_5x -> exoPlayer.setPlaybackSpeed(1.5f)
                R.id.speed_2x -> exoPlayer.setPlaybackSpeed(2.0f)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popupMenu.show()
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun hideControls() {
        topControls.visibility = View.GONE
        bottomControls.visibility = View.GONE
        portraitControls.visibility = View.GONE
        controlsVisible = false
    }

    private fun updateTimeAndSeekbar() {
        val duration = exoPlayer.duration
        val position = exoPlayer.currentPosition

        // Update landscape seekbar
        seekBar.max = duration.toInt()
        seekBar.progress = position.toInt()
        currentTime.text = formatDuration(position)
        totalTime.text = formatDuration(duration) // Toplam süreyi güncelle

        // Update portrait seekbar
        seekBarPortrait.max = duration.toInt()
        seekBarPortrait.progress = position.toInt()
        currentTimePortrait.text = formatDuration(position)
        totalTimePortrait.text = formatDuration(duration) // Toplam süreyi güncelle
    }

    private fun formatDuration(duration: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(duration)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun changeVolume(deltaY: Float) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // deltaY kontrolünü tersine çevir
        val volumeStep = if (deltaY > 0) 1 else -1

        val newVolume = (currentVolume + volumeStep).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

        // Ses göstergesini göster
        val volumePercent = (newVolume.toFloat() / maxVolume * 100).toInt()
        volumeText.text = "Volume: $volumePercent%"
        volumeText.visibility = View.VISIBLE
        handler.removeCallbacks { volumeText.visibility = View.GONE }
        handler.postDelayed({ volumeText.visibility = View.GONE }, 1500)
    }

    private fun changeBrightness(deltaY: Float) {
        val layoutParams = window.attributes
        val currentBrightness = if (layoutParams.screenBrightness < 0) 0.5f else layoutParams.screenBrightness

        // deltaY kontrolünü tersine çevir
        val brightnessStep = if (deltaY > 0) 0.05f else -0.05f
        val newBrightness = (currentBrightness + brightnessStep).coerceIn(0.1f, 1.0f)
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams

        // Parlaklık göstergesini göster
        val brightnessPercent = (newBrightness * 100).toInt()
        brightnessText.text = "Brightness: $brightnessPercent%"
        brightnessText.visibility = View.VISIBLE
        handler.removeCallbacks { brightnessText.visibility = View.GONE }
        handler.postDelayed({ brightnessText.visibility = View.GONE }, 1500)
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onPause() {
        super.onPause()
        wasPlaying = exoPlayer.isPlaying
        currentPosition = exoPlayer.currentPosition
        exoPlayer.pause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        exoPlayer.seekTo(currentPosition)
        if (wasPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        updateTimeAndSeekbar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("currentPosition", exoPlayer.currentPosition)
        outState.putBoolean("wasPlaying", wasPlaying)
        outState.putBoolean("isLocked", isLocked)
        outState.putBoolean("isRotationLocked", isRotationLocked)
        outState.putBoolean("isFullyLocked", isFullyLocked)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentPosition = savedInstanceState.getLong("currentPosition")
        wasPlaying = savedInstanceState.getBoolean("wasPlaying")
        isLocked = savedInstanceState.getBoolean("isLocked")
        isRotationLocked = savedInstanceState.getBoolean("isRotationLocked")
        isFullyLocked = savedInstanceState.getBoolean("isFullyLocked")

        exoPlayer.seekTo(currentPosition)
        if (wasPlaying) exoPlayer.play()

        if (isRotationLocked) {
            rotationLockButton.setImageResource(R.drawable.ic_rotation_lock)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }

        if (isFullyLocked) {
            lockButton.setImageResource(R.drawable.ic_locked)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        exoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}