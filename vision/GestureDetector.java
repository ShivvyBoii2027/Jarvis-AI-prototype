package JarvisAI.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.*;
import java.util.function.Consumer;

public class GestureDetector {

    public enum Gesture {
        NONE,
        OPEN_HAND,    // 5 fingers - wave / hello
        THUMBS_UP,    // thumb up - confirm / good
        PEACE,        // 2 fingers - screenshot
        FIST,         // 0 fingers - stop
        POINTING      // 1 finger - yes / select
    }

    private Gesture lastGesture = Gesture.NONE;
    private long lastGestureTime = 0;
    private static final long GESTURE_COOLDOWN_MS = 2000;

    private final Consumer<Gesture> onGesture;

    public GestureDetector(Consumer<Gesture> onGesture) {
        this.onGesture = onGesture;
    }

    public void processFrame(Mat frame) {
        if (frame == null || frame.empty()) return;

        try {
            // Convert to YCrCb for skin detection
            Mat ycrcb = new Mat();
            Imgproc.cvtColor(frame, ycrcb, Imgproc.COLOR_BGR2YCrCb);

            // Skin color range in YCrCb
            Mat skinMask = new Mat();
            Core.inRange(ycrcb,
                new Scalar(0, 133, 77),
                new Scalar(255, 173, 127),
                skinMask);

            // Clean up mask
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            Imgproc.erode(skinMask, skinMask, kernel);
            Imgproc.dilate(skinMask, skinMask, kernel);
            Imgproc.GaussianBlur(skinMask, skinMask, new Size(3, 3), 0);

            // Find contours
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(skinMask, contours, new Mat(),
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            if (contours.isEmpty()) {
                updateGesture(Gesture.NONE);
                return;
            }

            // Find largest contour (the hand)
            MatOfPoint largest = contours.stream()
                    .max(Comparator.comparingDouble(Imgproc::contourArea))
                    .orElse(null);

            if (largest == null || Imgproc.contourArea(largest) < 8000) {
                updateGesture(Gesture.NONE);
                return;
            }

            // Convex hull and defects for finger counting
            MatOfInt hull = new MatOfInt();
            Imgproc.convexHull(largest, hull);

            MatOfInt4 defects = new MatOfInt4();
            MatOfPoint largestForDefects = largest;
            try {
                Imgproc.convexityDefects(largestForDefects, hull, defects);
            } catch (Exception e) {
                updateGesture(Gesture.NONE);
                return;
            }

            int fingerCount = countFingers(defects, largest);

            // Draw on frame
            drawGestureOverlay(frame, largest, hull, fingerCount);

            // Map finger count to gesture
            Gesture detected;
            if (fingerCount == 0)      detected = Gesture.FIST;
            else if (fingerCount == 1) detected = Gesture.POINTING;
            else if (fingerCount == 2) detected = Gesture.PEACE;
            else if (fingerCount >= 4) detected = Gesture.OPEN_HAND;
            else                       detected = Gesture.THUMBS_UP;

            updateGesture(detected);

        } catch (Exception e) {
            // Silently ignore frame processing errors
        }
    }

    private int countFingers(MatOfInt4 defects, MatOfPoint contour) {
        if (defects.rows() == 0) return 0;

        int count = 0;
        int[] defectArr = defects.toArray();
        Point[] contourPts = contour.toArray();

        for (int i = 0; i < defectArr.length; i += 4) {
            Point start = contourPts[defectArr[i]];
            Point end   = contourPts[defectArr[i + 1]];
            Point far   = contourPts[defectArr[i + 2]];
            float depth = defectArr[i + 3] / 256f;

            if (depth > 20) {
                // Angle at the fingertip
                double a = dist(end, far);
                double b = dist(start, far);
                double c = dist(start, end);
                double angle = Math.acos((b * b + c * c - a * a) / (2 * b * c));

                if (angle < Math.toRadians(90)) {
                    count++;
                }
            }
        }
        return Math.min(count + 1, 5);
    }

    private double dist(Point a, Point b) {
        double dx = a.x - b.x, dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void drawGestureOverlay(Mat frame, MatOfPoint contour, MatOfInt hull, int fingerCount) {
        // Draw hand contour in green
        List<MatOfPoint> contours = Collections.singletonList(contour);
        Imgproc.drawContours(frame, contours, 0, new Scalar(0, 255, 136), 2);

        // Draw gesture label
        String label = fingerCount + " finger" + (fingerCount != 1 ? "s" : "");
        Imgproc.putText(frame, label, new Point(10, 50),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 212, 255), 2);
    }

    private void updateGesture(Gesture gesture) {
        if (gesture == Gesture.NONE) return;
        if (gesture == lastGesture) return;

        long now = System.currentTimeMillis();
        if (now - lastGestureTime < GESTURE_COOLDOWN_MS) return;

        lastGesture = gesture;
        lastGestureTime = now;

        if (onGesture != null) onGesture.accept(gesture);
    }

    public static String gestureName(Gesture g) {
        switch (g) {
            case OPEN_HAND: return "✋ Open Hand — Hello!";
            case THUMBS_UP: return "👍 Thumbs Up — Confirmed!";
            case PEACE:     return "✌ Peace Sign — Taking screenshot!";
            case FIST:      return "✊ Fist — Stopping current action.";
            case POINTING:  return "☝ Pointing — Yes, got it!";
            default:        return "";
        }
    }
}