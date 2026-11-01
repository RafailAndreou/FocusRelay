// Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.alarms

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.net.Uri
import android.os.*
import com.mindful.android.R
import androidx.core.app.NotificationCompat

/** Minimal bounded alarm playback with an always-visible dismiss action; no snooze or full-screen UI. */
class WakeAlarmService: Service(){
    companion object {
        @Volatile private var active: WakeAlarmService?=null
        fun isRinging(id: String)=active?.let{it.alarmId==id && runCatching{it.player?.isPlaying==true}.getOrDefault(false)}?:false
        fun stopIfRinging(c: Context,id: String){val s=active;if(s?.alarmId==id)s.handler.post{s.stopSelf()}}
    }
    val handler=Handler(Looper.getMainLooper())
    private var player: MediaPlayer?=null
    private var focus: AudioFocusRequest?=null
    private var alarmId: String?=null
    private val listener=AudioManager.OnAudioFocusChangeListener{change->if(change<0){alarmId?.let{OwnedAlarms.mark(this,it,"failed","Alarm interrupted by audio focus loss")};stopSelf()}}
    override fun onBind(i: Intent?)=null
    override fun onStartCommand(i: Intent?,flags: Int,startId: Int): Int {
        val id=i?.getStringExtra("id")?:return START_NOT_STICKY.also{stopSelf()}
        val a=OwnedAlarms.get(this,id)?:return START_NOT_STICKY.also{stopSelf()}
        if(alarmId==id)return START_NOT_STICKY
        if(!OwnedAlarms.enabled(this)||!OwnedAlarmPolicy.mayRing(a.optString("state"),a.getLong("triggerAtMs"),System.currentTimeMillis()))return START_NOT_STICKY.also{if(alarmId==null)stopSelf()}
        // Overlapping alarms share one player; never leave a superseded notification ringing.
        alarmId?.let{OwnedAlarms.mark(this,it,"dismissed","Superseded by another alarm")};releaseAudio()
        alarmId=id;active=this;OwnedAlarms.channel(this)
        val dismiss=PendingIntent.getBroadcast(this,0,Intent(this,OwnedAlarmReceiver::class.java).setAction("DISMISS").setData(Uri.parse("focusrelay://alarm/"+id)),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=NotificationCompat.Builder(this,OwnedAlarms.CHANNEL).setSmallIcon(R.drawable.ic_focus_mode).setContentTitle(a.getString("label"))
            .setContentText("FocusRelay alarm · Tap Dismiss to stop").setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true).setContentIntent(OwnedAlarms.show(this)).addAction(0,"Dismiss",dismiss).build()
        try{
            if(Build.VERSION.SDK_INT>=29)startForeground(9321,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)else startForeground(9321,n)
            val audio=getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val granted=if(Build.VERSION.SDK_INT>=26){val req=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(listener).build();focus=req;audio.requestAudioFocus(req)}else audio.requestAudioFocus(listener,AudioManager.STREAM_ALARM,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            check(granted==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Alarm audio focus unavailable"}
            val uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?:RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            check(uri!=null){"No alarm sound configured"}
            player=MediaPlayer().apply{setAudioAttributes(attrs);setDataSource(this@WakeAlarmService,uri);isLooping=true;setWakeMode(this@WakeAlarmService,PowerManager.PARTIAL_WAKE_LOCK);prepare();start()}
            OwnedAlarms.mark(this,id,"ringing")
            handler.removeCallbacksAndMessages(null);handler.postDelayed({OwnedAlarms.mark(this,id,"missed","Stopped after ten minutes");stopSelf()},10*60000L)
        }catch(e:Exception){OwnedAlarms.mark(this,id,"failed",e.message?:"Alarm playback failed");stopSelf()}
        return START_NOT_STICKY
    }
    private fun releaseAudio(){runCatching{player?.release()};player=null;val audio=getSystemService(Context.AUDIO_SERVICE) as AudioManager;if(Build.VERSION.SDK_INT>=26)focus?.let{audio.abandonAudioFocusRequest(it)}else audio.abandonAudioFocus(listener);focus=null}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);releaseAudio();alarmId?.let{if(OwnedAlarms.get(this,it)?.optString("state")=="ringing")OwnedAlarms.mark(this,it,"missed","Playback stopped")};if(active===this)active=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}
