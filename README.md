# cordova-plugin-taffprint
A cordova plugin for Android to print text and images with Taffware Bluetooth POS printer.

The plugin was tested with model POS-5802D D. However this seems to be a white-labeled printer, so it's likely other printers are supported in the same way.

The SDK comes largely from the manufacturers installation CD (this came with Android examples).

Note: This plugin does note make new connections. It assumes you want to print from a device that has already been paired with.

# Install 
```
cordova plugin add https://github.com/c00/cordova-plugin-taffprint.git
```

# Usage
Use the `TaffPrint` object to do stuff.

## List paired devices:
```
TaffPrint.scan(
    function(result){
        console.log(result);
    },
    function(error){
        console.log("Error: " + error);
    });
```

## Connect to a device
```
var address = "AA:BB:CC:DD:EE:FF";
TaffPrint.connect(address,    
    function(result){
      //Note that this callback will be called everytime the state of the connection changes, untill the connection is disconnected. 
       console.log(result);
    },
    function(error){
       console.log("Error: " + error);
    });
```

## Get connection status
Tells you what the current state is. Will be `connected`, `disconnected`, or `connecting`.
```
TaffPrint.status(
    function(result){
        console.log(result);
    });
```

# Print some text
```
TaffPrint.print("Some text to be printed\nI'll be on the second line.", 
    function(result){
        console.log(result);
    },
    function(error){
        console.log("Error: " + error);
    });
```

# Print the logo
There's one logo added to the resources that will be printed. In the future this should be able to take a URL or something as parameters. However, now it just prints one thing only.
```
TaffPrint.printLogo( 
    function(result){
        console.log(result);
    },
    function(error){
        console.log("Error: " + error);
    });
```

## Disconnect the printer
```
TaffPrint.disconnect( 
    function(result){
        console.log(result);
    },
    function(error){
        console.log("Error: " + error);
    });
```