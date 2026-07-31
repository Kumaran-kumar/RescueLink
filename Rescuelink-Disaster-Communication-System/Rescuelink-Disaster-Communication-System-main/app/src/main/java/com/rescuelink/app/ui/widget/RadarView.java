package com.rescuelink.app.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RadarView extends View {

    private Paint gridPaint;
    private Paint sweepPaint;
    private Paint nodePaint;
    private float sweepAngle = 0f;
    private ValueAnimator animator;
    private int deviceCount = 0;
    
    private List<Node> nodes = new ArrayList<>();
    private Random random = new Random();

    public RadarView(Context context) {
        super(context);
        init();
    }

    public RadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#1FFFFFFF")); // Subtle white/cyan lines
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(3f);

        sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sweepPaint.setStyle(Paint.Style.FILL);

        nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodePaint.setStyle(Paint.Style.FILL);

        startAnimation();
    }

    private void startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(3000); // 3 seconds per rotation
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            sweepAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setDeviceCount(int count) {
        if (this.deviceCount != count) {
            this.deviceCount = count;
            updateNodes();
        }
    }

    private void updateNodes() {
        nodes.clear();
        for (int i = 0; i < deviceCount; i++) {
            // Randomly place nodes on the radar (as a hackathon visual representation)
            float angle = random.nextFloat() * 360f;
            float distance = 0.2f + random.nextFloat() * 0.7f; // between 20% and 90% of radius
            nodes.add(new Node(angle, distance));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        float radius = Math.min(cx, cy) - 20f;

        // Draw concentric circles (Grid)
        canvas.drawCircle(cx, cy, radius, gridPaint);
        canvas.drawCircle(cx, cy, radius * 0.66f, gridPaint);
        canvas.drawCircle(cx, cy, radius * 0.33f, gridPaint);
        
        // Draw crosshairs
        canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint);

        // Draw sweeping radar
        int[] sweepColors = {Color.TRANSPARENT, Color.parseColor("#00E676")};
        float[] sweepPositions = {0f, 0.25f};
        SweepGradient sweepGradient = new SweepGradient(cx, cy, sweepColors, sweepPositions);
        
        canvas.save();
        canvas.rotate(sweepAngle, cx, cy);
        sweepPaint.setShader(sweepGradient);
        canvas.drawCircle(cx, cy, radius, sweepPaint);
        canvas.restore();

        // Draw nodes depending on the theme (Utilitarian Cyber-Minimalism)
        int colorBase = deviceCount > 0 ? Color.parseColor("#00E676") : Color.parseColor("#FF1744");
        
        for (Node node : nodes) {
            float nodeCx = cx + (float) (Math.cos(Math.toRadians(node.angle)) * radius * node.distance);
            float nodeCy = cy + (float) (Math.sin(Math.toRadians(node.angle)) * radius * node.distance);
            
            // Draw a glowing node
            RadialGradient glow = new RadialGradient(nodeCx, nodeCy, 15f, colorBase, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            nodePaint.setShader(glow);
            canvas.drawCircle(nodeCx, nodeCy, 15f, nodePaint);
            
            // Core
            nodePaint.setShader(null);
            nodePaint.setColor(Color.WHITE);
            canvas.drawCircle(nodeCx, nodeCy, 5f, nodePaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    private static class Node {
        float angle;
        float distance;
        Node(float angle, float distance) {
            this.angle = angle;
            this.distance = distance;
        }
    }
}
