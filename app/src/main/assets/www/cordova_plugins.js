cordova.define('cordova/plugin_list', function(require, exports, module) {
module.exports = [
  {
    "id": "cordova-plugin-audiorecorder.AudioRecorder",
    "file": "plugins/cordova-plugin-audiorecorder/www/AudioRecorder.js",
    "pluginId": "cordova-plugin-audiorecorder",
    "clobbers": [
      "audiorecord"
    ]
  },
  {
    "id": "cordova-plugin-audioinput.AudioInput",
    "file": "plugins/cordova-plugin-audioinput/www/audioInputCapture.js",
    "pluginId": "cordova-plugin-audioinput",
    "clobbers": [
      "audioinput"
    ]
  }
];
module.exports.metadata = 
// TOP OF METADATA
{
  "cordova-plugin-whitelist": "1.3.3",
  "cordova-plugin-audiorecorder": "0.7.0",
  "cordova-plugin-audioinput": "1.0.1"
};
// BOTTOM OF METADATA
});