cordova.define("cordova-plugin-audiorecorder.AudioRecorder", function(require, exports, module) {

var exec = require('cordova/exec');

var audiorecorder = {};

audiorecorder.AUDIO_SAMPLINGS = {
    MAX: 44100,
    NORMAL: 22050,
    LOW: 8000
};

var noop = function(){};

audiorecorder.startRecord = function(options, success, error) {
    success = success || noop;
    error = error || noop;
    exec(success, error, 'AudioRecorder', 'startRecord', [options]);
};

audiorecorder.stopRecord = function(success, error){
    success = success || noop;
    error = error || noop;
    exec(success, error, 'AudioRecorder', 'stopRecord');
};

audiorecorder.hasPermission = function(success, error) {
    success = success || noop;
    error = error || noop;
    exec(success, error, 'AudioRecorder', 'hasPermission');
}

audiorecorder.requestPermission = function(success, error){
    success = success || noop;
    error = error || noop;
    exec(success, error, 'AudioRecorder', 'requestPermission');
}

audiorecorder.playSound = function(path, success, error) {
    success = success || noop;
    error = error || noop;
    exec(success, error, 'AudioRecorder', 'playSound', [path]);
}

audiorecorder.stopSound = function(success, error) {
    success = success || noop;
    error = error || noop;
    exec(success, error, 'AudioRecorder', 'stopSound');
}

module.exports = audiorecorder;
});


