package com.vova9110.bloodbath.AlarmScreenBackground

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.vova9110.bloodbath.Database.TimeSInfo


fun returnPlayer(context: Context, info: TimeSInfo): MediaPlayer{
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    return MediaPlayer().apply {
        setDataSource(context, info.soundPath!!)
        setAudioAttributes(attributes)
        isLooping = true
        prepare()
    }
}