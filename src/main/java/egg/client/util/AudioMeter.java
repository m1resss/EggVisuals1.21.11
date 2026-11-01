package egg.client.util;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.Arrays;
import java.util.List;

public final class AudioMeter {
    private static volatile float currentPeak = 0.0f;
    private static volatile boolean initialized = false;
    private static volatile boolean active = false;

    public static class GUID extends Structure {
        public int Data1;
        public short Data2;
        public short Data3;
        public byte[] Data4 = new byte[8];

        public GUID() {}

        public GUID(int d1, int d2, int d3, int... b) {
            this.Data1 = d1;
            this.Data2 = (short) d2;
            this.Data3 = (short) d3;
            for (int i = 0; i < 8 && i < b.length; i++) {
                this.Data4[i] = (byte) b[i];
            }
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("Data1", "Data2", "Data3", "Data4");
        }
    }

    private static final GUID CLSID_MMDeviceEnumerator = new GUID(0xBCDE0395, 0xE52F, 0x467C, 0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E);
    private static final GUID IID_IMMDeviceEnumerator = new GUID(0xA95664D2, 0x9614, 0x4F35, 0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6);
    private static final GUID IID_IAudioMeterInformation = new GUID(0xC02216F6, 0x8C67, 0x4B5B, 0x9D, 0x00, 0xD0, 0x08, 0xE7, 0x3E, 0x00, 0x64);

    static {
        Thread worker = new Thread(AudioMeter::pollLoop, "Egg-AudioMeter");
        worker.setDaemon(true);
        worker.start();
    }

    public static float getPeak() {
        return currentPeak;
    }

    public static boolean isActive() {
        return active;
    }

    private static void pollLoop() {
        Function coInit = null;
        Function coCreate = null;
        try {
            coInit = Function.getFunction("ole32", "CoInitialize", Function.ALT_CONVENTION);
            coCreate = Function.getFunction("ole32", "CoCreateInstance", Function.ALT_CONVENTION);
            coInit.invokeInt(new Object[]{Pointer.NULL});
        } catch (Throwable t) {
            return;
        }

        Pointer pMeter = null;
        FloatByReference peakRef = new FloatByReference();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (pMeter == null) {
                    pMeter = initMeter(coCreate);
                    active = (pMeter != null);
                }

                if (pMeter != null) {
                    Pointer vtblMeter = pMeter.getPointer(0);
                    Pointer pGetPeak = vtblMeter.getPointer(3L * Native.POINTER_SIZE);
                    Function getPeakFunc = Function.getFunction(pGetPeak, Function.ALT_CONVENTION);
                    int hr = getPeakFunc.invokeInt(new Object[]{pMeter, peakRef});
                    if (hr == 0) {
                        currentPeak = Math.max(0.0f, Math.min(1.0f, peakRef.getValue()));
                    } else {
                        // Audio device might have changed or disconnected
                        pMeter = null;
                        active = false;
                    }
                } else {
                    currentPeak = 0.0f;
                }

                Thread.sleep(15);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                pMeter = null;
                active = false;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    private static Pointer initMeter(Function coCreate) {
        try {
            PointerByReference ppEnum = new PointerByReference();
            int hr = coCreate.invokeInt(new Object[]{
                    CLSID_MMDeviceEnumerator.getPointer(),
                    Pointer.NULL,
                    1, // CLSCTX_INPROC_SERVER
                    IID_IMMDeviceEnumerator.getPointer(),
                    ppEnum
            });
            if (hr != 0 || ppEnum.getValue() == null) return null;
            Pointer pEnum = ppEnum.getValue();

            // IMMDeviceEnumerator::GetDefaultAudioEndpoint(eRender=0, eConsole=0, &pDevice) -> vtable index 4
            Pointer vtblEnum = pEnum.getPointer(0);
            Pointer pGetDefaultEndpoint = vtblEnum.getPointer(4L * Native.POINTER_SIZE);
            Function getDefaultFunc = Function.getFunction(pGetDefaultEndpoint, Function.ALT_CONVENTION);
            PointerByReference ppDevice = new PointerByReference();
            hr = getDefaultFunc.invokeInt(new Object[]{pEnum, 0, 0, ppDevice});
            if (hr != 0 || ppDevice.getValue() == null) return null;
            Pointer pDevice = ppDevice.getValue();

            // IMMDevice::Activate(IID_IAudioMeterInformation, CLSCTX_ALL=7, NULL, &pMeter) -> vtable index 3
            Pointer vtblDevice = pDevice.getPointer(0);
            Pointer pActivate = vtblDevice.getPointer(3L * Native.POINTER_SIZE);
            Function activateFunc = Function.getFunction(pActivate, Function.ALT_CONVENTION);
            PointerByReference ppMeter = new PointerByReference();
            hr = activateFunc.invokeInt(new Object[]{pDevice, IID_IAudioMeterInformation.getPointer(), 7, Pointer.NULL, ppMeter});
            if (hr != 0 || ppMeter.getValue() == null) return null;

            return ppMeter.getValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private AudioMeter() {}
}
