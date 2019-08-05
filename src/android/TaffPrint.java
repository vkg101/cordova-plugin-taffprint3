package com.covle.cordova.plugin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;

import com.covle.sdk.Command;
import com.covle.sdk.PrintPicture;
import com.covle.sdk.PrinterCommand;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


public class TaffPrint extends CordovaPlugin {

    private final String LOGO = "logo";
    private final String TAG = "coo";
    private BluetoothAdapter mBtAdapter;
    private JSONArray mPairedDevices;
    private CallbackContext mBtConnectCallback;

    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_CONNECTION_LOST = 6;
    public static final int MESSAGE_UNABLE_CONNECT = 7;
    public static final String DEVICE_NAME = "device_name";

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the services
    private BluetoothService mService = null;



    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        init();

        if (action.equals("scan")){
            scan(callbackContext);
        } else if (action.equals("connect")){
            connect(data.getString(0), callbackContext);
        } else if (action.equals("status")){
            status(callbackContext);
        } else if (action.equals("disconnect")){
            mService.stop();
            callbackContext.success("disconnected");
        } else if (action.equals("printLogo")){
            printLogo(data.getString(0), callbackContext);
        } else if (action.equals("print")){
            sendDataString(data.getString(0), callbackContext);
        } else if (action.equals("printPOSCommand")){
			printPOSCommand(hexStringToBytes(data.getString(0)), data.getString(1), callbackContext);
		} else {
            return false;
        }

