package com.mljsgto222.cordova.plugin.audiorecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xiph.vorbis.recorder.VorbisRecorder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * This class echoes a string called from JavaScript.
 */
public class AudioRecorder extends CordovaPlugin implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    private static final String TAG = AudioRecorder.class.getName();

    private static final String OUT_NUMBER_OF_CHANNELS = "outNumberOfChannels";
    private static final String OUT_ENCODE_FORMAT = "outEncodeFormat";
    private static final String OUT_SAMPLING_RATE = "outSamplingRate";
    private static final String OUT_BIT_RATE = "outBitRate";
    private static final String IS_CHAT_MODE = "isChatMode";
    private static final String IS_SAVE = "isSave";

    private static final String STATUS_START = "start";
    private static final String STATUS_FINISH = "finish";
    private static final String STATUS_STOP = "stop";

    private MP3Recorder recorder;
    private CallbackContext callback;
    private CallbackContext playSoundCallback;
    private MediaPlayer mediaPlayer;

    private String encodeFormat = "mp3"; // mp3 or ogg录音数据使用哪一种编码格式。
    private JSONArray argsRecord; // 保存录音参数。
    private VorbisRecorder recoderOgg = null; // Ogg格式的录音器。
    private File oggFile = null; // 保存的Ogg文件。
    private int sampleRateOgg = 44100;
    private int numberOfChannelsOgg = 1;
    private int bitrateOgg = 128000;

    private CallbackContext encodeCallback; // Ogg编码器
    private VorbisRecorder encoderOgg = null;
    private ByteArrayInputStream oggInputStream = null;
    private ByteArrayOutputStream oggOutputStream = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("startRecord")) {
            startRecord(args, callbackContext);
            return true;
        } else if (action.equals("stopRecord")) {
            stopRecord(callbackContext);
            return true;
        } else if (action.equals("hasPermission")) {
            hasPermission(callbackContext);
            return true;
        } else if (action.equals("requestPermission")) {
            requestPermission(callbackContext);
            return true;
        } else if (action.equals("playSound")) {
            playSound(args, callbackContext);
            return true;
        } else if (action.equals("stopSound")) {
            stopSound(callbackContext);
            return true;
        }
        else if (action.equals("startEncode")) {
            startEncode(args, callbackContext);
            return true;
        }
        else if (action.equals("stopEncode")) {
            stopEncode();
            return true;
        }
        return false;
    }

    private boolean requestRecordPermission(){
        boolean isPermissionGranted = cordova.hasPermission(Manifest.permission.RECORD_AUDIO);
        if(!isPermissionGranted){
            cordova.requestPermission(this, 1, Manifest.permission.RECORD_AUDIO);
        }

        return isPermissionGranted;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        switch (requestCode){
            case 1: {
                JSONObject json = new JSONObject();
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    json.put("hasPermission", true);
                } else {
                    json.put("hasPermission", false);
                }
                callback.success(json);
                return;
            }
        }
        callback.error("request code doesn't support");
    }

    private void startRecord(JSONArray args, CallbackContext callbackContext){

        // 确定使用哪一种编码格式。
        argsRecord = args;
        encodeFormat = "mp3";
        if (!args.isNull(0)) {
            try {
                JSONObject options = args.getJSONObject(0);
                if (options.has(OUT_ENCODE_FORMAT)){
                    if (options.getString(OUT_ENCODE_FORMAT).equals("ogg")) {
                        encodeFormat = "ogg";
                    }
                }
            }
            catch (JSONException ex){
                Log.e(TAG, ex.getMessage());
            }
        }

        // 支持ogg格式的录音。
        if (encodeFormat.equals("ogg")) {
            String state = Environment.getExternalStorageState();
            File directory = null;
            if(Environment.MEDIA_MOUNTED.equals(state)){
                directory = this.cordova.getActivity().getExternalCacheDir();
            }else{
                directory = this.cordova.getActivity().getCacheDir();
            }
            try{
                oggFile = File.createTempFile("temp", ".ogg", directory);
                oggFile.deleteOnExit();

                try{
                    JSONObject options = args.getJSONObject(0);
                    if(options.has(OUT_SAMPLING_RATE)){
                        sampleRateOgg = options.getInt(OUT_SAMPLING_RATE);
                    }
                    if(options.has(OUT_BIT_RATE)){
                        bitrateOgg = options.getInt(OUT_BIT_RATE);
                    }
                }catch (JSONException ex){
                    Log.e(TAG, ex.getMessage());
                }

                if(recoderOgg == null || !recoderOgg.isRecording()){
                    Handler recordingHandler = new Handler() {
                        @Override
                        public void handleMessage(Message msg) {
                            switch (msg.what) {
                                case VorbisRecorder.START_ENCODING:
                                    Log.i(TAG, "Starting to encode");
                                    break;
                                case VorbisRecorder.STOP_ENCODING:
                                    Log.i(TAG, "Stopping the encoder");
                                    break;
                                case VorbisRecorder.UNSUPPORTED_AUDIO_TRACK_RECORD_PARAMETERS:
                                    Log.i(TAG, "You're device does not support this configuration");
                                    break;
                                case VorbisRecorder.ERROR_INITIALIZING:
                                    Log.i(TAG, "There was an error initializing.  Try changing the recording configuration");
                                    break;
                                case VorbisRecorder.FAILED_FOR_UNKNOWN_REASON:
                                    Log.i(TAG, "The encoder failed for an unknown reason!");
                                    break;
                                case VorbisRecorder.FINISHED_SUCCESSFULLY:
                                    Log.i(TAG, "The encoder has finished successfully");
                                    break;
                            }
                        }
                    };
                    recoderOgg = new VorbisRecorder(oggFile, recordingHandler);
                    callback = callbackContext;
                    if(requestRecordPermission()){
                        recoderOgg.start(sampleRateOgg, numberOfChannelsOgg, bitrateOgg);
                        callbackContext.success();
                    }
                }
                callbackContext.success();
            }catch (IOException ex){
                Log.e(TAG, ex.getMessage());
                callbackContext.error(ex.getMessage());
            }

            return;
        }

        // 处理mp3格式的录音。
        if(recorder == null || !recorder.isRecording()){

            recorder = new MP3Recorder(this.cordova.getActivity());
            if(!args.isNull(0)){
                try{
                    JSONObject options = args.getJSONObject(0);
                    if(options.has(OUT_SAMPLING_RATE)){
                        recorder.setSamplingRate(options.getInt(OUT_SAMPLING_RATE));
                    }
                    if(options.has(OUT_BIT_RATE)){
                        recorder.setBitRate(options.getInt(OUT_BIT_RATE));
                    }
                    if(options.has(IS_CHAT_MODE)) {
                        recorder.setIsChatMode(options.getBoolean(IS_CHAT_MODE));
                    }
                    if(options.has(IS_SAVE)){
                        recorder.setIsSave(options.getBoolean(IS_SAVE));
                    }
                }catch (JSONException ex){
                    Log.e(TAG, ex.getMessage());
                }
            }
            try{
                callback = callbackContext;
                if(requestRecordPermission()){
                    recorder.startRecord();
                    callbackContext.success();
                }
            }catch (IOException ex){
                Log.e(TAG, ex.getMessage());
                callbackContext.error(ex.getMessage());
            }
        }else if(recorder.isRecording()){
            callbackContext.success();
        }
    }

    private void stopRecord(CallbackContext callbackContext){
        if (recoderOgg != null) {
            recoderOgg.stop();
            if (oggFile != null) {
                // 返回ogg数据到网页。
                byte[] oggData = null;
                try {
                    FileInputStream is = new FileInputStream(oggFile);
                    oggData = new byte[is.available()];
                    is.read(oggData);
                    is.close();
                    oggFile.delete();
                    oggFile = null;
                }
                catch (FileNotFoundException ex) {
                    Log.e(TAG, ex.getMessage());
                }
                catch (IOException ex) {
                    Log.e(TAG, ex.getMessage());
                }
                callbackContext.success(oggData);
            }
            else {
                callbackContext.error("record file not found");
            }
        }
        if(recorder != null){
            recorder.stopRecord();
            File file = recorder.getFile();
            if(file != null){
                /*
                Uri uri = Uri.fromFile(file);
                JSONObject fileJson = new JSONObject();
                try{
                    fileJson.put("name", file.getName());
                    fileJson.put("type", "audio/mpeg");
                    fileJson.put("uri", uri.toString());
                    fileJson.put("duration", recorder.getDuration());

                }catch(JSONException ex){
                    Log.e(TAG, ex.getMessage());
                }

                callbackContext.success(fileJson);
                */
                // ZYH: 修改成返回mp3数据到网页。
                byte[] mp3data = null;
                try {
                    FileInputStream is = new FileInputStream(file);
                    mp3data = new byte[is.available()];
                    is.read(mp3data);
                    is.close();
                    file.delete();
                }
                catch (FileNotFoundException ex) {
                    Log.e(TAG, ex.getMessage());
                }
                catch (IOException ex) {
                    Log.e(TAG, ex.getMessage());
                }
                callbackContext.success(mp3data);
            }else{
                callbackContext.error("record file not found");
            }
        } else {
            callbackContext.error("AudioRecorder has not recorded yet");
        }
    }

    private void hasPermission(CallbackContext callbackContext) {
        boolean isPermissionGranted = cordova.hasPermission(Manifest.permission.RECORD_AUDIO);
        JSONObject json = new JSONObject();
        try {
            json.put("hasPermission", isPermissionGranted);
        } catch (JSONException ex) {
            callbackContext.error(ex.getMessage());
            return ;
        }
        callbackContext.success(json);

    }

    private void requestPermission(CallbackContext callbackContext) {
        callback = callbackContext;
        if (this.requestRecordPermission()) {
            callbackContext.success();
        }
    }

    private void playSound(JSONArray args, CallbackContext callbackContext) {
        String path = null;
        try {
            path = args.getString(0);
        } catch (JSONException ex) {
            callbackContext.error(ex.getMessage());
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            if (this.playSoundCallback != null) {
                this.playSoundCallback.success(STATUS_STOP);
                this.playSoundCallback = null;
            }
        }

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException ex) {
            callbackContext.error(ex.getMessage());
            return;
        }
        this.playSoundCallback = callbackContext;
        PluginResult result = new PluginResult(PluginResult.Status.OK, STATUS_START);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);


    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (this.playSoundCallback != null) {
            this.playSoundCallback.success(STATUS_FINISH);
        }
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        if(this.playSoundCallback != null) {
            String errorMessage;
            switch (i) {
                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                    errorMessage = "unknown message";
                    break;
                default:
                    switch (i1) {
                        case MediaPlayer.MEDIA_ERROR_IO:
                            errorMessage = "io error";
                            break;
                        case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                            errorMessage = "timeout";
                            break;
                        case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                            errorMessage = "unsupported format";
                            break;
                        case MediaPlayer.MEDIA_ERROR_MALFORMED:
                            errorMessage = "bit error";
                            break;
                        default:
                            errorMessage = "other error";
                            break;
                    }
            }
            this.playSoundCallback.error(errorMessage);
        }
        return false;
    }

    private void stopSound(CallbackContext callbackContext) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer = null;

        }
        if (playSoundCallback != null) {
            playSoundCallback.success(STATUS_STOP);
            playSoundCallback = null;
        }
        callbackContext.success();
    }

    private void startEncode(JSONArray args, CallbackContext callbackContext) {
        String inFileName;
        String encodeFormat = "ogg";
        int sampleRate = 44100;
        int bitrate = 128000;
        int numberOfChannels = 1;

        // 停止上一次的编码。
        stopEncode();

        // 编码完成时的回调。
        Handler encodeHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case VorbisRecorder.FINISHED_SUCCESSFULLY:
                        // 返回ogg数据到网页。
                        if (oggOutputStream != null) {
                            byte[] oggData = oggOutputStream.toByteArray();
                            encodeCallback.success(oggData);
                        }
                        Log.i(TAG, "The encoder has finished successfully");
                        //break; Need through.
                    case VorbisRecorder.ERROR_INITIALIZING:
                    case VorbisRecorder.FAILED_FOR_UNKNOWN_REASON:
                        try {
                            if (oggInputStream != null) {
                                oggInputStream.close();
                                oggInputStream = null;
                            }
                        }
                        catch (IOException ex) {
                            Log.i(TAG, "Close input stream fail:" + ex.getMessage());
                        }
                        try {
                            if (oggOutputStream != null) {
                                oggOutputStream.close();
                                oggOutputStream = null;
                            }
                        }
                        catch (IOException ex) {
                            Log.i(TAG, "Close output stream fail:" + ex.getMessage());
                        }
                        break;
                }
                return true;
            }
        });

        if (!args.isNull(0)) {
            try {
                // 读取参数。
                JSONObject options = args.getJSONObject(0);
                String data = args.getString(1);

                if (options.has(OUT_NUMBER_OF_CHANNELS)) {
                    numberOfChannels = options.getInt(OUT_NUMBER_OF_CHANNELS);
                }
                if (options.has(OUT_ENCODE_FORMAT)) {
                    encodeFormat = options.getString(OUT_ENCODE_FORMAT);
                }
                if (options.has(OUT_SAMPLING_RATE)){
                    sampleRate = options.getInt(OUT_SAMPLING_RATE);
                }
                if (options.has(OUT_BIT_RATE)){
                    bitrate = options.getInt(OUT_BIT_RATE);
                }

                if (encodeFormat.equals("ogg")) {
                    // 启动后台编码
                    encodeCallback = callbackContext;
                    oggInputStream = new ByteArrayInputStream(Base64.decode(data, Base64.DEFAULT));
                    oggOutputStream = new ByteArrayOutputStream();
                    encoderOgg = new VorbisRecorder(oggInputStream, oggOutputStream, encodeHandler);
                    encoderOgg.start(sampleRate, numberOfChannels, bitrate);
                }
                else {
                    callbackContext.error("unsuport encode format.");
                }
            }
            catch (JSONException ex) {
                Log.e(TAG, ex.getMessage());
                encoderOgg = null;
                encodeCallback = null;
                callbackContext.error(ex.getMessage());
            }
        }

        return;
    }

    private void stopEncode() {
        if (encoderOgg != null) {
             if (encoderOgg.isRecording()) {
                 encoderOgg.stop();
             }
            encoderOgg = null;

            try {
                if (oggInputStream != null) {
                    oggInputStream.close();
                    oggInputStream = null;
                }
            }
            catch (IOException ex) {
                Log.i(TAG, "Close input stream fail:" + ex.getMessage());
            }
            try {
                if (oggOutputStream != null) {
                    oggOutputStream.close();
                    oggOutputStream = null;
                }
            }
            catch (IOException ex) {
                Log.i(TAG, "Close output stream fail:" + ex.getMessage());
            }
        }
    }
}
