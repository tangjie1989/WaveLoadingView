package me.itangqi.waveloadingview;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import me.itangqi.library.R;

public class YiXiaWaveLoadingView extends View {
    /**
     * +------------------------+
     * | wave length - 波长      |__________
     * |   /\          |   /\   |  |
     * |  /  \         |  /  \  | amplitude - 振幅
     * | /    \        | /    \ |  |
     * |/      \       |/      \|__|_______
     * |        \      /        |  |
     * |         \    /         |  |
     * |          \  /          |  |
     * |           \/           | water level - 水位
     * |                        |  |
     * |                        |  |
     * +------------------------+__|_______
     */
    private static final float DEFAULT_AMPLITUDE_RATIO = 0.1f;
    private static final float DEFAULT_AMPLITUDE_VALUE = 50.0f;
    private static final float DEFAULT_WATER_LEVEL_RATIO = 0.5f;
    private static final float DEFAULT_WAVE_LENGTH_RATIO = 1.0f;
    private static final float DEFAULT_WAVE_SHIFT_RATIO = 0.0f;
    private static final int DEFAULT_WAVE_PROGRESS_VALUE = 50;
    private static final int DEFAULT_WAVE_COLOR = Color.parseColor("#212121");
    private static final int DEFAULT_WAVE_BACKGROUND_COLOR = Color.parseColor("#00000000");
    private static final float DEFAULT_BORDER_WIDTH = 0;
    // This is incorrect/not recommended by Joshua Bloch in his book Effective Java (2nd ed).
    private static final int DEFAULT_WAVE_SHAPE = ShapeType.CIRCLE.ordinal();
    private static final int DEFAULT_ROUND_RECTANGLE_X_AND_Y = 30;

    public enum ShapeType {
        CIRCLE,
        SQUARE,
        RECTANGLE
    }

    // Dynamic Properties.
    private int mCanvasSize;
    private int mCanvasHeight;
    private int mCanvasWidth;
    private float mAmplitudeRatio;
    private int mWaveBgColor;
    private int mWaveColor;
    private int mShapeType;
    private int mRoundRectangleXY;

    // Properties.
    private float mDefaultWaterLevel;
    private float mWaterLevelRatio = 1f;
    private float mWaveShiftRatio = DEFAULT_WAVE_SHIFT_RATIO;
    private int mProgressValue = DEFAULT_WAVE_PROGRESS_VALUE;

    // Object used to draw.
    // Shader containing repeated waves.
    private BitmapShader mWaveShader;
    // Shader matrix.
    private Matrix mShaderMatrix;
    // Paint to draw wave.
    private Paint mWavePaint;
    //Paint to draw waveBackground.
    private Paint mWaveBgPaint;
    // Paint to draw border.
    private Paint mBorderPaint;

    // Animation.
    private ObjectAnimator waveShiftAnim;
    private AnimatorSet mAnimatorSet;

    private Context mContext;

    //add for draw
    @NonNull
    private RectF drawRectangleRect = new RectF();

    // Constructor & Init Method.
    public YiXiaWaveLoadingView(final Context context) {
        this(context, null);
    }

    public YiXiaWaveLoadingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YiXiaWaveLoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        mContext = context;
        // Init Wave.
        mShaderMatrix = new Matrix();
        mWavePaint = new Paint();
        // The ANTI_ALIAS_FLAG bit AntiAliasing smooths out the edges of what is being drawn,
        // but is has no impact on the interior of the shape.
        mWavePaint.setAntiAlias(true);
        mWaveBgPaint = new Paint();
        mWaveBgPaint.setAntiAlias(true);
        // Init Animation
        initAnimation();

