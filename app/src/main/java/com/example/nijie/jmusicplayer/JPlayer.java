package com.example.nijie.jmusicplayer;

/**
 * Created by nijie on 9/1/15.
 */
import java.io.IOException;
import java.nio.ByteBuffer;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;

public class JPlayer implements Runnable {
    public final String LOG_TAG = "JPlayer";

    private Context mContext;

    private MediaExtractor mExtractor;
    private MediaCodec mCodec;
    private AudioTrack mAudioTrack;
    private MediaFormat mFormat = null;

    private PlayerEvents mEvents = null;
    private PlayerStates mState = new PlayerStates();

    private String mSourcePath = null;
    private int sourceRawResId = -1;

    private boolean stop = false;

    Handler mHandler = new Handler();

    String mime = null;
    int sampleRate = 0, channels = 0, bitrate = 0;
    long presentationTimeUs = 0, duration = 0;

    public void setEventsListener(PlayerEvents events) {
        this.mEvents = events;
    }

    public JPlayer() {

    }
    public JPlayer(PlayerEvents events) {
        setEventsListener(events);
    }

    /**
     * For live streams, duration is 0
     * @return
     */
    public boolean isLive() {
        return (duration == 0);
    }

    /**
     * set the data source, a file path or an url, or a file descriptor, to play encoded audio from
     * @param src
     */
    public void setDataSource(String src) {
        mSourcePath = src;
    }

    public void setDataSource(Context context, int resid) {
        mContext = context;
        sourceRawResId = resid;
    }

    public void synchronousPlay() {
        if (mState.get() == PlayerStates.STOPPED) {
            stop = false;
            new Thread(this).start();
        }
        if (mState.get() == PlayerStates.READY_TO_PLAY) {
            mState.set(PlayerStates.PLAYING);
            syncNotify();
        }
    }

    public void asynchronousPlay(){
        if (mState.get() == PlayerStates.STOPPED) {
            stop = false;
            asyncStart();
        }
        if (mState.get() == PlayerStates.READY_TO_PLAY) {
            mState.set(PlayerStates.PLAYING);
            syncNotify();
        }
    }

    /**
     * Call notify to control the PAUSE (waiting) state, when the state is changed
     */
    public synchronized void syncNotify() {
        notify();
    }
    public void stop() {
        stop = true;
    }

    public void pause() {
        mState.set(PlayerStates.READY_TO_PLAY);
    }

