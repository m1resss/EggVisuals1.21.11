package egg.client.module.combat;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.util.EntityUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HitSounds extends Module {
    private static final String SKEET = "Skeet";
    private static final String NEVERLOSE = "Neverlose";
    private static final String BELL = "Колокольчик";
    private static final String ARROW = "Стрела";
    private static final String BUBBLE = "Пузырек";
    private static final String BONK = "Бонк";
    private static final String POP = "Поп";

    private static final String[] DEFAULT_MODES = new String[]{
            SKEET, NEVERLOSE, BELL, ARROW, BUBBLE, BONK, POP
    };

    private final ModeSetting soundMode = new ModeSetting("Звук", SKEET, "Выбор звукового эффекта попадания", DEFAULT_MODES);
    private final SliderSetting volume = new SliderSetting("Громкость", 80.0, 5.0, 100.0, 5.0, "Громкость звукового эффекта");
    private final SliderSetting pitch = new SliderSetting("Питч", 1.0, 0.5, 2.0, 0.05, "Базовая высота звука");
    private final BooleanSetting dynamicPitch = new BooleanSetting("Динамический питч", true, "Повышает тон по мере снижения здоровья цели");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false, "Звук воспроизводится только при ударе по игрокам");
    private final ButtonSetting openFolderBtn = new ButtonSetting("Открыть папку", "Открыть директорию для добавления своих звуков", this::openCustomFolder);
    private final ButtonSetting refreshBtn = new ButtonSetting("Обновить список", "Пересканировать папку со звуками", this::refreshCustomSounds);

    private final Path soundsDir;
    private final Map<String, short[]> pcmCache = new ConcurrentHashMap<>();
    private final Map<String, File> customFilesMap = new ConcurrentHashMap<>();
    private final Clip[] clipPool = new Clip[4];
    private int clipPoolIndex = 0;

    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HitSounds-Worker");
        t.setDaemon(true);
        return t;
    });

    public HitSounds() {
        super("HitSounds", "Воспроизводит кастомный звук при попадании по существу", Category.ESP);
        addSettings(soundMode, volume, pitch, dynamicPitch, onlyPlayers, openFolderBtn, refreshBtn);

        Path gameDir = FabricLoader.getInstance().getGameDir();
        this.soundsDir = gameDir.resolve("egg").resolve("hitsounds");
        ensureSoundsDir();
        initSyntheticSounds();
        refreshCustomSounds();
    }

    private void ensureSoundsDir() {
        try {
            if (!Files.exists(soundsDir)) {
                Files.createDirectories(soundsDir);
                Path readme = soundsDir.resolve("readme.txt");
                if (!Files.exists(readme)) {
                    Files.writeString(readme, "Поместите сюда свои звуки в формате .wav (PCM 16-bit 44100Hz/48000Hz).\n"
                            + "Затем нажмите кнопку 'Обновить список' в настройках модуля HitSounds.");
                }
            }
        } catch (Throwable ignored) {}
    }

    private void openCustomFolder() {
        ensureSoundsDir();
        File folder = soundsDir.toFile();
        if (folder.exists()) {
            Util.getOperatingSystem().open(folder);
        }
    }

    public synchronized void refreshCustomSounds() {
        ensureSoundsDir();
        File folder = soundsDir.toFile();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".wav"));

        customFilesMap.clear();
        List<String> modes = new ArrayList<>(Arrays.asList(DEFAULT_MODES));

        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                String name = f.getName();
                if (name.toLowerCase(Locale.ROOT).endsWith(".wav")) {
                    String baseName = name.substring(0, name.length() - 4);
                    if (!modes.contains(baseName)) {
                        modes.add(baseName);
                    }
                    customFilesMap.put(baseName, f);
                }
            }
        }

        soundMode.setModes(modes);
    }

    private void initSyntheticSounds() {
        pcmCache.put(SKEET, generateSkeet());
        pcmCache.put(NEVERLOSE, generateNeverlose());
        pcmCache.put(BUBBLE, generateBubble());
        pcmCache.put(BONK, generateBonk());
        pcmCache.put(POP, generatePop());
    }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (target instanceof LivingEntity living && living != mc.player && living.isAlive()) {
            if (EntityUtil.isNakedInvisible(living)) return;
            if (onlyPlayers.get() && !(living instanceof PlayerEntity)) return;
            playHitSound(living);
        }
    }

    public void onDamagePacket(Entity entity) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (entity instanceof LivingEntity living && living != mc.player && living.isAlive()) {
            if (EntityUtil.isNakedInvisible(living)) return;
            if (onlyPlayers.get() && !(living instanceof PlayerEntity)) return;
        }
    }

    private void playHitSound(LivingEntity living) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        float basePitch = pitch.getValueFloat();
        float finalPitch = basePitch;

        if (dynamicPitch.get()) {
            float maxHp = Math.max(1.0f, living.getMaxHealth());
            float curHp = Math.clamp(living.getHealth(), 0.0f, maxHp);
            float hpRatio = curHp / maxHp;
            float pitchBonus = (1.0f - hpRatio) * 0.42f;
            finalPitch = Math.clamp(basePitch + pitchBonus, 0.4f, 2.4f);
        }

        float vol = volume.getValueFloat() / 100.0f;
        String mode = soundMode.get();

        if (mode.equalsIgnoreCase(BELL)) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), vol, finalPitch);
            return;
        }

        if (mode.equalsIgnoreCase(ARROW)) {
            mc.player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, vol, finalPitch);
            return;
        }

        final float effVol = vol;
        final float effPitch = finalPitch;
        final String effMode = mode;

        AUDIO_EXECUTOR.submit(() -> playCustomOrSynthetic(effMode, effVol, effPitch));
    }

    private void playCustomOrSynthetic(String mode, float vol, float targetPitch) {
        try {
            short[] samples = pcmCache.get(mode);
            if (samples == null) {
                File customFile = customFilesMap.get(mode);
                if (customFile != null && customFile.exists()) {
                    samples = loadWavSamples(customFile);
                    if (samples != null) {
                        pcmCache.put(mode, samples);
                    }
                }
            }

            if (samples == null || samples.length == 0) return;

            short[] pitched = (Math.abs(targetPitch - 1.0f) > 0.02f) ? resample(samples, targetPitch) : samples;
            byte[] wavData = createWavBytes(pitched, 44100);

            synchronized (clipPool) {
                Clip clip = clipPool[clipPoolIndex];
                if (clip != null) {
                    try {
                        clip.stop();
                        clip.close();
                    } catch (Throwable ignored) {}
                }

                AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavData));
                clip = AudioSystem.getClip();
                clip.open(ais);

                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float clampedVol = Math.max(0.0001f, Math.min(1.0f, vol));
                    float dB = (float) (Math.log10(clampedVol) * 20.0);
                    dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
                    gain.setValue(dB);
                }

                clip.start();
                clipPool[clipPoolIndex] = clip;
                clipPoolIndex = (clipPoolIndex + 1) % clipPool.length;
            }
        } catch (Throwable ignored) {}
    }

    private short[] loadWavSamples(File file) {
        try (AudioInputStream sourceStream = AudioSystem.getAudioInputStream(file)) {
            AudioFormat srcFormat = sourceStream.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100.0f,
                    16,
                    1,
                    2,
                    44100.0f,
                    false
            );

            AudioInputStream pcmStream = sourceStream;
            if (!srcFormat.matches(pcmFormat)) {
                pcmStream = AudioSystem.getAudioInputStream(pcmFormat, sourceStream);
            }

            byte[] rawBytes = pcmStream.readAllBytes();
            int sampleCount = rawBytes.length / 2;
            short[] samples = new short[sampleCount];
            ByteBuffer bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < sampleCount; i++) {
                samples[i] = bb.getShort();
            }
            return samples;
        } catch (Throwable t) {
            return null;
        }
    }

    private static short[] resample(short[] input, float pitch) {
        if (pitch <= 0.05f) pitch = 0.05f;
        int newLen = Math.max(1, (int) (input.length / pitch));
        short[] out = new short[newLen];
        for (int i = 0; i < newLen; i++) {
            float srcPos = i * pitch;
            int idx = (int) srcPos;
            if (idx >= input.length - 1) {
                out[i] = input[input.length - 1];
            } else {
                float frac = srcPos - idx;
                out[i] = (short) Math.round(input[idx] * (1.0f - frac) + input[idx + 1] * frac);
            }
        }
        return out;
    }

    private static byte[] createWavBytes(short[] samples, int sampleRate) {
        int dataLen = samples.length * 2;
        int totalLen = 36 + dataLen;
        int byteRate = sampleRate * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        buf.putInt(totalLen);
        buf.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');
        buf.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(sampleRate);
        buf.putInt(byteRate);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        buf.putInt(dataLen);
        for (short s : samples) {
            buf.putShort(s);
        }
        return buf.array();
    }

    private static short[] generateSkeet() {
        int len = 3200;
        short[] samples = new short[len];
        for (int i = 0; i < len; i++) {
            float t = (float) i / 44100.0f;
            float env = (t < 0.0025f) ? (t / 0.0025f) : (float) Math.exp(-44.0 * t);
            float val = (float) (Math.sin(2.0 * Math.PI * 2280.0 * t) * 0.68
                    + Math.sin(2.0 * Math.PI * 3420.0 * t) * 0.24
                    + Math.sin(2.0 * Math.PI * 5150.0 * t) * 0.14) * env;
            samples[i] = (short) Math.clamp(Math.round(val * 28500.0f), -32767, 32767);
        }
        return samples;
    }

    private static short[] generateNeverlose() {
        int len = 2600;
        short[] samples = new short[len];
        for (int i = 0; i < len; i++) {
            float t = (float) i / 44100.0f;
            float env = (t < 0.002f) ? (t / 0.002f) : (float) Math.exp(-54.0 * t);
            float val = (float) (Math.sin(2.0 * Math.PI * 1860.0 * t) * 0.58
                    + Math.sin(2.0 * Math.PI * 2790.0 * t) * 0.32
                    + Math.sin(2.0 * Math.PI * 4180.0 * t) * 0.16) * env;
            samples[i] = (short) Math.clamp(Math.round(val * 29000.0f), -32767, 32767);
        }
        return samples;
    }

    private static short[] generateBubble() {
        int len = 3400;
        short[] samples = new short[len];
        double phase = 0.0;
        float dur = (float) len / 44100.0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / 44100.0f;
            float freq = 440.0f + 760.0f * (t / dur);
            phase += 2.0 * Math.PI * freq / 44100.0;
            float env = (float) Math.sin(Math.PI * (t / dur));
            float val = (float) Math.sin(phase) * env;
            samples[i] = (short) Math.clamp(Math.round(val * 27000.0f), -32767, 32767);
        }
        return samples;
    }

    private static short[] generateBonk() {
        int len = 3600;
        short[] samples = new short[len];
        double phase = 0.0;
        float dur = (float) len / 44100.0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / 44100.0f;
            float freq = 520.0f - 260.0f * (float) Math.pow(t / dur, 0.65);
            phase += 2.0 * Math.PI * freq / 44100.0;
            float env = (t < 0.0025f) ? (t / 0.0025f) : (float) Math.exp(-36.0 * t);
            float val = (float) (Math.sin(phase) * 0.78 + Math.sin(phase * 1.82) * 0.22) * env;
            samples[i] = (short) Math.clamp(Math.round(val * 28000.0f), -32767, 32767);
        }
        return samples;
    }

    private static short[] generatePop() {
        int len = 1800;
        short[] samples = new short[len];
        for (int i = 0; i < len; i++) {
            float t = (float) i / 44100.0f;
            float env = (t < 0.0015f) ? (t / 0.0015f) : (float) Math.exp(-68.0 * t);
            float val = (float) Math.sin(2.0 * Math.PI * 1150.0 * t) * env;
            samples[i] = (short) Math.clamp(Math.round(val * 29500.0f), -32767, 32767);
        }
        return samples;
    }
}