        // Load the styled attributes and set their properties
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.YiXiaWaveLoadingView, defStyleAttr, 0);

        // Init ShapeType
        mShapeType = attributes.getInteger(R.styleable.YiXiaWaveLoadingView_shapeType, DEFAULT_WAVE_SHAPE);

        // Init Wave
        mWaveColor = attributes.getColor(R.styleable.YiXiaWaveLoadingView_waveColor, DEFAULT_WAVE_COLOR);
        mWaveBgColor = attributes.getColor(R.styleable.YiXiaWaveLoadingView_wave_background_Color, DEFAULT_WAVE_BACKGROUND_COLOR);

        mWaveBgPaint.setColor(mWaveBgColor);

        // Init AmplitudeRatio
        float amplitudeRatioAttr = attributes.getFloat(R.styleable.YiXiaWaveLoadingView_waveAmplitude, DEFAULT_AMPLITUDE_VALUE) / 1000;
        mAmplitudeRatio = (amplitudeRatioAttr > DEFAULT_AMPLITUDE_RATIO) ? DEFAULT_AMPLITUDE_RATIO : amplitudeRatioAttr;

        // Init Progress
        mProgressValue = attributes.getInteger(R.styleable.YiXiaWaveLoadingView_progressValue, DEFAULT_WAVE_PROGRESS_VALUE);
        setProgressValue(mProgressValue);

        // Init RoundRectangle
        mRoundRectangleXY = attributes.getInteger(R.styleable.YiXiaWaveLoadingView_round_rectangle_x_and_y, DEFAULT_ROUND_RECTANGLE_X_AND_Y);

        // Init Border
        mBorderPaint = new Paint();
        mBorderPaint.setAntiAlias(true);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(attributes.getDimension(R.styleable.YiXiaWaveLoadingView_borderWidth, dp2px(DEFAULT_BORDER_WIDTH)));
        mBorderPaint.setColor(attributes.getColor(R.styleable.YiXiaWaveLoadingView_borderColor, DEFAULT_WAVE_COLOR));

        attributes.recycle();
    }

    @Override
    public void onDraw(Canvas canvas) {
        mCanvasSize = canvas.getWidth();
        if (canvas.getHeight() < mCanvasSize) {
            mCanvasSize = canvas.getHeight();
        }

        // Draw Wave.
        // Modify paint shader according to mShowWave state.
        if (mWaveShader != null) {
            // First call after mShowWave, assign it to our paint.
            if (mWavePaint.getShader() == null) {
                mWavePaint.setShader(mWaveShader);
            }

            // Sacle shader according to waveLengthRatio and amplitudeRatio.
            // This decides the size(waveLengthRatio for width, amplitudeRatio for height) of waves.
            mShaderMatrix.setScale(1, mAmplitudeRatio / DEFAULT_AMPLITUDE_RATIO, 0, mDefaultWaterLevel);
            // Translate shader according to waveShiftRatio and waterLevelRatio.
            // This decides the start position(waveShiftRatio for x, waterLevelRatio for y) of waves.
            mShaderMatrix.postTranslate(mWaveShiftRatio * getWidth(),
                    (DEFAULT_WATER_LEVEL_RATIO - mWaterLevelRatio) * getHeight());

            // Assign matrix to invalidate the shader.
            mWaveShader.setLocalMatrix(mShaderMatrix);

            // Get borderWidth.
            float borderWidth = mBorderPaint.getStrokeWidth();

            switch (mShapeType) {
                // Draw circle
                case 0:
                    if (borderWidth > 0) {
                        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
                                (getWidth() - borderWidth) / 2f - 1f, mBorderPaint);
                    }

                    float radius = getWidth() / 2f - borderWidth;
                    // Draw background
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, mWaveBgPaint);
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, mWavePaint);
                    break;
                // Draw square
                case 1:
                    if (borderWidth > 0) {
                        canvas.drawRect(
                                borderWidth / 2f,
                                borderWidth / 2f,
                                getWidth() - borderWidth / 2f - 0.5f,
                                getHeight() - borderWidth / 2f - 0.5f,
                                mBorderPaint);
                    }

                    canvas.drawRect(borderWidth, borderWidth, getWidth() - borderWidth,
                            getHeight() - borderWidth, mWaveBgPaint);
                    canvas.drawRect(borderWidth, borderWidth, getWidth() - borderWidth,
                            getHeight() - borderWidth, mWavePaint);
                    break;
                // Draw rectangle
                case 2:
                    if (borderWidth > 0) {
                        drawRectangleRect.left = borderWidth / 2f;
                        drawRectangleRect.top = borderWidth / 2f;
                        drawRectangleRect.right = getWidth() - borderWidth / 2f - 0.5f;
                        drawRectangleRect.bottom = getHeight() - borderWidth / 2f - 0.5f;
//                        RectF rect = new RectF(borderWidth / 2f, borderWidth / 2f, getWidth() - borderWidth / 2f - 0.5f, getHeight() - borderWidth / 2f - 0.5f);
                        canvas.drawRoundRect(drawRectangleRect, mRoundRectangleXY, mRoundRectangleXY, mWaveBgPaint);
                        canvas.drawRoundRect(drawRectangleRect, mRoundRectangleXY, mRoundRectangleXY, mWavePaint);
                        canvas.drawRoundRect(drawRectangleRect, mRoundRectangleXY, mRoundRectangleXY, mBorderPaint);
                    } else {
                        drawRectangleRect.left = 0;
                        drawRectangleRect.top = 0;
                        drawRectangleRect.right = getWidth();
                        drawRectangleRect.bottom = getHeight();
//                        RectF rect = new RectF(0, 0, getWidth(), getHeight());
                        canvas.drawRoundRect(drawRectangleRect, mRoundRectangleXY, mRoundRectangleXY, mWaveBgPaint);
                        canvas.drawRoundRect(drawRectangleRect, mRoundRectangleXY, mRoundRectangleXY, mWavePaint);
                    }
                    break;
                default:
                    break;
            }
        } else {
            mWavePaint.setShader(null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // If shapType is rectangle
        if (getShapeType() == ShapeType.RECTANGLE.ordinal()) {
            mCanvasWidth = w;
            mCanvasHeight = h;
        } else {
            mCanvasSize = w;
            if (h > mCanvasSize)
                mCanvasSize = h;
        }
        updateWaveShader();
    }

    private void updateWaveShader() {
        // IllegalArgumentException: width and height must be > 0 while loading Bitmap from View
        // http://stackoverflow.com/questions/17605662/illegalargumentexception-width-and-height-must-be-0-while-loading-bitmap-from
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (width > 0 && height > 0) {
            double defaultAngularFrequency = 2.0f * Math.PI / DEFAULT_WAVE_LENGTH_RATIO / width;
            float defaultAmplitude = height * DEFAULT_AMPLITUDE_RATIO;
            mDefaultWaterLevel = height * DEFAULT_WATER_LEVEL_RATIO;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint wavePaint = new Paint();
            wavePaint.setStrokeWidth(2);
            wavePaint.setAntiAlias(true);

            // Draw default waves into the bitmap.
            // y=Asin(ωx+φ)+h
            final int endX = width + 1;
            final int endY = height + 1;

            float[] waveY = new float[endX];

            wavePaint.setColor(adjustAlpha(mWaveColor, 0.3f));
            for (int beginX = 0; beginX < endX; beginX++) {
                double wx = beginX * defaultAngularFrequency;
                float beginY = (float) (mDefaultWaterLevel + defaultAmplitude * Math.sin(wx));
                canvas.drawLine(beginX, beginY, beginX, endY, wavePaint);
                waveY[beginX] = beginY;
            }

            wavePaint.setColor(mWaveColor);
            final int wave2Shift = width / 4;
            for (int beginX = 0; beginX < endX; beginX++) {
                canvas.drawLine(beginX, waveY[(beginX + wave2Shift) % endX], beginX, endY, wavePaint);
            }

            // Use the bitamp to create the shader.
            mWaveShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            mWavePaint.setShader(mWaveShader);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = measureWidth(widthMeasureSpec);
        int height = measureHeight(heightMeasureSpec);
        // If shapType is rectangle
        if (getShapeType() == ShapeType.RECTANGLE.ordinal()) {
            System.out.println("onMeasure width : " + width + " height : " + height);
            setMeasuredDimension(width, height);
        } else {
            int imageSize = (width > height) ? width : height;
            System.out.println("onMeasure imageSize : " + imageSize);
            setMeasuredDimension(imageSize, imageSize);
        }

    }

    private int measureWidth(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            // The parent has determined an exact size for the child.
            result = specSize;
        } else if (specMode == MeasureSpec.AT_MOST) {
            // The child can be as large as it wants up to the specified size.
            result = specSize;
        } else {
            // The parent has not imposed any constraint on the child.
            result = mCanvasWidth;
        }
        return result;
    }

    private int measureHeight(int measureSpecHeight) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpecHeight);
        int specSize = MeasureSpec.getSize(measureSpecHeight);

        if (specMode == MeasureSpec.EXACTLY) {
            // We were told how big to be.
            result = specSize;
        } else if (specMode == MeasureSpec.AT_MOST) {
            // The child can be as large as it wants up to the specified size.
            result = specSize;
        } else {
            // Measure the text (beware: ascent is a negative number).
            result = mCanvasHeight;
        }
        return (result + 2);
    }


    public void setWaveBgColor(int color) {
        this.mWaveBgColor = color;
        mWaveBgPaint.setColor(this.mWaveBgColor);
        updateWaveShader();
        invalidate();
    }

    public void setWaveColor(int color) {
        mWaveColor = color;
        // Need to recreate shader when color changed ?
//        mWaveShader = null;
        updateWaveShader();
        invalidate();
    }

    public void setBorderWidth(float width) {
        mBorderPaint.setStrokeWidth(width);
        invalidate();
    }

    public void setBorderColor(int color) {
        mBorderPaint.setColor(color);
        updateWaveShader();
        invalidate();
    }

    public void setShapeType(ShapeType shapeType) {
        mShapeType = shapeType.ordinal();
        requestLayout();
        invalidate();
    }

    public int getShapeType() {
        return mShapeType;
    }

    /**
     * Set vertical size of wave according to amplitudeRatio.
     *
     * @param amplitudeRatio Default to be 0.05. Result of amplitudeRatio + waterLevelRatio should be less than 1.
     */
    public void setAmplitudeRatio(int amplitudeRatio) {
        if (this.mAmplitudeRatio != (float) amplitudeRatio / 1000) {
            this.mAmplitudeRatio = (float) amplitudeRatio / 1000;
            invalidate();
        }
    }

    /**
     * Water level increases from 0 to the value of WaveView.
     *
     * @param progress Default to be 50.
     */
    public void setProgressValue(int progress) {
        mProgressValue = progress;
        ObjectAnimator waterLevelAnim = ObjectAnimator.ofFloat(this, "waterLevelRatio", mWaterLevelRatio, ((float) mProgressValue / 100));
        waterLevelAnim.setDuration(1000);
        waterLevelAnim.setInterpolator(new DecelerateInterpolator());
        AnimatorSet animatorSetProgress = new AnimatorSet();
        animatorSetProgress.play(waterLevelAnim);
        animatorSetProgress.start();
    }

    public void setWaveShiftRatio(float waveShiftRatio) {
        if (this.mWaveShiftRatio != waveShiftRatio) {
            this.mWaveShiftRatio = waveShiftRatio;
            invalidate();
        }
    }

    public void setWaterLevelRatio(float waterLevelRatio) {
        if (this.mWaterLevelRatio != waterLevelRatio) {
            this.mWaterLevelRatio = waterLevelRatio;
            invalidate();
        }
    }

    public void startAnimation() {
        if (mAnimatorSet != null) {
            mAnimatorSet.start();
        }
    }

    public void endAnimation() {
        if (mAnimatorSet != null) {
            mAnimatorSet.end();
        }
    }

    public void cancelAnimation() {
        if (mAnimatorSet != null) {
            mAnimatorSet.cancel();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @SuppressWarnings("deprecation")
    public void pauseAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (mAnimatorSet != null) {
                mAnimatorSet.pause();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @SuppressWarnings("deprecation")
    public void resumeAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (mAnimatorSet != null) {
                mAnimatorSet.resume();
            }
        }
    }

    /**
     * Sets the length of the animation. The default duration is 1000 milliseconds.
     *
     * @param duration The length of the animation, in milliseconds.
     */
    public void setAnimDuration(long duration) {
        waveShiftAnim.setDuration(duration);
    }

    private void initAnimation() {
        // Wave waves infinitely.
        waveShiftAnim = ObjectAnimator.ofFloat(this, "waveShiftRatio", 0f, 1f);
        waveShiftAnim.setRepeatCount(ValueAnimator.INFINITE);
        waveShiftAnim.setDuration(1000);
        waveShiftAnim.setInterpolator(new LinearInterpolator());
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.play(waveShiftAnim);
    }

    @Override
    protected void onAttachedToWindow() {
        startAnimation();
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }

    /**
     * Transparent the given color by the factor
     * The more the factor closer to zero the more the color gets transparent
     *
     * @param color  The color to transparent
     * @param factor 1.0f to 0.0f
     * @return int - A transplanted color
     */
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    /**
     * Paint.setTextSize(float textSize) default unit is px.
     *
     * @param spValue The real size of text
     * @return int - A transplanted sp
     */
    private int sp2px(float spValue) {
        final float fontScale = mContext.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    private int dp2px(float dp) {
        final float scale = mContext.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

}