package com.wise.wisescreenshot;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.hardware.display.IDisplayManager;
import android.hardware.input.InputManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.view.InputDeviceCompat;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by kuanghaochuan on 2017/7/13.
 */

public class PhoneClient {
    private static final String DOWN = "DOWN";
    private static final String UP = "UP";
    private static final String MOVE = "MOVE";

    private static final String MENU = "MENU";
    private static final String HOME = "HOME";
    private static final String BACK = "BACK";

    private static InputManager im;
    private static Method injectInputEventMethod;
    private static long downTime;
    private static float scale = 0.5f;
    private static int rotation = 0;
    private static LocalServerSocket mLocalServerSocket;

    public static void main(String[] args) {
        System.out.println("Phone client start");
        try {
            startLocalServerSocket();
        } catch (Exception e) {
            System.out.println("Phone client start error");
            System.out.println(e.getMessage());
        }
    }

    private static void startLocalServerSocket() throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        System.out.println("start local server socket");
        mLocalServerSocket = new LocalServerSocket("wisescreenshot");
        init();

        while (true) {
            System.out.println("listening...");
            try {
                LocalSocket localSocket = mLocalServerSocket.accept();
                handleLocalSocket(localSocket);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                mLocalServerSocket = new LocalServerSocket("wisescreenshot");
            }
        }
    }

    private static void init() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        im = (InputManager) InputManager.class.getDeclaredMethod("getInstance", new Class[0]).invoke(null);
        MotionEvent.class.getDeclaredMethod("obtain").setAccessible(true);
        injectInputEventMethod = InputManager.class.getMethod("injectInputEvent", InputEvent.class, Integer.TYPE);
    }

    private static void handleLocalSocket(LocalSocket localSocket) {
        readSocket(localSocket);
        writeSocket(localSocket);
    }

    private static void writeSocket(final LocalSocket localSocket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(localSocket.getOutputStream());
                    DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
                    while (true) {
                        int x = getCurrentDisplaySize().x;
                        int y = getCurrentDisplaySize().y;

                        dataOutputStream.writeInt(x);
                        dataOutputStream.writeInt(y);

                        Bitmap bitmap = screenshot();
                        if (bitmap == null) {
                            return;
                        }
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream);

                        dataOutputStream.writeInt(byteArrayOutputStream.size());

                        bufferedOutputStream.write(byteArrayOutputStream.toByteArray());
                        bufferedOutputStream.flush();
                    }
                } catch (Exception e) {
                    System.out.println("write socket " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void readSocket(final LocalSocket localSocket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(localSocket.getInputStream()));
                    while (true) {
                        String line;
                        try {
                            line = reader.readLine();
                            if (line == null) {
                                return;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                        try {
                            if (line.startsWith(DOWN)) {
                                handleDown(line.substring(DOWN.length()));
                            } else if (line.startsWith(MOVE)) {
                                handleMove(line.substring(MOVE.length()));
                            } else if (line.startsWith(UP)) {
                                handleUp(line.substring(UP.length()));
                            } else if (line.startsWith(MENU)) {
                                pressMenu();
                            } else if (line.startsWith(HOME)) {
                                pressHome();
                            } else if (line.startsWith(BACK)) {
                                pressBack();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void handleUp(String line) {
        Point point = getXY(line);
        if (point != null) {
            try {
                touchUp(point.x, point.y);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleMove(String line) {
        Point point = getXY(line);
        if (point != null) {
            try {
                touchMove(point.x, point.y);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleDown(String line) {
        Point point = getXY(line);
        if (point != null) {
            try {
                touchDown(point.x, point.y);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Point getXY(String input) {
        try {
            Point point = new Point();
            String[] s = input.split("#");
            point.x = Integer.parseInt(s[0]);
            point.y = Integer.parseInt(s[1]);
            return point;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void pressMenu() throws InvocationTargetException, IllegalAccessException {
        sendKeyEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_KEYBOARD, KeyEvent.KEYCODE_MENU, false);
    }

    private static void pressHome() throws InvocationTargetException, IllegalAccessException {
        sendKeyEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_KEYBOARD, KeyEvent.KEYCODE_HOME, false);
    }

    private static void pressBack() throws InvocationTargetException, IllegalAccessException {
        sendKeyEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_KEYBOARD, KeyEvent.KEYCODE_BACK, false);
    }

    private static void touchDown(float clientX, float clientY) throws InvocationTargetException, IllegalAccessException {
        downTime = SystemClock.uptimeMillis();
        injectMotionEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_DOWN, downTime, downTime, clientX, clientY, 1.0f);
    }

    private static void touchUp(float clientX, float clientY) throws InvocationTargetException, IllegalAccessException {
        injectMotionEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), clientX, clientY, 1.0f);
    }

    private static void touchMove(float clientX, float clientY) throws InvocationTargetException, IllegalAccessException {
        injectMotionEvent(im, injectInputEventMethod, InputDeviceCompat.SOURCE_TOUCHSCREEN, MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis(), clientX, clientY, 1.0f);
    }

    private static void injectMotionEvent(InputManager im, Method injectInputEventMethod, int inputSource, int action, long downTime, long eventTime, float x, float y, float pressure) throws InvocationTargetException, IllegalAccessException {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, pressure, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(inputSource);
        injectInputEventMethod.invoke(im, event, Integer.valueOf(0));
    }

    private static void injectKeyEvent(InputManager im, Method injectInputEventMethod, KeyEvent event) throws InvocationTargetException, IllegalAccessException {
        injectInputEventMethod.invoke(im, event, Integer.valueOf(0));
    }

    private static void sendKeyEvent(InputManager im, Method injectInputEventMethod, int inputSource, int keyCode, boolean shift) throws InvocationTargetException, IllegalAccessException {
        long now = SystemClock.uptimeMillis();
        int meta = shift ? 1 : 0;
        injectKeyEvent(im, injectInputEventMethod, new KeyEvent(now, now, 0, keyCode, 0, meta, -1, 0, 0, inputSource));
        injectKeyEvent(im, injectInputEventMethod, new KeyEvent(now, now, 1, keyCode, 0, meta, -1, 0, 0, inputSource));
    }

    /**
     * 进行图像截取
     *
     * @throws Exception
     */
    private static Bitmap screenshot() throws Exception {
        try {
            String surfaceClassName;
            Point size = getCurrentDisplaySize();

            size.x *= scale;
            size.y *= scale;

            if (Build.VERSION.SDK_INT <= 17) {
                surfaceClassName = "android.view.Surface";
            } else {
                surfaceClassName = "android.view.SurfaceControl";
            }
            Bitmap bitmap = (Bitmap) Class.forName(surfaceClassName).getDeclaredMethod("screenshot", new Class[]{Integer.TYPE, Integer.TYPE}).invoke(null, Integer.valueOf(size.x), Integer.valueOf(size.y));
            if (rotation == 0) {
                return bitmap;
            }
            Matrix m = new Matrix();
            if (Surface.ROTATION_90 == rotation) {
                m.postRotate(-90.0f);
            } else if (Surface.ROTATION_180 == rotation) {
                m.postRotate(-180.0f);
            } else if (Surface.ROTATION_270 == rotation) {
                m.postRotate(-270.0f);
            }
            return Bitmap.createBitmap(bitmap, 0, 0, size.x, size.y, m, false);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    /**
     * 获取屏幕的高度和宽度
     */
    private static Point getCurrentDisplaySize() {
        try {
            Method getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
            Point point = new Point();
            IWindowManager wm;
            if (Build.VERSION.SDK_INT >= 18) {
                wm = IWindowManager.Stub.asInterface((IBinder) getServiceMethod.invoke(null, "window"));
                wm.getInitialDisplaySize(0, point);
                rotation = wm.getRotation();
            } else if (Build.VERSION.SDK_INT == 17) {
                DisplayInfo di = IDisplayManager.Stub.asInterface((IBinder) getServiceMethod.invoke(null, "display")).getDisplayInfo(0);
                point.x = ((Integer) DisplayInfo.class.getDeclaredField("logicalWidth").get(di)).intValue();
                point.y = ((Integer) DisplayInfo.class.getDeclaredField("logicalHeight").get(di)).intValue();
                rotation = ((Integer) DisplayInfo.class.getDeclaredField("rotation").get(di)).intValue();
            } else {
                wm = IWindowManager.Stub.asInterface((IBinder) getServiceMethod.invoke(null, "window"));
                wm.getRealDisplaySize(point);
                rotation = wm.getRotation();
            }
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                int temp = point.x;
                point.x = point.y;
                point.y = temp;
            }
            return point;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
