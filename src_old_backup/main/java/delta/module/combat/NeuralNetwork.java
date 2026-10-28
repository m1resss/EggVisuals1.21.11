package delta.module.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Random;

public class NeuralNetwork {
    public static final int INPUT_SIZE = 18;
    public static final int HIDDEN1_SIZE = 28;
    public static final int HIDDEN2_SIZE = 18;
    public static final int OUTPUT_SIZE = 5; // 0: offsetX, 1: offsetY, 2: offsetZ, 3: attack, 4: wTap

    private float[][] weights1 = new float[INPUT_SIZE][HIDDEN1_SIZE];
    private float[] bias1 = new float[HIDDEN1_SIZE];
    private float[][] dWeights1 = new float[INPUT_SIZE][HIDDEN1_SIZE];

    private float[][] weights2 = new float[HIDDEN1_SIZE][HIDDEN2_SIZE];
    private float[] bias2 = new float[HIDDEN2_SIZE];
    private float[][] dWeights2 = new float[HIDDEN1_SIZE][HIDDEN2_SIZE];

    private float[][] weights3 = new float[HIDDEN2_SIZE][OUTPUT_SIZE];
    private float[] bias3 = new float[OUTPUT_SIZE];
    private float[][] dWeights3 = new float[HIDDEN2_SIZE][OUTPUT_SIZE];

    private final Random random = new Random(42);
    private int trainedSamples = 0;
    private float lastLoss = 0.0f;

    public NeuralNetwork() {
        initializeWeights();
    }

    public synchronized void initializeWeights() {
        initHe(this.weights1, INPUT_SIZE, HIDDEN1_SIZE);
        initHe(this.weights2, HIDDEN1_SIZE, HIDDEN2_SIZE);
        initXavier(this.weights3, HIDDEN2_SIZE, OUTPUT_SIZE);

        for (int i = 0; i < HIDDEN1_SIZE; i++) {
            bias1[i] = 0.01f;
            for (int j = 0; j < INPUT_SIZE; j++) dWeights1[j][i] = 0.0f;
        }
        for (int i = 0; i < HIDDEN2_SIZE; i++) {
            bias2[i] = 0.01f;
            for (int j = 0; j < HIDDEN1_SIZE; j++) dWeights2[j][i] = 0.0f;
        }
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            bias3[i] = 0.0f;
            for (int j = 0; j < HIDDEN2_SIZE; j++) dWeights3[j][i] = 0.0f;
        }

