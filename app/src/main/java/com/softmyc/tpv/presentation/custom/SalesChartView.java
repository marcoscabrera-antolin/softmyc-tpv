package com.softmyc.tpv.presentation.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SalesChartView extends View {

    private List<Double> values = new ArrayList<>();
    private List<String> labels = new ArrayList<>();
    private float animProgress = 0f;
    private int selectedIndex = -1;

    private Paint linePaint;
    private Paint fillPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint pointPaint;
    private Paint selectPaint;
    private Paint cardPaint;

    // P1: Objetos preasignados para evitar recolección de basura en onDraw() (60fps)
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private LinearGradient fillGradient = null;
    private float lastChartHeight = -1f;
    private float lastPaddingTop = -1f;

    public SalesChartView(Context context) {
        super(context);
        init();
    }

    public SalesChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SalesChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // En Android, para ver sombras difuminadas (setShadowLayer) hace falta desactivar aceleración en hardware en esta vista
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setColor(Color.parseColor("#06B6D4")); // Cyan
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setShadowLayer(16f, 0f, 0f, Color.parseColor("#06B6D4"));

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
        gridPaint.setColor(Color.parseColor("#334155")); // Slate 700
        gridPaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#94A3B8")); // Slate 400
        textPaint.setTextSize(26f);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(Color.parseColor("#06B6D4"));

        selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectPaint.setStyle(Paint.Style.STROKE);
        selectPaint.setStrokeWidth(2f);
        selectPaint.setColor(Color.parseColor("#64748B")); // Slate 500
        selectPaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0f));

        cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(Color.parseColor("#0F172A")); // Slate 900
        cardPaint.setShadowLayer(8f, 0f, 4f, Color.BLACK);
    }

    public void setData(List<Double> values, List<String> labels) {
        this.values = values != null ? values : new ArrayList<>();
        this.labels = labels != null ? labels : new ArrayList<>();
        this.selectedIndex = -1;
        startAnimation();
    }

    private void startAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (values == null || values.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No hay datos de ventas para este periodo", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        int N = values.size();
        float paddingLeft = 110f;
        float paddingRight = 40f;
        float paddingTop = 60f;
        float paddingBottom = 70f;

        float chartWidth = getWidth() - paddingLeft - paddingRight;
        float chartHeight = getHeight() - paddingTop - paddingBottom;

        // Calcular el pico máximo de facturación
        double maxVal = 10.0; // mínimo para evitar divisiones entre cero
        for (Double val : values) {
            if (val > maxVal) {
                maxVal = val;
            }
        }
        // Redondear maxVal hacia arriba por estética
        maxVal = Math.ceil(maxVal / 10.0) * 10.0;

        // 1. Dibujar líneas de cuadrícula horizontales y etiquetas Y
        textPaint.setTextAlign(Paint.Align.RIGHT);
        int divisions = 3;
        for (int i = 0; i <= divisions; i++) {
            double divValue = (maxVal / divisions) * i;
            float y = paddingTop + chartHeight - (float) ((divValue * chartHeight) / maxVal);
            
            // Cuadrícula horizontal
            if (i > 0 && i < divisions) {
                canvas.drawLine(paddingLeft, y, getWidth() - paddingRight, y, gridPaint);
            }
            
            // Texto label Y
            String labelY = String.format(Locale.getDefault(), "%.1f€", divValue);
            canvas.drawText(labelY, paddingLeft - 15f, y + 8f, textPaint);
        }

        // 2. Dibujar etiquetas de días en el eje X
        textPaint.setTextAlign(Paint.Align.CENTER);
        int step = Math.max(1, N / 5); // Evitar colisión de textos si son 30 días
        for (int i = 0; i < N; i++) {
            if (N <= 8 || i % step == 0 || i == N - 1) {
                float x = paddingLeft + i * (chartWidth / (N - 1));
                canvas.drawText(labels.get(i), x, getHeight() - 15f, textPaint);
            }
        }

        // 3. Generar curvas spline para el gráfico (reutilizando paths preasignados)
        linePath.reset();
        fillPath.reset();

        float startX = paddingLeft;
        float startY = paddingTop + chartHeight - (float) (((values.get(0) * chartHeight) / maxVal) * animProgress);
        linePath.moveTo(startX, startY);
        fillPath.moveTo(startX, paddingTop + chartHeight);
        fillPath.lineTo(startX, startY);

        for (int i = 1; i < N; i++) {
            float prevX = paddingLeft + (i - 1) * (chartWidth / (N - 1));
            float prevY = paddingTop + chartHeight - (float) (((values.get(i - 1) * chartHeight) / maxVal) * animProgress);

            float currX = paddingLeft + i * (chartWidth / (N - 1));
            float currY = paddingTop + chartHeight - (float) (((values.get(i) * chartHeight) / maxVal) * animProgress);

            float controlX1 = prevX + (currX - prevX) / 2f;
            float controlY1 = prevY;
            float controlX2 = prevX + (currX - prevX) / 2f;
            float controlY2 = currY;

            linePath.cubicTo(controlX1, controlY1, controlX2, controlY2, currX, currY);
            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, currX, currY);
        }

        float endX = paddingLeft + (N - 1) * (chartWidth / (N - 1));
        fillPath.lineTo(endX, paddingTop + chartHeight);
        fillPath.close();

        // Aplicar el gradiente lineal de relleno solo si cambian las dimensiones
        if (fillGradient == null || lastChartHeight != chartHeight || lastPaddingTop != paddingTop) {
            lastChartHeight = chartHeight;
            lastPaddingTop = paddingTop;
            fillGradient = new LinearGradient(
                    0, paddingTop,
                    0, paddingTop + chartHeight,
                    Color.parseColor("#4406B6D4"), // Azul semitransparente
                    Color.parseColor("#00000000"), // Totalmente transparente
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillGradient);
        }

        // 4. Dibujar áreas rellenas y las líneas
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // 5. Dibujar círculos en los nodos de datos (puntos clave)
        for (int i = 0; i < N; i++) {
            float x = paddingLeft + i * (chartWidth / (N - 1));
            float y = paddingTop + chartHeight - (float) (((values.get(i) * chartHeight) / maxVal) * animProgress);
            
            // Círculo base
            canvas.drawCircle(x, y, 6f, pointPaint);
        }

        // 6. Línea vertical interactiva y Tooltip al pulsar
        if (selectedIndex >= 0 && selectedIndex < N) {
            float selX = paddingLeft + selectedIndex * (chartWidth / (N - 1));
            float selY = paddingTop + chartHeight - (float) (((values.get(selectedIndex) * chartHeight) / maxVal) * animProgress);

            // Línea guía vertical
            canvas.drawLine(selX, paddingTop, selX, paddingTop + chartHeight, selectPaint);

            // Resaltar punto activo
            pointPaint.setColor(Color.WHITE);
            canvas.drawCircle(selX, selY, 10f, pointPaint);
            pointPaint.setColor(Color.parseColor("#06B6D4"));
            canvas.drawCircle(selX, selY, 6f, pointPaint);

            // Dibujar Tooltip flotante con detalles
            String tooltipText = String.format(Locale.getDefault(), "%s: %.2f €", labels.get(selectedIndex), values.get(selectedIndex));
            textPaint.setTextAlign(Paint.Align.LEFT);
            float textLength = textPaint.measureText(tooltipText);
            float tooltipW = textLength + 40f;
            float tooltipH = 70f;

            // Centrar tooltip sobre el cursor del nodo
            float rectLeft = selX - (tooltipW / 2f);
            float rectTop = selY - tooltipH - 25f;

            // Evitar que el panel se salga por los bordes laterales
            if (rectLeft < paddingLeft) rectLeft = paddingLeft;
            if (rectLeft + tooltipW > getWidth() - paddingRight) {
                rectLeft = getWidth() - paddingRight - tooltipW;
            }
            // Evitar que se oculte por el techo
            if (rectTop < 10) rectTop = selY + 25f;

            RectF rect = new RectF(rectLeft, rectTop, rectLeft + tooltipW, rectTop + tooltipH);
            canvas.drawRoundRect(rect, 10f, 10f, cardPaint);

            // Texto del tooltip
            textPaint.setColor(Color.parseColor("#F8FAFC")); // slate 50
            canvas.drawText(tooltipText, rectLeft + 20f, rectTop + (tooltipH / 2f) + 8f, textPaint);
            textPaint.setColor(Color.parseColor("#94A3B8")); // Restaurar slate 400
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (values == null || values.isEmpty()) return false;

        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float xVal = event.getX();
            int N = values.size();
            float paddingLeft = 110f;
            float paddingRight = 40f;
            float chartWidth = getWidth() - paddingLeft - paddingRight;

            // Encontrar el punto más cercano horizontalmente
            float clickOffset = xVal - paddingLeft;
            float stepSize = chartWidth / (N - 1);
            int index = Math.round(clickOffset / stepSize);

            if (index < 0) index = 0;
            if (index >= N) index = N - 1;

            if (index != selectedIndex) {
                selectedIndex = index;
                invalidate();
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Mantener el tooltip visible unos segundos o quitarlo inmediatamente.
            // Quitarlo inmediatamente al levantar el dedo es lo más intuitivo.
            selectedIndex = -1;
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
