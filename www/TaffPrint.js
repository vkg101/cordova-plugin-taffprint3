module.exports = {
    scan: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "TaffPrint", "scan", []);
    },
    connect: function(name, success, error) {
        //Note: These callbacks will fire whenever a status change occurs.
        cordova.exec(success, error, "TaffPrint", "connect", [name]);
    },
    printText: function(message, success, error) {
        cordova.exec(success, error, "TaffPrint", "print", [message]);
    },
    status: function(success, error) {
        cordova.exec(success, error, "TaffPrint", "status", []);
    },
    disconnect: function(success, error) {
        cordova.exec(success, error, "TaffPrint", "disconnect", []);
    },
    printImage: function(path, success, error) {
        cordova.exec(success, error, "TaffPrint", "printLogo", [path]);
    },
	printPOSCommand: function(command, message, success, error) {
        cordova.exec(success, error, "TaffPrint", "printPOSCommand", [command, message]);
    },

};