        injectPriors();
    }

    private void initHe(float[][] w, int in, int out) {
        float std = (float) Math.sqrt(2.0 / in);
        for (int i = 0; i < in; i++) {
            for (int j = 0; j < out; j++) {
                w[i][j] = (float) (random.nextGaussian() * std * 0.5);
            }
        }
    }

    private void initXavier(float[][] w, int in, int out) {
        float std = (float) Math.sqrt(2.0 / (in + out));
        for (int i = 0; i < in; i++) {
            for (int j = 0; j < out; j++) {
                w[i][j] = (float) (random.nextGaussian() * std * 0.5);
            }
        }
    }

    private void injectPriors() {
        // Natural upward height bias (aiming at upper torso / head)
        bias3[1] = 0.15f;
    }

    private float leakyRelu(float x) {
        return x > 0.0f ? Math.min(x, 10.0f) : 0.05f * Math.max(x, -10.0f);
    }

    private float leakyReluDeriv(float x) {
        return x > 0.0f ? 1.0f : 0.05f;
    }

    private float sigmoid(float x) {
        float clamped = Math.max(-10.0f, Math.min(10.0f, x));
        return 1.0f / (1.0f + (float) Math.exp(-clamped));
    }

    private float tanh(float x) {
        float clamped = Math.max(-10.0f, Math.min(10.0f, x));
        return (float) Math.tanh(clamped);
    }

    private float tanhDeriv(float tanhVal) {
        return Math.max(0.01f, 1.0f - (tanhVal * tanhVal));
    }

    public boolean hasNaN() {
        for (float[] row : weights1) for (float v : row) if (Float.isNaN(v) || Float.isInfinite(v)) return true;
        for (float[] row : weights2) for (float v : row) if (Float.isNaN(v) || Float.isInfinite(v)) return true;
        for (float[] row : weights3) for (float v : row) if (Float.isNaN(v) || Float.isInfinite(v)) return true;
        for (float v : bias1) if (Float.isNaN(v) || Float.isInfinite(v)) return true;
        for (float v : bias2) if (Float.isNaN(v) || Float.isInfinite(v)) return true;
        for (float v : bias3) if (Float.isNaN(v) || Float.isInfinite(v)) return true;
        return false;
    }

    public synchronized float[] predict(float[] rawInput) {
        if (hasNaN()) {
            initializeWeights();
        }

        float[] input = new float[INPUT_SIZE];
        for (int i = 0; i < INPUT_SIZE; i++) {
            float v = (i < rawInput.length) ? rawInput[i] : 0.0f;
            if (Float.isNaN(v) || Float.isInfinite(v)) v = 0.0f;
            input[i] = Math.max(-5.0f, Math.min(5.0f, v));
        }

        float[] h1 = new float[HIDDEN1_SIZE];
        for (int j = 0; j < HIDDEN1_SIZE; j++) {
            float sum = bias1[j];
            for (int i = 0; i < INPUT_SIZE; i++) sum += input[i] * weights1[i][j];
            h1[j] = leakyRelu(sum);
        }

        float[] h2 = new float[HIDDEN2_SIZE];
        for (int j = 0; j < HIDDEN2_SIZE; j++) {
            float sum = bias2[j];
            for (int i = 0; i < HIDDEN1_SIZE; i++) sum += h1[i] * weights2[i][j];
            h2[j] = leakyRelu(sum);
        }

        float[] output = new float[OUTPUT_SIZE];
        for (int j = 0; j < OUTPUT_SIZE; j++) {
            float sum = bias3[j];
            for (int i = 0; i < HIDDEN2_SIZE; i++) sum += h2[i] * weights3[i][j];
            if (j < 3) {
                // 3D Axis Displacement Offsets (dx, dy, dz) bounded strictly to [-1.0, 1.0]
                output[j] = tanh(sum);
            } else {
                // Action triggers (Attack, W-Tap): Sigmoid probability [0.0, 1.0]
                output[j] = sigmoid(sum);
            }
            if (Float.isNaN(output[j]) || Float.isInfinite(output[j])) {
                output[j] = 0.0f;
            }
        }

        return output;
    }

    public synchronized float train(float[] rawInput, float[] rawExpected, float lr) {
        if (hasNaN()) {
            initializeWeights();
        }

        float[] input = new float[INPUT_SIZE];
        for (int i = 0; i < INPUT_SIZE; i++) {
            float v = (i < rawInput.length) ? rawInput[i] : 0.0f;
            if (Float.isNaN(v) || Float.isInfinite(v)) v = 0.0f;
            input[i] = Math.max(-5.0f, Math.min(5.0f, v));
        }

        float[] expected = new float[OUTPUT_SIZE];
        for (int j = 0; j < OUTPUT_SIZE; j++) {
            float v = (j < rawExpected.length) ? rawExpected[j] : 0.0f;
            if (Float.isNaN(v) || Float.isInfinite(v)) v = 0.0f;
            expected[j] = Math.max(-1.0f, Math.min(1.0f, v));
        }

        float momentum = 0.75f;
        float weightDecay = 0.9998f;

        // Layer 1 Forward
        float[] z1 = new float[HIDDEN1_SIZE];
        float[] a1 = new float[HIDDEN1_SIZE];
        for (int j = 0; j < HIDDEN1_SIZE; j++) {
            float sum = bias1[j];
            for (int i = 0; i < INPUT_SIZE; i++) sum += input[i] * weights1[i][j];
            z1[j] = sum;
            a1[j] = leakyRelu(sum);
        }

        // Layer 2 Forward
        float[] z2 = new float[HIDDEN2_SIZE];
        float[] a2 = new float[HIDDEN2_SIZE];
        for (int j = 0; j < HIDDEN2_SIZE; j++) {
            float sum = bias2[j];
            for (int i = 0; i < HIDDEN1_SIZE; i++) sum += a1[i] * weights2[i][j];
            z2[j] = sum;
            a2[j] = leakyRelu(sum);
        }

        // Output Layer Forward
        float[] out = new float[OUTPUT_SIZE];
        for (int j = 0; j < OUTPUT_SIZE; j++) {
            float sum = bias3[j];
            for (int i = 0; i < HIDDEN2_SIZE; i++) sum += a2[i] * weights3[i][j];
            if (j < 3) {
                out[j] = tanh(sum);
            } else {
                out[j] = sigmoid(sum);
            }
        }

        // Error & Output Gradients (with clipping)
        float[] outGrad = new float[OUTPUT_SIZE];
        float totalLoss = 0.0f;
        for (int j = 0; j < OUTPUT_SIZE; j++) {
            float error = expected[j] - out[j];
            totalLoss += error * error;
            float deriv = (j < 3) ? tanhDeriv(out[j]) : (out[j] * (1.0f - out[j]));
            outGrad[j] = Math.max(-1.0f, Math.min(1.0f, error * deriv));
        }

        if (Float.isNaN(totalLoss) || Float.isInfinite(totalLoss)) {
            initializeWeights();
            return 0.5f;
        }
        this.lastLoss = totalLoss / OUTPUT_SIZE;

        // Hidden Layer 2 gradients
        float[] grad2 = new float[HIDDEN2_SIZE];
        for (int j = 0; j < HIDDEN2_SIZE; j++) {
            float sum = 0.0f;
            for (int k = 0; k < OUTPUT_SIZE; k++) {
                sum += outGrad[k] * weights3[j][k];
            }
            grad2[j] = Math.max(-1.0f, Math.min(1.0f, sum * leakyReluDeriv(z2[j])));
        }

        // Hidden Layer 1 gradients
        float[] grad1 = new float[HIDDEN1_SIZE];
        for (int j = 0; j < HIDDEN1_SIZE; j++) {
            float sum = 0.0f;
            for (int k = 0; k < HIDDEN2_SIZE; k++) {
                sum += grad2[k] * weights2[j][k];
            }
            grad1[j] = Math.max(-1.0f, Math.min(1.0f, sum * leakyReluDeriv(z1[j])));
        }

        // Update Weights 3
        for (int j = 0; j < HIDDEN2_SIZE; j++) {
            for (int k = 0; k < OUTPUT_SIZE; k++) {
                float delta = Math.max(-0.2f, Math.min(0.2f, lr * outGrad[k] * a2[j] + momentum * dWeights3[j][k]));
                weights3[j][k] = Math.max(-4.0f, Math.min(4.0f, weights3[j][k] * weightDecay + delta));
                dWeights3[j][k] = delta;
            }
        }
        for (int k = 0; k < OUTPUT_SIZE; k++) {
            bias3[k] = Math.max(-2.0f, Math.min(2.0f, bias3[k] + lr * outGrad[k]));
        }

        // Update Weights 2
        for (int j = 0; j < HIDDEN1_SIZE; j++) {
            for (int k = 0; k < HIDDEN2_SIZE; k++) {
                float delta = Math.max(-0.2f, Math.min(0.2f, lr * grad2[k] * a1[j] + momentum * dWeights2[j][k]));
                weights2[j][k] = Math.max(-4.0f, Math.min(4.0f, weights2[j][k] * weightDecay + delta));
                dWeights2[j][k] = delta;
            }
        }
        for (int k = 0; k < HIDDEN2_SIZE; k++) {
            bias2[k] = Math.max(-2.0f, Math.min(2.0f, bias2[k] + lr * grad2[k]));
        }

        // Update Weights 1
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < HIDDEN1_SIZE; j++) {
                float delta = Math.max(-0.2f, Math.min(0.2f, lr * grad1[j] * input[i] + momentum * dWeights1[i][j]));
                weights1[i][j] = Math.max(-4.0f, Math.min(4.0f, weights1[i][j] * weightDecay + delta));
                dWeights1[i][j] = delta;
            }
        }
        for (int j = 0; j < HIDDEN1_SIZE; j++) {
            bias1[j] = Math.max(-2.0f, Math.min(2.0f, bias1[j] + lr * grad1[j]));
        }

        this.trainedSamples++;
        return this.lastLoss;
    }

    public int getTrainedSamples() {
        return this.trainedSamples;
    }

    public float getLastLoss() {
        return this.lastLoss;
    }

    public void setProfile(String profile) {
        initializeWeights();
        if (profile.equalsIgnoreCase("Aggressive")) {
            bias3[1] += 0.25f; // aim higher towards head
            bias3[3] += 0.5f; // higher attack sensitivity
            bias3[4] += 0.4f; // faster W-tap
        } else if (profile.equalsIgnoreCase("Smooth")) {
            bias3[1] += 0.10f;
            bias3[3] += 0.10f;
        }
    }

    public static class ModelData {
        public float[][] weights1;
        public float[] bias1;
        public float[][] weights2;
        public float[] bias2;
        public float[][] weights3;
        public float[] bias3;
        public int trainedSamples;
    }

    public void save(File file) {
        if (hasNaN()) return;
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            ModelData data = new ModelData();
            data.weights1 = this.weights1;
            data.bias1 = this.bias1;
            data.weights2 = this.weights2;
            data.bias2 = this.bias2;
            data.weights3 = this.weights3;
            data.bias3 = this.bias3;
            data.trainedSamples = this.trainedSamples;

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean load(File file) {
        if (!file.exists() || file.length() < 50) return false;
        try {
            Gson gson = new Gson();
            try (FileReader reader = new FileReader(file)) {
                ModelData data = gson.fromJson(reader, ModelData.class);
                if (data != null && data.weights1 != null && data.weights2 != null && data.weights3 != null
                        && data.weights1.length == INPUT_SIZE && data.weights3[0].length == OUTPUT_SIZE) {
                    this.weights1 = data.weights1;
                    this.bias1 = data.bias1;
                    this.weights2 = data.weights2;
                    this.bias2 = data.bias2;
                    this.weights3 = data.weights3;
                    this.bias3 = data.bias3;
                    this.trainedSamples = data.trainedSamples;

                    if (hasNaN()) {
                        initializeWeights();
                        return false;
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
