package com.vova9110.bloodbath.alarmScreenBackground

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

fun returnPlayer(context: Context, info: SubInfo): MediaPlayer{
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    return MediaPlayer().apply {
        setDataSource(context, info.soundPath!!)
        setAudioAttributes(attributes)
        isLooping = true
        prepare()
    }
}