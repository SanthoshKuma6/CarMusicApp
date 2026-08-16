package com.sk.carmusicapp.activity

import android.annotation.SuppressLint
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sk.carmusicapp.R
import com.sk.carmusicapp.adapter.MyAdapter
import com.sk.carmusicapp.api.Response
import com.sk.carmusicapp.databinding.ActivityMainBinding
import com.sk.carmusicapp.model.MyMusic
import com.sk.carmusicapp.service.PlaybackService
import com.sk.carmusicapp.viewmodel.MusicViewModel
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), MyAdapter.ItemClickListener {

    private val mainActivity by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val musicViewModel: MusicViewModel by viewModels()
    private var myAdapter: MyAdapter? = null
    private lateinit var imageView: ImageView
    private lateinit var songTitle: TextView
    private lateinit var singerName: TextView
    private lateinit var seekBar: SeekBar
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val musicList: ArrayList<MyMusic.Data> = ArrayList()
    private var playPosition: Int = 0
    private lateinit var timerTextView: TextView
    private var songId: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var nextSongPosition: Int = 0
    private var previousSongPosition: Int = 0

    companion object {
        private const val PREF_NAME = "MusicAppPrefs"
        private const val KEY_LAST_SONG_ID = "last_song_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mainActivity.root)
        musicViewModel.mutableLiveDataResponse.observe(this, musicListObserver)
        initializeViews()
        initializeListeners()
        apiCalMusicList()
        Log.d("TAG", "onCreate: $songId")

        mainActivity.backBtn.setOnClickListener { onBackPressed() }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            setupSeekBar()
            updateUIFromController()
            controller?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateUIFromController()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    mainActivity.ivPlay.setImageResource(
                        if (isPlaying) R.drawable.btn_pause else R.drawable.btn_play
                    )
                }
            })
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    private fun updateUIFromController() {
        val player = controller ?: return
        val currentMediaItem = player.currentMediaItem ?: return
        val metadata = currentMediaItem.mediaMetadata
        
        mainActivity.tvSongTitle.text = metadata.title
        mainActivity.tvSingerName.text = metadata.artist
        Picasso.get().load(metadata.artworkUri).into(mainActivity.imageViewControl)
        
        // Update highlight in list
        val currentId = currentMediaItem.mediaId.toLongOrNull() ?: -1L
        val position = musicList.indexOfFirst { it.id == currentId }
        if (position != -1) {
            playPosition = position
            myAdapter = MyAdapter(this@MainActivity, musicList, this, position)
            mainActivity.recyclerview.adapter = myAdapter
            mainActivity.recyclerview.scrollToPosition(position)
        }
    }

    private fun initializeViews() {
        songTitle = findViewById(R.id.tv_song_title)
        singerName = findViewById(R.id.tv_singer_name)
        imageView = findViewById(R.id.imageViewControl)
        seekBar = findViewById(R.id.seekBar)
        timerTextView = findViewById(R.id.tvTimePost)

    }

    private fun initializeListeners() {
        mainActivity.ivNext.setOnClickListener {
            playNextSong()
        }
        mainActivity.ivPre.setOnClickListener {
            playPreviousSong()

        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun playNextSong() {
        controller?.seekToNext()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun playPreviousSong() {
        controller?.seekToPrevious()
    }

    private fun apiCalMusicList() {
        lifecycleScope.launch {
            musicViewModel.getMusicList("Padayappa")
//            musicViewModel.getMusicList("eminem")
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    private val musicListObserver = Observer<Response<MyMusic>> { response ->
        when (response) {
            is Response.Success -> {
                musicList.clear()
                musicList.addAll(response.data!!.data)
                
                // Add songs to controller playlist
                controller?.let { player ->
                    player.clearMediaItems()
                    val mediaItems = musicList.map { song ->
                        MediaItem.Builder()
                            .setMediaId(song.id.toString())
                            .setUri(song.preview)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist.name)
                                    .setArtworkUri(android.net.Uri.parse(song.album.cover))
                                    .build()
                            )
                            .build()
                    }
                    player.setMediaItems(mediaItems)
                    player.prepare()
                }

                // Restore last played song state
                val sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                val lastSongId = sharedPreferences.getLong(KEY_LAST_SONG_ID, -1L)
                
                var initialPosition = 0
                if (lastSongId != -1L) {
                    val lastSongIndex = musicList.indexOfFirst { it.id == lastSongId }
                    if (lastSongIndex != -1) {
                        initialPosition = lastSongIndex
                        musicList[initialPosition].isSelected = true
                        playPosition = initialPosition
                        
                        // Update UI to show last played song info
                        val song = musicList[initialPosition]
                        mainActivity.tvSongTitle.text = song.title
                        mainActivity.tvSingerName.text = song.artist.name
                        Picasso.get().load(song.album.cover).into(mainActivity.imageViewControl)
                    }
                }

                myAdapter = MyAdapter(this@MainActivity, musicList, this, initialPosition)
                mainActivity.recyclerview.adapter = myAdapter
                myAdapter?.notifyDataSetChanged()
                mainActivity.recyclerview.scrollToPosition(initialPosition)
            }

            is Response.Error -> {
                Log.d("TAG", "Error: ${response.errorMessage}")
            }

            is Response.Loading -> {
                if (response.showLoader == true) {
                    mainActivity.progress.visibility = View.VISIBLE
                } else {
                    mainActivity.progress.visibility = View.GONE
                }
                Log.d("TAG", "Loading: ${response.showLoader}")
            }
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    override fun onItemClick(item: MyMusic.Data, position: Int) {
        controller?.let { player ->
            player.seekTo(position, 0)
            player.play()
        }

        // Save last played song ID
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SONG_ID, item.id)
            .apply()
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    controller?.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        handler.post(object : Runnable {
            override fun run() {
                controller?.let { player ->
                    val currentPosition = player.currentPosition
                    val duration = player.duration
                    
                    seekBar.max = if (duration > 0) duration.toInt() else 100
                    seekBar.progress = currentPosition.toInt()

                    val minutes = currentPosition / 1000 / 60
                    val seconds = (currentPosition / 1000) % 60
                    mainActivity.tvTimePost.text = String.format("%02d:%02d", minutes, seconds)

                    val totalMinutes = duration / 1000 / 60
                    val totalSeconds = (duration / 1000) % 60
                    mainActivity.tvTimePre.text = String.format("%02d:%02d", totalMinutes, totalSeconds)
                }
                handler.postDelayed(this, 1000)
            }
        })
        
        mainActivity.ivPlay.setOnClickListener {
            controller?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onBackPressed() {
        // Remove recursive call
        super.onBackPressed()
    }


}

