package JarvisAI.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgcodecs.Imgcodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FocusTracker {

    private static final String FACE_CASCADE = "C:\\opencv\\build\\etc\\haarcascades\\haarcascade_frontalface_default.xml";
    private static final String EYE_CASCADE  = "C:\\opencv\\build\\etc\\haarcascades\\haarcascade_eye.xml";
    private static final String OPENCV_DLL   = "C:\\opencv\\build\\java\\x64\\opencv_java490.dll";

    private VideoCapture camera;
    private CascadeClassifier faceDetector;
    private CascadeClassifier eyeDetector;
    private boolean running = false;
    private int focusScore = 100;
    private long lastFaceSeenTime;
    private static final long DISTRACTION_THRESHOLD_MS = 25000;

    // Gesture tracking
    private String lastGesture = "";
    private long lastGestureTime = 0;
    private static final long GESTURE_COOLDOWN_MS = 2000;

    // Object tracking
    private int frameCount = 0;

    private final Consumer<Integer> onFocusUpdate;
    private final Runnable onDistracted;
    private final Consumer<byte[]> onFrameUpdate;
    private final Consumer<String> onGesture;      // callback for gesture events
    private final Consumer<String> onObjectDetect; // callback for objects detected

    public FocusTracker(Consumer<Integer> onFocusUpdate,
                        Runnable onDistracted,
                        Consumer<byte[]> onFrameUpdate,
                        Consumer<String> onGesture,
                        Consumer<String> onObjectDetect) {
        this.onFocusUpdate   = onFocusUpdate;
        this.onDistracted    = onDistracted;
        this.onFrameUpdate   = onFrameUpdate;
        this.onGesture       = onGesture;
        this.onObjectDetect  = onObjectDetect;
    }

    public boolean initialize() {
        try {
            System.load(OPENCV_DLL);
            System.out.println("OpenCV loaded successfully.");

            boolean opened = false;
            for (int i = 0; i <= 2; i++) {
                System.out.println("Trying camera index " + i + "...");
                camera = new VideoCapture(i);
                if (camera.isOpened()) {
                    Mat test = new Mat();
                    camera.read(test);
                    if (!test.empty()) {
                        System.out.println("Webcam opened at index " + i);
                        opened = true;
                        break;
                    }
                    camera.release();
                }
            }

            if (!opened) { System.out.println("No webcam found."); return false; }

            faceDetector = new CascadeClassifier(FACE_CASCADE);
            eyeDetector  = new CascadeClassifier(EYE_CASCADE);
            if (faceDetector.empty() || eyeDetector.empty()) {
                System.out.println("ERROR: Cascade files failed to load.");
                return false;
            }
            System.out.println("Cascades loaded.");
            return true;
        } catch (Exception e) {
            System.out.println("FocusTracker init error: " + e.getMessage());
            return false;
        }
    }

    public void start() {
        focusScore = 100;
        lastFaceSeenTime = System.currentTimeMillis();
        running = true;

        new Thread(() -> {
            Mat frame = new Mat();
            Mat hsv   = new Mat();
            Mat mask  = new Mat();

            while (running) {
                try {
                    if (!camera.read(frame) || frame.empty()) {
                        Thread.sleep(100); continue;
                    }
                    frameCount++;

                    // Enhance brightness for low light
                    Mat enhanced = new Mat();
                    frame.convertTo(enhanced, -1, 1.3, 20);
                    Mat gray = new Mat();
                    Imgproc.cvtColor(enhanced, gray, Imgproc.COLOR_BGR2GRAY);
                    Imgproc.equalizeHist(gray, gray);

                    // ── FACE DETECTION ──────────────────────────────────────
                    MatOfRect faces = new MatOfRect();
                    faceDetector.detectMultiScale(gray, faces, 1.08, 3, 0,
                            new Size(50, 50), new Size(500, 500));

                    boolean faceVisible = faces.toArray().length > 0;
                    boolean eyesVisible = false;

                    for (Rect faceRect : faces.toArray()) {
                        Imgproc.rectangle(frame, faceRect.tl(), faceRect.br(),
                                new Scalar(255, 212, 0), 2);

                        Rect eyeZone = new Rect(faceRect.x, faceRect.y,
                                faceRect.width, (int)(faceRect.height * 0.55));
                        Mat faceROI = gray.submat(eyeZone);
                        MatOfRect eyes = new MatOfRect();
                        int minEye = (int)(faceRect.width * 0.15);
                        int maxEye = (int)(faceRect.width * 0.45);
                        eyeDetector.detectMultiScale(faceROI, eyes, 1.1, 3, 0,
                                new Size(minEye, minEye), new Size(maxEye, maxEye));

                        Rect[] eyeArr = eyes.toArray();
                        if (eyeArr.length >= 1 && eyeArr.length <= 2) {
                            eyesVisible = true;
                            for (Rect eyeRect : eyeArr) {
                                Point center = new Point(
                                    faceRect.x + eyeRect.x + eyeRect.width / 2.0,
                                    faceRect.y + eyeRect.y + eyeRect.height / 2.0);
                                Imgproc.circle(frame, center, eyeRect.width / 2,
                                        new Scalar(0, 255, 136), 2);
                            }
                        }
                        break;
                    }

                    // ── GESTURE DETECTION (every frame) ─────────────────────
                    // Use skin color segmentation to find hand regions
                    Imgproc.cvtColor(enhanced, hsv, Imgproc.COLOR_BGR2HSV);
                    // Skin tone HSV range
                    Core.inRange(hsv,
                        new Scalar(0, 20, 70),
                        new Scalar(20, 255, 255),
                        mask);

                    // Morphological cleanup
                    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
                    Imgproc.dilate(mask, mask, kernel);
                    Imgproc.erode(mask, mask, kernel);
                    Imgproc.GaussianBlur(mask, mask, new Size(5, 5), 0);

                    // Find contours for hand shape
                    List<MatOfPoint> contours = new ArrayList<>();
                    Mat hierarchy = new Mat();
                    Imgproc.findContours(mask, contours, hierarchy,
                            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                    String gesture = detectGesture(contours, frame, faceVisible, faces.toArray());
                    if (!gesture.isEmpty()) {
                        fireGesture(gesture);
                    }

                    // ── OBJECT DETECTION (every 30 frames ~3sec) ────────────
                    if (frameCount % 30 == 0 && onObjectDetect != null) {
                        String objects = detectObjects(contours, frame, faceVisible);
                        if (!objects.isEmpty()) {
                            final String finalObjects = objects;
                            onObjectDetect.accept(finalObjects);
                        }
                    }

                    // ── FOCUS SCORING ────────────────────────────────────────
                    long timeSinceFace = System.currentTimeMillis() - lastFaceSeenTime;
                    if (faceVisible) {
                        lastFaceSeenTime = System.currentTimeMillis();
                        focusScore = eyesVisible
                            ? Math.min(100, focusScore + 3)
                            : Math.min(100, focusScore + 1);
                    } else {
                        focusScore = Math.max(0, focusScore - 1);
                    }

                    // ── DRAW OVERLAYS ────────────────────────────────────────
                    Scalar scoreColor = focusScore > 60
                        ? new Scalar(0, 255, 136)
                        : focusScore > 30 ? new Scalar(0, 165, 255) : new Scalar(0, 50, 255);

                    Imgproc.putText(frame, "FOCUS: " + focusScore + "%",
                            new Point(8, 24), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, scoreColor, 2);
                    Imgproc.putText(frame,
                            faceVisible ? (eyesVisible ? "EYES: OK" : "FACE: OK") : "NO FACE",
                            new Point(8, 46), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4,
                            faceVisible ? new Scalar(0, 255, 136) : new Scalar(0, 80, 255), 1);

                    if (!lastGesture.isEmpty()) {
                        Imgproc.putText(frame, "GESTURE: " + lastGesture,
                                new Point(8, 68), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4,
                                new Scalar(255, 100, 255), 1);
                    }

                    // Encode and send frame
                    MatOfByte buf = new MatOfByte();
                    Imgcodecs.imencode(".jpg", frame, buf);
                    if (onFocusUpdate != null) onFocusUpdate.accept(focusScore);
                    if (onFrameUpdate != null) onFrameUpdate.accept(buf.toArray());

                    // Distraction alert
                    if (timeSinceFace > DISTRACTION_THRESHOLD_MS && onDistracted != null) {
                        onDistracted.run();
                        lastFaceSeenTime = System.currentTimeMillis();
                    }

                    Thread.sleep(100);
                } catch (Exception e) {
                    System.out.println("Frame error: " + e.getMessage());
                }
            }
            if (camera != null) camera.release();
        }).start();
    }

    // ── GESTURE DETECTION ─────────────────────────────────────────────────
    private String detectGesture(List<MatOfPoint> contours, Mat frame,
                                  boolean faceVisible, Rect[] faces) {
        // Find the largest skin-colored contour (likely a hand)
        // but ignore face regions
        double maxArea = 0;
        MatOfPoint handContour = null;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < 3000 || area > 80000) continue; // too small or too large

            Rect bounds = Imgproc.boundingRect(contour);

            // Skip if this contour overlaps a face region
            boolean isFace = false;
            for (Rect face : faces) {
                if (bounds.x < face.x + face.width && bounds.x + bounds.width > face.x &&
                    bounds.y < face.y + face.height && bounds.y + bounds.height > face.y) {
                    isFace = true; break;
                }
            }
            if (isFace) continue;

            if (area > maxArea) {
                maxArea = area;
                handContour = contour;
            }
        }

        if (handContour == null) return "";

        // Draw hand outline
        Rect handRect = Imgproc.boundingRect(handContour);
        Imgproc.rectangle(frame, handRect.tl(), handRect.br(),
                new Scalar(255, 50, 255), 1);

        // Convex hull to count fingers
        MatOfInt hullIdx = new MatOfInt();
        Imgproc.convexHull(handContour, hullIdx);

        // Count convexity defects (gaps between fingers)
        MatOfInt4 defects = new MatOfInt4();
        try {
            Imgproc.convexityDefects(handContour, hullIdx, defects);
        } catch (Exception e) {
            return "";
        }

        int fingerCount = countFingers(defects, handContour, handRect);

        String gesture = "";
        if (fingerCount == 0)      gesture = "FIST";
        else if (fingerCount == 1) gesture = "POINT";
        else if (fingerCount == 2) gesture = "PEACE";
        else if (fingerCount == 3) gesture = "THREE";
        else if (fingerCount == 4) gesture = "FOUR";
        else if (fingerCount == 5) gesture = "OPEN HAND";

        return gesture;
    }

    private int countFingers(MatOfInt4 defects, MatOfPoint contour, Rect handRect) {
        if (defects.rows() == 0) return 0;

        int[] defectArr = defects.toArray();
        Point[] contourPts = contour.toArray();
        int fingerGaps = 0;

        for (int i = 0; i < defectArr.length; i += 4) {
            Point start  = contourPts[defectArr[i]];
            Point end    = contourPts[defectArr[i + 1]];
            Point far    = contourPts[defectArr[i + 2]];
            double depth = defectArr[i + 3] / 256.0;

            // Only count significant gaps (depth > 20% of hand height)
            if (depth < handRect.height * 0.2) continue;

            // Angle at the defect point — fingers create angles < 90 degrees
            double angle = angleBetween(start, far, end);
            if (angle < 90) fingerGaps++;
        }

        return Math.min(5, fingerGaps + 1); // gaps + 1 = fingers
    }

    private double angleBetween(Point a, Point center, Point b) {
        double ax = a.x - center.x, ay = a.y - center.y;
        double bx = b.x - center.x, by = b.y - center.y;
        double dot  = ax * bx + ay * by;
        double magA = Math.sqrt(ax * ax + ay * ay);
        double magB = Math.sqrt(bx * bx + by * by);
        if (magA == 0 || magB == 0) return 180;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / (magA * magB)))));
    }

    private void fireGesture(String gesture) {
        if (gesture.equals(lastGesture)) return;
        long now = System.currentTimeMillis();
        if (now - lastGestureTime < GESTURE_COOLDOWN_MS) return;
        lastGesture = gesture;
        lastGestureTime = now;
        System.out.println("Gesture detected: " + gesture);
        if (onGesture != null) onGesture.accept(gesture);
    }

    // ── OBJECT DETECTION ─────────────────────────────────────────────────
    // Simple shape-based object detection using contour analysis
    private String detectObjects(List<MatOfPoint> contours, Mat frame, boolean faceVisible) {
        List<String> detected = new ArrayList<>();

        if (faceVisible) detected.add("person");

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < 5000) continue;

            // Approximate the contour shape
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.04 * perimeter, true);

            int sides = approx.rows();
            double aspectRatio = (double) Imgproc.boundingRect(contour).width /
                                          Imgproc.boundingRect(contour).height;

            Rect bounds = Imgproc.boundingRect(contour);

            // Skip face regions
            boolean skip = false;
            // Just check if it's a typical face-sized region at top
            if (bounds.y < frame.rows() / 2 && bounds.width > 60 && bounds.height > 60 && sides > 6) {
                skip = true;
            }
            if (skip) continue;

            String shape = "";
            if (sides == 3) {
                shape = "triangle";
                Imgproc.putText(frame, "△", bounds.tl(), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(100, 255, 100), 1);
            } else if (sides == 4) {
                if (aspectRatio >= 0.85 && aspectRatio <= 1.15) shape = "square";
                else shape = "rectangle";
                Imgproc.rectangle(frame, bounds.tl(), bounds.br(), new Scalar(100, 200, 255), 1);
                Imgproc.putText(frame, shape, bounds.tl(), Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(100, 200, 255), 1);
            } else if (sides > 6) {
                shape = "circle";
                Point center = new Point(bounds.x + bounds.width / 2.0, bounds.y + bounds.height / 2.0);
                Imgproc.circle(frame, center, (bounds.width + bounds.height) / 4, new Scalar(255, 200, 0), 1);
                Imgproc.putText(frame, "○", bounds.tl(), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 200, 0), 1);
            }

            if (!shape.isEmpty() && !detected.contains(shape)) detected.add(shape);
        }

        if (detected.isEmpty()) return "";
        return "Objects in view: " + String.join(", ", detected);
    }

    public void stop() { running = false; }
}