        return true;
    }

    public void status(CallbackContext callback){
        int state = mService.getState();
        switch (state) {
            case BluetoothService.STATE_LISTEN:
            case BluetoothService.STATE_NONE:
                callback.success("disconnected");
            case BluetoothService.STATE_CONNECTED:
                callback.success("connected");
            case BluetoothService.STATE_CONNECTING:
                callback.success("connecting");
        }

        callback.error("Unknown state");
    }

    public void scan(CallbackContext callbackContext){
        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mPairedDevices = new JSONArray();

        // Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                JSONObject d = new JSONObject();

                try{
                    d.put("name", device.getName());
                    d.put("address", device.getAddress());
                    mPairedDevices.put(d);
                } catch (Exception ex) {
                    //cry
                }
            }
        } else {
            callbackContext.error("No paired devices");
        }


        JSONObject json = new JSONObject();
        try {
            json.put("devices", mPairedDevices);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, json));
        } catch (Exception ex){
            callbackContext.error("Json error...");
        }
    }

    private void init(){
        if (mBluetoothAdapter == null){
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (mService == null) {
            mService = new BluetoothService(mHandler);
        }
    }

    public void connect(String address, CallbackContext callback){
        //init();
        mBtConnectCallback = callback;
        PluginResult result;

        if (mBluetoothAdapter == null){
            result = new PluginResult(PluginResult.Status.ERROR, "No bluetooth adapter");
        } else if (BluetoothAdapter.checkBluetoothAddress(address)) {
            // Get the BluetoothDevice object
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            // Attempt to connect to the device
            mService.connect(device);

            result = new PluginResult(PluginResult.Status.OK, "Connecting...");
            //Magically keep the callback.
            result.setKeepCallback(true);
        } else {
            result = new PluginResult(PluginResult.Status.ERROR, "Can't find Printer");
        }

        mBtConnectCallback.sendPluginResult(result);
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            PluginResult result;
            boolean persist = true;

            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:

                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            result = new PluginResult(PluginResult.Status.OK, "connected");
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            result = new PluginResult(PluginResult.Status.OK, "connecting");
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            result = new PluginResult(PluginResult.Status.OK, "disconnected");
                            persist = false;
                            break;
                        default:
                            result = new PluginResult(PluginResult.Status.ERROR, "unknown");
                            persist = false;
                    }
                    break;
                /*case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    result = new PluginResult(PluginResult.Status.OK, "Getting device name");
                    break;*/
                case MESSAGE_CONNECTION_LOST:
                    result = new PluginResult(PluginResult.Status.OK, "disconnected");
                    persist = false;
                    break;
                case MESSAGE_UNABLE_CONNECT:
                    result = new PluginResult(PluginResult.Status.OK, "cant-connect");
                    persist = false;
                    break;
                default:
                    result = new PluginResult(PluginResult.Status.NO_RESULT, "irrelevant");
            }

            result.setKeepCallback(persist);
            mBtConnectCallback.sendPluginResult(result);
        }
    };

    private void sendDataString(String data, CallbackContext callback) {

        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            callback.error("Not connected");
            return;
        }
        if (data.length() > 0) {
            try {
                mService.write(data.getBytes("GBK"));
                callback.success("Printed.");
            } catch (UnsupportedEncodingException e) {

            }
        } else {
            callback.error("Nothing to write...");
        }
    }

	private void printPOSCommand( byte[] command, String message, CallbackContext callback){
		if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            callback.error("Not connected");
            return;
        }
		try {
			//sendDataByte(Command.ESC_Init);
            //sendDataByte(Command.LF);
			
			sendDataByte(command);
			sendDataByte(message.getBytes(Charset.forName("UTF-8")));
			
			sendDataByte(PrinterCommand.getCodeBarCommand("teststr", 65, 100, 100, 1, 1));
			
			sendDataByte(PrinterCommand.POS_Set_Cut(1));
            sendDataByte(PrinterCommand.POS_Set_PrtInit());
			
			
			
			callback.success("Executed.");
		} catch (Exception e) {

		}
		
	}
	
    private void sendDataByte(byte[] data) {

        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            //todo return not connected?
            return;
        }
        mService.write(data);
    }

    private void printLogo(String path, CallbackContext callback){
		
        Bitmap mBitmap;


        try {
            //Resources resources = cordova.getActivity().getResources();
            //String packageName = cordova.getActivity().getPackageName();
            //int id = resources.getIdentifier(LOGO, "drawable", packageName);

            //mBitmap = BitmapFactory.decodeResource(resources, id);
			
			//final String encodedString = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAlgAAADSCAYAAACW0r5LAAAgAElEQVR4Xu2dB7QURfq3X8wZFbMiiogBcQ2omDDnBOa0JjDn7Io5R1wVEXPOYs6iuIoJBTNmMQurGDBgxO//1H41p+/c6emume7LhF+d41Hvra6ueqqZeah66+12f/9fMRUREAEREAEREAEREIHMCLSTYGXGUg2JgAiIgAiIgAiIgCMgwdKDIAIiIAIiIAIiIAIZE5BgZQxUzYmACIiACIiACIiABEvPgAiIgAiIgAiIgAhkTECClTFQNScC9Ubggw8+sEUXXbTQ7Xvvvdc233zz1MP47bffbIkllrAxY8a4a44//ng75ZRTWly///7726BBg0q2Offcc9tCCy1kPXv2tE022cTWWmstm2qqqVLfP67iBhtsYI899piNHTvWuAfl5ZdfthVWWMHoz8CBA6u6x59//mkvvPCC3XHHHTZp0iS7+OKLq2pPF4uACDQWAQlWY82nRiMCwQSKBWuLLbawe+65J3U7Dz74oG266aaF+qGCVXyjzTbbzK644oqCFKXuSKTi77//brPNNpvNN9989v777xd+c9NNN9nOO+9sgwcPtr333ruSpg1eSOjVV19to0ePdm3stddedtlll1XUni4SARFoTAISrMacV41KBFITKBYsLnz33Xeta9euqdrYdttt3SqOL+UEa8EFF7T//Oc/Ldr94Ycf7NNPP7XLL7/cHnjgAfe7tdde2x555BGbeuqpU/WhuBL9X3zxxW2XXXax6667rvDrY4891s4880x76qmnbI011ghq+6677rJrrrmm0MfoxRKsIJSqLAJNQUCC1RTTrEGKQDyBqGAhNH/88Yeddtpp1r9//0RsH330kS2yyCKunr+2nGAtvPDCxjWlCin5uO8JJ5zgfn333Xdb7969E/tQqsJ9991nrMSdf/75dthhhxWq+G3DL774wq1uhRSEDXFjnEjldtttZ+edd549/fTTWsEKAam6ItAkBCRYTTLRGqYIxBGIChbbZzfeeKOx0vTOO+/Y9NNPXxbc2Wefbcccc4yTrCWXXNLuv//+sjFY5QSLG/3888/WsWNH++6776qKk/L9euihh2yjjTZyY/DbhnPMMYd9/PHH1q5du6CHAqFaf/31XZzYPPPM467dcMMN7dFHH5VgBZFUZRFoDgISrOaYZ41SBGIJRAWLLTofT5UU7B4Nbj/33HNt+PDhLjap0hUs30FisOgH8vLwww9XNHM77rij3XLLLS5eyq+w+W1DfkcsVhZFgpUFRbUhAo1JQILVmPOqUYlAagJRwRo1apQNGDDArWL16dPHiDuKK6wOsZpDYUWIOCRO7VUrWGztscW3zjrr2NChQ1ONg1irG264IVXdaCXis7i20iLBqpScrhOBxicgwWr8OdYIRaAsgahgkXZgwoQJbiuMwgm8Ll26lLyeLbPbb7/dncpDbnr16mXPPPNMVYL1008/ue1JtggPOuggu/DCC1PN3hFHHOEC1ykTJ050p/s6dOjg0j/48vnnn9u4ceNsscUWs5lmmsn9mKD3LbfcMtU9SlWSYFWMTheKQMMTkGA1/BRrgCJQnkBUsAjYXnnllW3ppZe2t99+28444wz717/+1aoBcl517tzZ/ZxVq/XWW8969OhhI0eOrFiwCHJn9ev000937Q4bNszWXHPN4Ol7/PHHnSDSDgLlCyJF4Dx9j4pX8A0iF0iwqqGna0WgsQlIsBp7fjU6EUgkEBUs5GTddde1iy66yA4++GAnIojWdNNN16Kdc845x44++miXYPS1115zJ+v8KbukNA3PP/98i7aI5aIPV111ld12223ud4cccohdcMEFiX0vVaFUgDv3INno7LPPbh9++GFwgHtcRyRYFU2RLhKBpiAgwWqKadYgRSCeQFSwSBq68cYb25dffunkipQNnAyMJhJFVrp16+ZEhS08tvIonP5jG66aRKOIGqcSSdVQaTZ3stDTZ/oy//zzu74hgcsss4ztueeeLt9WVkWClRVJtSMCjUdAgtV4c6oRiUAQgahgDRkypBCT1LdvX5etfOutt26RSJSTfUgYMsR2m5eYWWaZxX788ceqBOvJJ590r8qptPz6668u9qpTp06FLOu0df3119uuu+7qMsT369ev0uZbXSfBygylGhKBhiMgwWq4KdWARCCMQFSw2KIjiSaFoHEvO9F0Bz64fY899nDber74vFKhpwhZLSOHFhndjzrqKGOLL6QceOCB7h2DFFbXXnnlFfeaHILZfWE1i3/Y0mzfvr378XHHHVc4BRlyv2hdCVal5HSdCDQ+AQlW48+xRigCZQlEBevWW291GcopvMyYwHW218466ywXc0U6BpKFUoqD0CsVLNrycVOsir3xxhst5Chp+kgnEfLuRN9edKxJ94j7vQSrUnK6TgQan4AEq/HnWCMUgYoEi4suvfRS22+//ZxUEexOzBWi1b17dyNnVjROqhrB+v77712brDIVvz8wZPqI3Tr11FNd/izyaFFI28CqFaceyU6fZZFgZUlTbYlAYxGQYDXWfGo0IhBMIG4Fi4bIG0WwO7FNxGexhUdw+6BBg2zfffdtca9qBIuGiI8iWSmFk4Y9e/YMHotPUvrJJ5+4fFqUt956y5Zaainbbbfd3MuasywSrCxpqi0RaCwCEqzGmk+NRgSCCZQTLBpDpAYPHuzSHCBcpGxgq5D/j5ZqBYuVppVWWsltESIuZIoPeV/gX3/95fo0xRRT2NixY92/KeS+IgcWGeoPPfTQYD7lLpBgZYpTjYlAQxGQYDXUdGowIhBOIEmweMfg6quvXmh4n332cVuHxaVawaI9Vsk4tUjhfYT+VTxpRuXjw3r37u2kyheSpfbv398Jm3/xc5r20tSRYKWhpDoi0JwEJFjNOe8atQgUCCQJ1qRJk9zKkj+px+twVltttVwEi8B6YqfIKL/88svbs88+a9NOO22q2fIZ3E866SQ78cQTC9dsv/32LoFpudf+pLpBiUoSrErJ6ToRaHwCEqzGn2ONUATKEkgSLC728VFIz4gRIwrbb9GGs1jBor1oeohrr73W5a9KUwYOHGikbIimmuD1O8RikQJi/PjxLndXlkWClSVNtSUCjUVAgtVY86nRiIAIiIAIiIAI1AABCVYNTIK6IAIiIAIiIAIi0FgEJFiNNZ8ajQiIgAiIgAiIQA0QkGDVwCSoCyIgAiIgAiIgAo1FQILVWPOp0YiACIiACIiACNQAAQlWDUyCuiACIiACIiACItBYBCRYjTWfGo0IiIAIiIAIiEANEJBg1cAkqAsiIAIiIAIiIAKNRUCC1VjzqdGIgAiIgAiIgAjUAAEJVg1MgrogAiIgAiIgAiLQWAQkWI01nxqNCIiACIiACIhADRCQYNXAJKgLIiACIiACIiACjUVAgtVY86nRiIAIiIAIiIAI1AABCVYNTIK6IAIiIAIiIAIi0FgEJFiNNZ8ajQiIgAiIgAiIQA0QkGDVwCSoCyIgAiIgAiIgAo1FQILVWPOp0YiACIiACIiACNQAAQlWDUyCuiACIiACIiACItBYBCRYjTWfGs1kJPDBBx/Yoosu6npw00032Y477lh1b/bbbz+79NJLbZFFFrHRo0fbNNNMk9jmwQcfbBdddJHNP//89vnnnyfWj1bw1/KzHj162IsvvmhTTDFF6jauueYa22OPPQr1x44da3PPPXeL69u1a+f+//jjj7dTTjmlxe9uvvlm22mnnUreb7bZZrMFFljAll9+eVtvvfVss802s5lnnjl136gYnaNbb73Vtttuu9jr3333XVtnnXXsiy++cPd8+OGHbc4553T1b7vtNtt+++3df7///vvWpUuXxH5su+22dscdd9jqq69uTz/9dGL9P/74w5Zaail77733bK+99rLLLrss8Zro+HbYYQf3HHrecRdfccUVrn3K119/bXPMMUfifVRBBEQgmYAEK5mRaohAKgJZC9a3335rHTt2tF9++cXd//HHH7d11103sS9ZCRY3evbZZ22VVVZJvCcV/v77b1t11VXt+eefL9TPUrCKO7Hkkkva9ddf7+QnbUkrWF9++aWTOKR2oYUWsieeeMI6d+5cuE1bCNawYcNs7bXXdvecbrrp7LPPPkuUn+j4uA6h23rrrcvikWClfXpUTwTCCEiwwniptgjEEshasG644QbbZZddCvfr27evXXnllYkzkKVg7b///jZw4MDEe1JhxIgRttJKK7WoW41gPfXUU9apU6dCexMnTnSrSQ888IBdeOGF7ufzzjuvvfDCC7bgggum6mMawfr+++/d6tjw4cONVTPkatlll23RflsI1j777NNi1YrVwd12263sOIsFixW/l19+udUqYrQRCVaqR0eVRCCYgAQrGJkuEIHSBLIWLFar+HJff/317bHHHnOrGJ988onNNddcZacgC8GaeuqpjS2qGWaYwd0zzbbRoYceav/+97/NX0snqxGscltvjz76qG244YaOw1FHHWVnn312qscySbCQOLZ277nnHjeOBx980K1kFZe8Beubb75xcsnqpZ//Xr162X/+858gwaJy0vaiBCvVo6NKIhBMQIIVjEwXiED+gvXWW2+5+BvK66+/7mKF3n77bbvqqqtaxDiV6kkWgkW8EFuS3333nfEF3K9fv7LTznYmW2k//vij7brrrnbddde5+nkJFm1vs802duedd7r7fvTRR4mxRlxTTrD+/PNPY8Xu8ssvd32/8cYbY+PB8hYs+LFatfDCC7sVu27durk+vfbaa7b00kvHzkV0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8Ouvv9pDDz3k/nnxxRfts88+s3nmmce6du1qG220kW2++eY2//zzB43/jz/+sH/84x/29ttv23XXXWe77LJL0PXVVM56PO+++64tvvjirktpx3LzzTfbTjvtVHIYs802my2wwAK2/PLL23rrrWebbbaZzTzzzCXrdu7c2caMGdPqd1zfvXt3W3PNNW2LLbawxRZbLBhZJeOKXlN8wxlmmMHmm28+W3rppW2NNdawrbbaKvi5oU0/5oMOOsguvPDC2HH9+OOP1qdPH3viiSccP/69wgoruPq33Xabbb/99u6/33//fevSpUsin2233dbuuOMOW3311e3pp59OrE+F/fbbzy699FJbZJFFbPTo0TbNNNMkXufHR59GjBhhPA/lyquvvmrLLrusq/Loo4/a+uuvn3gPVRCBeiIgwaqn2VJfUxN45513bPfdd7cXXngh9hq+AM4//3xXL23hi2DDDTd0Xx4fffSRzTrrrGkvrapeHuM566yz7F//+pfr11prrWVPPvlkYh/LCVbxxUsuuaRdf/31TriKS5xgFdc77rjjjH+mnXbaxL75CpWMq5xgFd+4ffv2NmjQINtxxx1T94mKaQTrt99+s912281uvfVW1zZ/OeAvA760hWB9++231rFjR/vll1/cbR9//HFbd911E8candNDDz3UBgwYUPYaCVYiUlWocwISrDqfQHW/NYEvv/zSVl55Zfv000+dCPFhv/baa9vcc89tEyZMcGKEWHn5uvLKK61v376pULJ6wJfcMcccY2eeeWaqa6qtlMd4fv/9d0OAPvzww0L33njjDVtqqaXKdjcqWE899ZR16tSpUH/ixIn2xRdf2AMPPFBYoZl33nkd5wUXXLBFu/7LGJk48cQTC79jfljZGjJkiN1www3u55tuuqndeOONhtgklUrHFRWsiy++2N3Tlz///NO++uore+mll+yMM86w8ePHl5SfpL4lCdakSZPcs3rRRRe5pq655honW9HSFoIF9+jKLH82+DOSVIqlmeeDFb+4IsFKIqrf1zsBCVa9z6D634rAUUcdZeeee65NN910bmuQrZ3iwlYfcVSXXXaZq/fBBx8kbvsgI35L5vXXX3dbWW1R8hgP206sSrD9tdJKK9mwYcPs5JNPthNOOKHskKKCVW6Lyq/00Rj9P/vss1u0myQbVH744Ydtu+22M7bM0qyI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BwlW9Z9XakEE6oGABKseZqkN+ijBkmCFykeoMIXWz0NuQseYRx8kWG3wgaZbiEANEJBg1cAk1EIXJFgSrFD5CBWm0Pp5yE3oGPPogwSrFj7x1AcRyJ+ABCt/xrqDCIiACIiACIhAkxGQYDXZhGu4IiACIiACIiAC+ROQYOXPWHcQAREQAREQARFoMgISrCabcA1XBERABERABEQgfwISrPwZ6w4iIAIiIAIiIAJNRkCC1WQTruG2JjBixAjbcccd7cMPP7STTz7ZTjjhhIow0c5OO+1kH3zwgZ100kl24oknlm3nnnvusS233NKiJ9vS3PiRRx6xDTbYoGTVv/76yx5++GG7++677dlnn7Uvv/zS5ptvPltppZWsT58+tummm9pUU02VeJus2pk4caLdeeeddv/999uoUaNswoQJtthii9mGG25oO++8s3Xq1CmxL1QYN26c3XvvvW5so0ePtvHjx1vnzp1trbXWcsyXXnrpVO2okgiIgAi0FQEJVluR1n1qksD1119v++yzjyEClEoF64YbbrC999670M7kEKzvvvvO9thjDycipaSNlAMbb7yxXXfdddahQ4fY+ciqnY8//th22GEHe/HFF1v1h77MNttsds0119jmm28e2xdEb/DgwU5Ykaq4cf373/+2gw46qCafMXVKBESgOQlIsJpz3pt+1L/++qsdf/zxdt555zkWM8wwg/3yyy/BgkU7rHide+65LdpJI1hvv/22PfHEE6nm4qWXXjJkkPL6669b9+7dW1yHeGy//fZ2++23u59vtNFGboVorrnmsrFjxxoC+Nhjj7nfbb311q5eqRxPtLPddtvZHXfcUVU7P/zwg6277rr28ssvu3b69evnVt1mnHFGe//99+2SSy6x9957z6aeemobOnSo9erVqySHQw45xC688EL3uwUWWMB23XVXW3bZZd0q3KuvvmrnnHOOmzcKq3a9e/dOxVOVREAERCBvAhKsvAmr/Zoj8NVXX1nfvn3ddhPl2muvtUsvvdSttISsYNEO4vDQQw+5dliNueyyy+yFF15ItUUYAoYtzFtuucVJCv0uliO233r06OFWePbaay8bNGiQTTnllIVbTJo0yf38qquucj8bOXKkLbfccq26QDvLL7+8+zn14UISWl/StsOK0qGHHuouu+mmm9wWbLSwGoXoPfXUU/aPf/zDnn/+eZt++ulb9ee1115z24AHHnigHXbYYda+ffsWdehvz5497Y8//nDSSf245KAhvFVXBERABKolIMGqlqCurysCrDituOKK9sYbb7hVqxtvvNHFJhHDw8/SChbtENfEahLtsEJEPBWywM/SrGClBUdMV9euXZ08sbKEmBQXtv123313V4eVHfpRXOiX/zn1d9lll5Lt7Lbbbu7nyEqp2KY07XTr1s3FStFXvxpWfDNWsqiHHA0ZMsTxK1V8HFkcr6OOOqqwgkibXbp0SYtW9URABEQgNwISrNzQquFaJcD22NFHH2233Xabky3Koosu6oLT0woW10TbWWGFFdzKCSLEl3yWgkWfaG/++ee3d999122zlRMsAsLZGiwu/HyeeeZxP04jWP/9739tzjnnDG6HlT0C68vdxzeKFLKCSKA6sltJYYWM7VDKc889ZyuvvHIlzegaERABEciUgAQrU5xqrF4IIA9RCSG+54svvggSLMZa3E7Hjh3t888/z0ywOHWH/HGfU0891fr3719yC+zpp5+2Nddcs+wKFvFQiCDlmWeesdVWW63VdNHOGmus4X4et4KV1A4rV6xMUYj7Wm+99WIfiyuuuMJtRc4777yOfyXbe6we+tW4uD7Xy3OpfoqACDQOAQlW48ylRlIFAWTr66+/Dhas4lvOPffcToayWsFCHgjsZuuPoHBkq1Rhm40YLLbvjjzySDv77LNbyArXEy929dVXuxUeRKpUugbaIQaL7VLaIYg8WtK0wyrb4osv7i5LCjwnrszHZ7Hy5VfYQqbytNNOcwcWKN98803ZE5Ih7aquCIiACFRDQIJVDT1d2zAE5phjDpcGIGSLsNTg2VLjSz4LwSKgnFUmAsC33XZbu/XWW8uu8BAwThD877//bgMGDHCB4UjUn3/+aUgIY0MAH3300ZIxWn48tLP++uu72KgLLrjADjjggKB2SPMw++yzu+YQPWKk4goS509yVhI/RR+XWWYZF++1zTbbFE5RNsyDqYGIgAjULQEJVt1OnTqeJYFaFKzhw4e79AWsGnFykOScSYUVI3JhkSaB03d77rmnsQ335JNPOlkbOHBgWbny7d91112uHbYo1157bbf6FdIOfUXk2Hp95ZVXDL7FhW3KddZZx4kcpVT6iaTx0s+tttrKVSOZKYlUVURABESgFghIsGphFtSHyU6gFgULwSH1A5nPiS2adtppU3EiXxYnAREzYpr490ILLeTioeK2GEs1TCC8P1EY2g7Z5snFRUGiWNEjfxVjIEYNMSJ/GIcCEDBK6AoW7SCNn3zyiUtWilxGU0qkgqVKIiACIpATAQlWTmDVbH0RqDXBQhoWXnhhJ0fklDr44IMTgZI6gq3A008/3Z3+49/IGT97/PHHXeb0iy66qHDiLq5B2iGg/owzzqiqHe5/3HHHxfabrUded0N+KwpB7v70YdJgybxPWgdEjgB5co8tuOCCSZfp9yIgAiLQZgQkWG2GWjeqZQK1JlhnnXWWHXvssS72acyYMS5FQ7nCNhspD0hZsMQSS9h9991XyAdFDBapEIjJ8vIUJz60w6rVzTff7Nph222RRRZxtw5px/eVbUKkzidj5ee0S4Z2kr0SF4bMUdiOnHnmmRMfE2LTkDOSoFLIhs82pooIiIAI1BIBCVYtzYb6MtkI1JJg8eoXTuF99tlnLoaK7PBJ6Qsuv/xy905FhIwtN58mIQqUOC7eRUiJO91HO7xTkVfYkLB0ySWXbDUnadopvuinn36yb7/91qabbjoXj+W38ni9D/nIyMJODFaaEj01CBvSPKiIgAiIQK0RkGDV2oyoP5OFQC0JFpnPeR8g24OkU1h99dXLMmFFBxEiPcJ+++3nAtnjhMwn9uQ1OeSzitajHVaXSAex//77u3biSrl20k4g9+vUqZOLySLezL/Gp9z1BNojgLAhNcMpp5yS9naqJwIiIAJtSkCC1aa4dbNaJVArgoU48JJkTv2R14rYoug7BUvxY6ULUeFaguJ9YHqpugTAk1eLwnWc8vOF//dxTGwp+nqh7aSd42jC0rjM8tG2oicGEUm2HpPYpO2L6omACIhA1gQkWFkTVXt1SaBWBGvEiBHu5cXI0pVXXunilJJK9F2FJCb1r40pdR25tHbYYQf3qw8//NAFmftCO/6UIa+t4fU1caVcO0n99b8//PDDXb4uCu8bJFg9rpCbi1OJxJDRL1a70p6qTNsf1RMBERCBLAlIsLKkqbbqlkCtCBbB25dccokL9ia4vUOHDolMSexJPaSMwHhilOK2CDmNyMoPsVBkrp9pppkK7UcThNIOpwDjSrl2Ejv8fxUQSV6WTSH4nj7FlVGjRrlVPfpHOgayv/OCbRUREAERqGUCEqxanh31rc0I1IJg8aoYTuyRgoDUBWQ4Twpu94A22WQTd1IP8WBbkaDx4sK2IwlAOSnIyhgrZMUl2s6LL75oSy21VEXtlJs4EozyehxirxBDgtvj0jMQD0ZWedJWkDh1yJAhLt2EigiIgAjUOgEJVq3PkPrXJgRqQbDId4VYsRI1cuRIIxA9bXnuuedc0k2uZfWLdrieV9YgbsOGDSukNeB1OWSJ79KlS6vmaWfVVVd1P6+mHa4n9xYyh0SxtUeM14MPPuhe+ePbRwpLvXSa33///fcuwP/NN9909VnpisaMxbHh5KUkLO2To3oiIAJ5EZBg5UVW7dYVgcktWL/99ptbLSIOipxOQ4cOTb165UGTeoGUBbwLMa5w2pBAd17oHFdoB0nh3YzVtEOmdp/jqrgdpIr3HBLIH1eiMWEhD9Onn35qHTt2DLlEdUVABEQgcwISrMyRqsF6JDC5BYvEoL1793YrUMQYkR+qkkKw+J133ulOIbL1Nm7cOLf9hlARJN6nTx+bZZZZEpvOoh1ONLLNSYb29u3bu9f10A9eSM123zTTTFO2HxKsxGlSBREQgRomIMGq4clR19qOAGJDSRvzFNezatqp5tri/vi2oj+vZGzVtpPH9UlPRSXjTGpTvxcBERCBUAISrFBiqi8CIiACIiACIiACCQQkWHpEREAEREAEREAERCBjAhKsjIGqOREQAREQAREQARGQYOkZEAEREAEREAEREIGMCUiwMgaq5kRABERABERABERAgqVnQAREQAREQAREQAQyJiDByhiomhMBERABERABERABCZaeAREQAREQAREQARHImIAEK2Ogak4EREAEREAEREAEJFh6BkRABERABERABEQgYwISrIyBqjkREAEREAEREAERkGDpGRABERABERABERCBjAlIsDIGquZEQAREQAREQAREQIKlZ0AEREAEREAEREAEMiYgwcoYqJoTAREQAREQAREQAQmWngEREAEREAEREAERyJiABCtjoGpOBERABERABERABP4fcWceiKHu6IcAAAAASUVORK5CYII=";
            
			final String encodedString = path;
            final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",")  + 1);

            final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);

            mBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
			
			//mBitmap = BitmapFactory.decodeFile(path);
        } catch (Exception ex) {
            callback.error("Error getting bitmap");
            return;
        }

        int nMode = 0;
        int nPaperWidth = 384;
        if(mBitmap != null)
        {
            byte[] data = PrintPicture.POS_PrintBMP(mBitmap, nPaperWidth, nMode);
            sendDataByte(Command.ESC_Init);
            sendDataByte(Command.LF);
            sendDataByte(data);
            sendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(30));
            sendDataByte(PrinterCommand.POS_Set_Cut(1));
            sendDataByte(PrinterCommand.POS_Set_PrtInit());

            callback.success("Image printed.");
        } else {
            callback.error("Bitmap "+path+" is null");
        }
    }
	
	/*
	public static byte[] ToByteArray(String HexString)
    {
        int NumberChars = HexString.length();
        byte[] bytes = new byte[NumberChars / 2];
        for (int i = 0; i < NumberChars; i += 2)
        {
            bytes[i / 2] = HexString.substring(i, 2).getBytes(Charset.forName("UTF-8"));
        }
        return bytes;
    }
	*/
	
	public static byte[] hexStringToBytes(String hexString) {
        hexString = hexString.toLowerCase();
        String[] hexStrings = hexString.split(" ");
        byte[] bytes = new byte[hexStrings.length];
        for (int i = 0; i < hexStrings.length; i++) {
            char[] hexChars = hexStrings[i].toCharArray();
            bytes[i] = (byte) (charToByte(hexChars[0]) << 4 | charToByte(hexChars[1]));
        }
        return bytes;
    }
	
	private static byte charToByte(char c) {
		return (byte) "0123456789abcdef".indexOf(c);
	}
	
}