    public void seek(long pos) {
        mExtractor.seekTo(pos, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    }

    public void seek(int percent) {
        long pos = percent * duration / 100;
        seek(pos);
    }


    /**
     * A pause mechanism that would block current thread when pause flag is set (READY_TO_PLAY)
     */
    public synchronized void waitPlay(){
        // if (duration == 0) return;
        while(mState.get() == PlayerStates.READY_TO_PLAY) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void asyncStart(){
        // extractor gets information about the stream
        mExtractor = new MediaExtractor();
        // try to set the source, this might fail
        try {
            if (mSourcePath != null) mExtractor.setDataSource(this.mSourcePath);
            if (sourceRawResId != -1) {
                AssetFileDescriptor fd = mContext.getResources().openRawResourceFd(sourceRawResId);
                mExtractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getDeclaredLength());
                fd.close();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "exception:" + e.getMessage());
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onError();  } });
            return;
        }

        // Read track header

        try {
            mFormat = mExtractor.getTrackFormat(0);
            mime = mFormat.getString(MediaFormat.KEY_MIME);
            sampleRate = mFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = mFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            // if duration is 0, we are probably playing a live stream
            duration = mFormat.getLong(MediaFormat.KEY_DURATION);
            bitrate = mFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Reading format parameters exception:"+e.getMessage());
            // don't exit, tolerate this error, we'll fail later if this is critical
        }
        Log.d(LOG_TAG, "Track info: mime:" + mime + " sampleRate:" + sampleRate + " channels:" + channels + " bitrate:" + bitrate + " duration:" + duration);

        // check we have audio content we know
        if (mFormat == null || !mime.startsWith("audio/")) {
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onError();  } });
            return;
        }
        // create the actual decoder, using the mime to select
        try{
            mCodec = MediaCodec.createDecoderByType(mime);
        }catch (IOException error){
            Log.e("LOG_TAG", "Create decoder error: " + error);
            error.printStackTrace();
            return;
        }

        // check we have a valid codec instance
        if (mCodec == null) {
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onError();  } });
            return;
        }

        //mState.set(PlayerStates.READY_TO_PLAY);
        if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onStart(mime, sampleRate, channels, duration);  } });


        //NJ since lollipop, it is preferred to use asynchronous handing of codec class

        mCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec mc, int inputBufferId) {
                ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferId);
                //NJ: fill inputBuffer with valid data
                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                boolean sawInputEOS = false;
                if (sampleSize < 0) {
                    Log.d(LOG_TAG, "saw input EOS. Stopping playback");
                    sawInputEOS = true;
                    sampleSize = 0;
                } else {
                    sawInputEOS = false;
                    presentationTimeUs = mExtractor.getSampleTime();
                    final int percent =  (duration == 0)? 0 : (int) (100 * presentationTimeUs / duration);
                    if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onPlayUpdate(percent, presentationTimeUs / 1000, duration / 1000);  } });
                }
                mCodec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                if (!sawInputEOS) mExtractor.advance();

                //mCodec.queueInputBuffer(inputBufferId, …);
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec mc, int outputBufferId, MediaCodec.BufferInfo info) {
                ByteBuffer outputBuffer = mCodec.getOutputBuffer(outputBufferId);
                MediaFormat bufferFormat = mCodec.getOutputFormat(outputBufferId); // option A
                // bufferFormat is equivalent to mOutputFormat
                // outputBuffer is ready to be processed or rendered.
                //NJ: write to audiotrack

                final byte[] chunk = new byte[info.size];
                outputBuffer.get(chunk);
                outputBuffer.clear();
                if(chunk.length > 0){
                    mAudioTrack.write(chunk,0,chunk.length);
                	/*if(this.mState.get() != PlayerStates.PLAYING) {
                		if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onPlay();  } });
            			mState.set(PlayerStates.PLAYING);
                	}*/

                }
                mCodec.releaseOutputBuffer(outputBufferId,false);
            }

            @Override
            public void onOutputFormatChanged(MediaCodec mc, MediaFormat format) {
                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
                mFormat = format;

            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {

            }
        });

        mCodec.configure(mFormat, null, null, 0);

        // configure AudioTrack
        int channelConfiguration = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minSize = AudioTrack.getMinBufferSize( sampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfiguration,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);

        // start playing, we will feed the AudioTrack later
        mAudioTrack.play();
        mExtractor.selectTrack(0);

        //NJ: once start, the callbacks will be called
        mCodec.start();





    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        // extractor gets information about the stream
        mExtractor = new MediaExtractor();
        // try to set the source, this might fail
        try {
            if (mSourcePath != null) mExtractor.setDataSource(this.mSourcePath);
            if (sourceRawResId != -1) {
                AssetFileDescriptor fd = mContext.getResources().openRawResourceFd(sourceRawResId);
                mExtractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getDeclaredLength());
                fd.close();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "exception:" + e.getMessage());
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onError();  } });
            return;
        }

        // Read track header
        MediaFormat format = null;
        try {
            format = mExtractor.getTrackFormat(0);
            mime = format.getString(MediaFormat.KEY_MIME);
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            // if duration is 0, we are probably playing a live stream
            duration = format.getLong(MediaFormat.KEY_DURATION);
            bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Reading format parameters exception:"+e.getMessage());
            // don't exit, tolerate this error, we'll fail later if this is critical
        }
        Log.d(LOG_TAG, "Track info: mime:" + mime + " sampleRate:" + sampleRate + " channels:" + channels + " bitrate:" + bitrate + " duration:" + duration);

        // check we have audio content we know
        if (format == null || !mime.startsWith("audio/")) {
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onError();  } });
            return;
        }
        // create the actual decoder, using the mime to select
        try{
            mCodec = MediaCodec.createDecoderByType(mime);
        }catch (IOException error){
            Log.e("LOG_TAG", "Create decoder error: " +error);
            error.printStackTrace();
            return;
        }

        // check we have a valid codec instance
        if (mCodec == null) {
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onError();  } });
            return;
        }

        //mState.set(PlayerStates.READY_TO_PLAY);
        if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onStart(mime, sampleRate, channels, duration);  } });

        mCodec.configure(format, null, null, 0);
        mCodec.start();
        ByteBuffer[] codecInputBuffers  = mCodec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = mCodec.getOutputBuffers();

        // configure AudioTrack
        int channelConfiguration = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minSize = AudioTrack.getMinBufferSize( sampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfiguration,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);

        // start playing, we will feed the AudioTrack later
        mAudioTrack.play();
        mExtractor.selectTrack(0);

        // start decoding
        final long kTimeOutUs = 1000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        int noOutputCounterLimit = 10;

        mState.set(PlayerStates.PLAYING);
        while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit && !stop) {

            // pause implementation
            waitPlay();

            noOutputCounter++;
            // read a buffer before feeding it to the decoder
            if (!sawInputEOS) {
                int inputBufIndex = mCodec.dequeueInputBuffer(kTimeOutUs);
                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    int sampleSize = mExtractor.readSampleData(dstBuf, 0);
                    if (sampleSize < 0) {
                        Log.d(LOG_TAG, "saw input EOS. Stopping playback");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = mExtractor.getSampleTime();
                        final int percent =  (duration == 0)? 0 : (int) (100 * presentationTimeUs / duration);
                        if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onPlayUpdate(percent, presentationTimeUs / 1000, duration / 1000);  } });
                    }

                    mCodec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) mExtractor.advance();

                } else {
                    Log.e(LOG_TAG, "inputBufIndex " +inputBufIndex);
                }
            } // !sawInputEOS

            // decode to PCM and push it to the AudioTrack player
            int res = mCodec.dequeueOutputBuffer(info, kTimeOutUs);

            if (res >= 0) {
                if (info.size > 0)  noOutputCounter = 0;

                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                final byte[] chunk = new byte[info.size];
                buf.get(chunk);
                buf.clear();
                if(chunk.length > 0){
                    mAudioTrack.write(chunk,0,chunk.length);
                	/*if(this.mState.get() != PlayerStates.PLAYING) {
                		if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onPlay();  } });
            			mState.set(PlayerStates.PLAYING);
                	}*/

                }
                mCodec.releaseOutputBuffer(outputBufIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(LOG_TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = mCodec.getOutputBuffers();
                Log.d(LOG_TAG, "output buffers have changed.");
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = mCodec.getOutputFormat();
                Log.d(LOG_TAG, "output format has changed to " + oformat);
            } else {
                Log.d(LOG_TAG, "dequeueOutputBuffer returned " + res);
            }
        }

        Log.d(LOG_TAG, "stopping...");

        if(mCodec != null) {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
        if(mAudioTrack != null) {
            mAudioTrack.flush();
            mAudioTrack.release();
            mAudioTrack = null;
        }

        // clear source and the other globals
        mSourcePath = null;
        sourceRawResId = -1;
        duration = 0;
        mime = null;
        sampleRate = 0; channels = 0; bitrate = 0;
        presentationTimeUs = 0; duration = 0;


        mState.set(PlayerStates.STOPPED);
        stop = true;

        if(noOutputCounter >= noOutputCounterLimit) {
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onError();  } });
        } else {
            if (mEvents != null) mHandler.post(new Runnable() { @Override public void run() { mEvents.onStop();  } });
        }
    }

    public static String listCodecs() {
        String results = "";
        //MediaCodecInfo[] supportedCodecs = MediaCodecList.getCodecInfos();
        //NJ it is called in a static context, and can only use getCodecCount method which is static
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            // grab results and put them in a list
            String name = codecInfo.getName();
            boolean isEncoder = codecInfo.isEncoder();

            if(isEncoder) continue; //NJ only display decoder

            String[] types = codecInfo.getSupportedTypes();
            String typeList = "";
            for (String s:types) typeList += s + " ";
            results += (i+1) + ". " + name+ " " + typeList + "\n\n";
        }
        return results;
    }


}
