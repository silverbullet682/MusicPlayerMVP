package com.sildev.musicplayer.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.sildev.musicplayer.*
import com.sildev.musicplayer.MusicPlayerHelper.getBitmapSong
import com.sildev.musicplayer.adapter.SongAdapter
import com.sildev.musicplayer.databinding.ActivityMainBinding
import com.sildev.musicplayer.model.Song
import com.sildev.musicplayer.presenter.MainContract
import com.sildev.musicplayer.presenter.MainPresenter
import com.sildev.musicplayer.service.PlaySongService
import java.util.*

class MainActivity : AppCompatActivity(), MainContract.View, SearchView.OnQueryTextListener {
    private var backPressTime: Long = 0
    private lateinit var controlMusicBottomSheetDialog: ControlMusicBottomSheetDialog
    private lateinit var songAdapter: SongAdapter
    private lateinit var songSearchView: SearchView
    private val mainBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(
            layoutInflater
        )
    }

    private var currentPlayList = mutableListOf<Song>()
    private var currentPositionSong: Int = -1
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private val mainPresenter: MainPresenter = MainPresenter(this)

    private val musicReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY -> {
                    playSong()
                }
                ACTION_NEXT -> {
                    nextSong()
                }
                ACTION_PREVIOUS -> {
                    previousSong()
                }
                ACTION_PAUSE -> {
                    pauseOrResume()
                    setImagePlayResource()
                }
            }

        }
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_PREVIOUS)
        intentFilter.addAction(ACTION_PLAY)
        intentFilter.addAction(ACTION_PAUSE)
        intentFilter.addAction(ACTION_NEXT)
        registerReceiver(musicReceiver, intentFilter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mainBinding.root)
        initUI()
        songAdapter = SongAdapter(::onClickItem)
        mainBinding.recyclerviewSong.adapter = songAdapter
        mainPresenter.loadDataToSongList(this)
        setOnCLickView()

    }

    private fun setOnCLickView() {
        mainBinding.imagePauseResume.setOnClickListener {
            sendPauseResumeBroadcast()
        }

        mainBinding.layoutPlayer.setOnClickListener {
            controlMusicBottomSheetDialog.show()
            controlMusicBottomSheetDialog.updateSong(currentPlayList[currentPositionSong])
            controlMusicBottomSheetDialog.updateCurrentTime(mediaPlayer.currentPosition)
        }
        mainBinding.imagePrevious.setOnClickListener {
            previousSong()
        }
        mainBinding.imageNext.setOnClickListener {
            nextSong()
        }
        mainBinding.toolbarSong.setOnMenuItemClickListener {
            if (it.itemId == R.id.item_search) {
                songSearchView = it.actionView as SearchView
                songSearchView.setOnQueryTextListener(this)
            }
            true
        }

    }


    private fun setImagePlayResource() {
        val icon = if (mediaPlayer.isPlaying) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        }
        controlMusicBottomSheetDialog.setIconPauseResume(icon)
        mainBinding.imagePauseResume.setImageResource(icon)
    }

    private fun initUI() {
        controlMusicBottomSheetDialog = ControlMusicBottomSheetDialog(this, mainPresenter)
    }

    private fun nextSong() {
        if (currentPositionSong == currentPlayList.size - 1) {
            currentPositionSong = 0
        } else {
            currentPositionSong++
        }
        if (mainPresenter.isShuffle()) {
            currentPositionSong = Random().nextInt(currentPlayList.size)
        }
        sendPlayBroadcast()
    }

    private fun previousSong() {
        if (currentPositionSong == 0) {
            currentPositionSong = currentPlayList.size - 1
        } else {
            currentPositionSong--
        }
        if (mainPresenter.isShuffle()) {
            currentPositionSong = Random().nextInt(currentPlayList.size)
        }
        sendPlayBroadcast()
    }


    private fun sendPauseResumeBroadcast() {
        val intentPauseResume = Intent()
        intentPauseResume.action = ACTION_PAUSE
        sendBroadcast(intentPauseResume)
    }

    fun playSong() {
        val song = currentPlayList[currentPositionSong]
        mediaPlayer.reset()
        try {
            mediaPlayer.setDataSource(song.path)
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (e: java.lang.Exception) {
            nextSong()
        }
        mainPresenter.setPlaying(true)
        showMiniPlayer()
        updatePlayer(song)
        setOnCompletePlaySong()
        controlMusicBottomSheetDialog.mediaPlayer = mediaPlayer
    }

    private fun updatePlayer(song: Song) {
        Glide.with(this).load(MusicPlayerHelper.getBitmapSong(song.path))
            .placeholder(R.drawable.ic_music).into(mainBinding.imageSong)
        mainBinding.apply {
            textSinger.text = song.singer
            textTitle.text = song.name
        }

        setImagePlayResource()

        controlMusicBottomSheetDialog.updateSong(song)
        val handlerUpdateSongTime = Handler()
        handlerUpdateSongTime.postDelayed(object : Runnable {
            override fun run() {
                controlMusicBottomSheetDialog.updateCurrentTime(mediaPlayer.currentPosition)
                handlerUpdateSongTime.postDelayed(this, DELAY_UPDATE_TIME.toLong())
            }

        }, 1)

    }

    private fun pauseOrResume() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            mainPresenter.setPlaying(false)
        } else {
            mediaPlayer.start()
            mainPresenter.setPlaying(true)
        }
        setImagePlayResource()

    }

    private fun sendPlayBroadcast() {
        val song: Song = currentPlayList[currentPositionSong]
        val intentReceiver = Intent(ACTION_PLAY)
        intentReceiver.putExtra("currentSong", song)
        sendBroadcast(intentReceiver)
    }

    private fun setOnCompletePlaySong() {
        mediaPlayer.setOnCompletionListener {
            if (mainPresenter.isRepeat()) {
                sendPlayBroadcast()
            } else {
                nextSong()
            }
        }
    }

    override fun showMiniPlayer() {
        mainBinding.layoutPlayer.isVisible = true
    }

    override fun hideMiniPlayer() {
        mainBinding.layoutPlayer.isVisible = false
    }

    override fun showListSong(list: MutableList<Song>) {
        songAdapter.setDataToList(list)
        currentPlayList = list
    }

    fun onClickItem(position: Int) {
        currentPositionSong = position
        val currentSong = currentPlayList[currentPositionSong]
        val intentService = Intent(this, PlaySongService::class.java)
        intentService.putExtra("currentSong", currentSong)
        startService(intentService)
        sendPlayBroadcast()
        mainPresenter.setPositionSong(position)
    }

    override fun onResume() {
        super.onResume()
        mainPresenter.showOrHideMiniPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(musicReceiver)
        mainPresenter.setPlaying(false)
        mainPresenter.setPositionSong(-1)
        mediaPlayer.stop()
        mediaPlayer.release()
        stopService(Intent(this, PlaySongService::class.java))
    }

    override fun onBackPressed() {
        if (backPressTime + BACK_PRESS_DELAY_TIME > System.currentTimeMillis()) {
            super.onBackPressed()
            return
        } else {
            Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()
        }
        backPressTime = System.currentTimeMillis()

    }

    override fun onQueryTextSubmit(query: String): Boolean {
        songAdapter.searchSong(query.trim())
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        songAdapter.searchSong(newText.trim())
        return true

    }
}

