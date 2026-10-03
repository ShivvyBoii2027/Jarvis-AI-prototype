package JarvisAI.vision;

import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.core.*;

/**
 * Shared access to the latest webcam frame.
 * FocusTracker updates this every 100ms.
 * VisionCommand and GestureDetector read from it.
 */
public class CameraManager {

    private static volatile byte[] latestFrame = null;
    private static volatile Mat latestMat = null;

    public static void updateFrame(byte[] jpegBytes, Mat mat) {
        latestFrame = jpegBytes;
        latestMat = mat.clone();
    }

    /** Returns the latest JPEG frame bytes, or null if no frame yet */
    public static byte[] captureFrame() {
        return latestFrame;
    }

    /** Returns the latest raw Mat frame, or null if no frame yet */
    public static Mat captureRawFrame() {
        return latestMat;
    }
}