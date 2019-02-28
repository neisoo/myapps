/**
 *
 * @param onSuccess
 * @param onDenied
 * @param onError
 */
var getRecordPermission = function (onSuccess, onDenied, onError) {
    window.audiorecord.hasPermission(function (hasPermission) {
        try {
            if (hasPermission) {
                if (onSuccess) onSuccess();
            }
            else {
                window.audiorecord.requestPermission(function (hasPermission, message) {
                    try {
                        if (hasPermission) {
                            if (onSuccess) onSuccess();
                        }
                        else {
                            if (onDenied) onDenied("User denied permission to record: " + message);
                        }
                    }
                    catch (ex) {
                        if (onError) onError("Start after getting permission exception: " + ex);
                    }
                });
            }
        }
        catch (ex) {
            if (onError) onError("getRecordPermission exception: " + ex);
        }
    });
};


/**
 * Start capturing audio.
 */
var startCapture = function () {
    try {
        if (window.audiorecord/* && !window.audioinput.isCapturing()*/) {

            getRecordPermission(function () {
                // Connect the audioinput to the speaker(s) in order to hear the captured sound.
                // We're using a filter here to avoid too much feedback looping...
                // Start with default values and let the plugin handle conversion from raw data to web audio.

                consoleMessage("Microphone input starting...");
                window.audiorecord.startRecord({
						outEncodeFormat: "ogg"
					}, function(result) {
					consoleMessage("Record success:" + result);
				});
                consoleMessage("Microphone input started!");
                consoleMessage("Capturing audio!");

                disableStartButton();
            }, function (deniedMsg) {
                consoleMessage(deniedMsg);
            }, function (errorMsg) {
                consoleMessage(errorMsg);
            });
        }
        else {
            alert("Already capturing!");
        }
    }
    catch (ex) {
        alert("startCapture exception: " + ex);
    }
};


/**
 * Stop capturing audio.
 */
var stopCapture = function () {

    if (window.audiorecord /*(window.audioinput.isCapturing()*/) {
        window.audiorecord.stopRecord(function(data) {
			var blob = new Blob([data], { type: 'audio/ogg' });
			if (blob.size > 0) {
				var mp3Name = 'recording_' + Date.now() + '.ogg';
				
				var reader = new FileReader();
				reader.onload = function (evt) {
					var audio = document.createElement("AUDIO");
					audio.controls = true;
					audio.src = evt.target.result;
					audio.type = "audio/ogg";
					document.getElementById("recording-list").appendChild(audio);
					consoleMessage("Audio created");
				};

				consoleMessage("Loading from BLOB");
				reader.readAsDataURL(blob);
			}
		});
        disableStopButton();
    }

    consoleMessage("Stopped!");
};

/**
 * Initialize UI listeners.
 */
var initUIEvents = function () {
    document.getElementById("startCapture").addEventListener("click", startCapture);
    document.getElementById("stopCapture").addEventListener("click", stopCapture);
};


/**
 * When cordova fires the deviceready event, we initialize everything needed for audio input.
 */
var onDeviceReady = function () {
    if (window.cordova && window.audioinput) {
        initUIEvents();

        consoleMessage("Use 'Start Capture' to begin...");
    }
    else {
        consoleMessage("cordova-plugin-audioinput not found!");
        disableAllButtons();
    }
};


// Make it possible to run the demo on desktop.
if (!window.cordova) {
    console.log("Running on desktop!");
    onDeviceReady();
}
else {
    // For Cordova apps
    console.log("Running on device!");
    document.addEventListener('deviceready', onDeviceReady, false);
}
