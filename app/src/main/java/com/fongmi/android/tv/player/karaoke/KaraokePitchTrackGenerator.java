package com.fongmi.android.tv.player.karaoke;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsWord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KaraokePitchTrackGenerator {

    private static final double BPM = 6000.0;
    private static final long BEAT_MS = 10;
    private static final int RAP_PITCH = 60;
    private static final int MAX_NOTES = 900;
    private static final long MIN_NOTE_MS = 80;
    private static final long DEFAULT_LAST_LINE_MS = 3000;
    private static final int FRAME_SIZE = 2048;
    private static final int HOP_SIZE = 1024;
    private static final double MIN_FREQUENCY_HZ = 80.0;
    private static final double MAX_FREQUENCY_HZ = 800.0;
    private static final double MIN_VOLUME = 0.006;
    private static final double MIN_CONFIDENCE = 0.18;
    private static final double MIN_WINDOW_VALID_RATIO = 0.14;
    private static final double MAX_WINDOW_SPREAD = 8.0;
    private static final double MIN_RAW_PITCH_RATIO = 0.08;
    private static final double MIN_TRACK_PITCH_RATIO = 0.18;
    private static final long WINDOW_MARGIN_MS = 70;
    private static final int MIN_MIDI = 40;
    private static final int MAX_MIDI = 84;
    private static final int GAP_FILL_MAX_NOTES = 8;
    private static final int OUTLIER_STEP = 9;

    private KaraokePitchTrackGenerator() {
    }

    public static boolean canGenerate(KaraokeTrackRepository.MediaInput input, List<LyricsLine> lines) {
        return input != null && !input.isEmpty() && KaraokeGeneratedTrackBuilder.canGenerate(lines);
    }

    public static String build(KaraokeTrackRepository.MediaInput input, List<LyricsLine> lines) throws Exception {
        if (!canGenerate(input, lines)) throw new IllegalStateException("no timed lyrics");
        List<Segment> segments = segments(lines, input.getDuration());
        if (segments.size() < 3) throw new IllegalStateException("not enough lyric timing");
        List<PitchFrame> frames = decode(input);
        if (frames.isEmpty()) throw new IllegalStateException("no pitch frames");
        return buildText(input.getKeyword(), input.getArtist(), segments, frames);
    }

    private static String buildText(String title, String artist, List<Segment> segments, List<PitchFrame> frames) {
        StringBuilder builder = new StringBuilder();
        builder.append("#TITLE:").append(tag(title, "Generated pitch track")).append('\n');
        builder.append("#ARTIST:").append(tag(artist, "Unknown")).append('\n');
        builder.append("#BPM:").append(BPM).append('\n');
        builder.append("#GAP:0").append('\n');
        builder.append("#COMMENT:Generated experimental pitch scoring track from local audio; octave corrected and smoothed").append('\n');
        List<Note> notes = notes(segments, frames);
        stabilize(notes);
        int count = 0;
        int observed = 0;
        int pitched = 0;
        for (Note note : notes) {
            if (count >= MAX_NOTES) break;
            char prefix = note.pitch >= 0 ? ':' : 'R';
            if (note.observed) observed++;
            if (note.pitch >= 0) pitched++;
            appendNote(builder, prefix, note.segment.startMs, note.segment.endMs, note.pitch >= 0 ? note.pitch : RAP_PITCH, note.segment.text);
            count++;
            if (note.segment.lineEnd) builder.append("-\n");
        }
        if (count < 3) throw new IllegalStateException("not enough notes");
        if (observed < 3 || observed < Math.round(count * MIN_RAW_PITCH_RATIO)) throw new IllegalStateException("pitch quality too low");
        if (pitched < 3 || pitched < Math.round(count * MIN_TRACK_PITCH_RATIO)) throw new IllegalStateException("pitch quality too low");
        builder.append('E').append('\n');
        return builder.toString();
    }

    private static List<Note> notes(List<Segment> segments, List<PitchFrame> frames) {
        List<Note> notes = new ArrayList<>();
        for (Segment segment : segments) {
            PitchCandidate candidate = PitchWindow.from(frames, segment.startMs, segment.endMs).candidate();
            notes.add(new Note(segment, candidate));
        }
        return notes;
    }

    private static void stabilize(List<Note> notes) {
        correctOctaves(notes);
        smoothOutliers(notes);
        fillMissing(notes);
        correctOctaves(notes);
        smoothOutliers(notes);
    }

    private static void correctOctaves(List<Note> notes) {
        Integer anchor = null;
        for (Note note : notes) {
            if (note.pitch < 0) continue;
            if (anchor != null) note.pitch = normalizeOctave(note.pitch, anchor);
            if (anchor != null && Math.abs(note.pitch - anchor) > OUTLIER_STEP && note.quality < 0.45) {
                note.pitch = -1;
                note.estimated = true;
                continue;
            }
            anchor = note.pitch;
        }
        anchor = null;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note note = notes.get(i);
            if (note.pitch < 0) continue;
            if (anchor != null) note.pitch = normalizeOctave(note.pitch, anchor);
            anchor = note.pitch;
        }
    }

    private static void smoothOutliers(List<Note> notes) {
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            if (note.pitch < 0) continue;
            Neighbor previous = previous(notes, i);
            Neighbor next = next(notes, i);
            if (previous == null || next == null || previous.distance > 3 || next.distance > 3) continue;
            int target = Math.round((previous.note.pitch + next.note.pitch) / 2.0f);
            note.pitch = normalizeOctave(note.pitch, target);
            if (Math.abs(note.pitch - target) > OUTLIER_STEP && note.quality < 0.65) {
                note.pitch = clampMidi(target);
                note.estimated = true;
            }
        }
    }

    private static void fillMissing(List<Note> notes) {
        int index = 0;
        while (index < notes.size()) {
            if (notes.get(index).pitch >= 0) {
                index++;
                continue;
            }
            int start = index;
            while (index < notes.size() && notes.get(index).pitch < 0) index++;
            int end = index;
            int length = end - start;
            Note previous = start > 0 ? notes.get(start - 1) : null;
            Note next = end < notes.size() ? notes.get(end) : null;
            if (length > GAP_FILL_MAX_NOTES) continue;
            if (previous != null && previous.pitch >= 0 && next != null && next.pitch >= 0) {
                int nextPitch = normalizeOctave(next.pitch, previous.pitch);
                for (int i = start; i < end; i++) {
                    float progress = (i - start + 1) / (float) (length + 1);
                    notes.get(i).pitch = clampMidi(Math.round(previous.pitch + (nextPitch - previous.pitch) * progress));
                    notes.get(i).estimated = true;
                }
            } else if (previous != null && previous.pitch >= 0) {
                for (int i = start; i < end; i++) {
                    notes.get(i).pitch = previous.pitch;
                    notes.get(i).estimated = true;
                }
            } else if (next != null && next.pitch >= 0) {
                for (int i = start; i < end; i++) {
                    notes.get(i).pitch = next.pitch;
                    notes.get(i).estimated = true;
                }
            }
        }
    }

    private static Neighbor previous(List<Note> notes, int index) {
        for (int i = index - 1; i >= 0; i--) if (notes.get(i).pitch >= 0) return new Neighbor(notes.get(i), index - i);
        return null;
    }

    private static Neighbor next(List<Note> notes, int index) {
        for (int i = index + 1; i < notes.size(); i++) if (notes.get(i).pitch >= 0) return new Neighbor(notes.get(i), i - index);
        return null;
    }

    private static int normalizeOctave(int pitch, int reference) {
        int best = pitch;
        int bestDistance = Math.abs(best - reference);
        for (int candidate = pitch - 24; candidate <= pitch + 24; candidate += 12) {
            if (candidate < MIN_MIDI || candidate > MAX_MIDI) continue;
            int distance = Math.abs(candidate - reference);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return clampMidi(best);
    }

    private static int clampMidi(int pitch) {
        return Math.max(MIN_MIDI, Math.min(MAX_MIDI, pitch));
    }

    private static void appendNote(StringBuilder builder, char prefix, long startMs, long endMs, int pitch, String lyric) {
        long safeStart = Math.max(0, startMs);
        long safeEnd = Math.max(safeStart + MIN_NOTE_MS, endMs);
        int startBeat = (int) Math.max(0, Math.round(safeStart / (double) BEAT_MS));
        int lengthBeat = (int) Math.max(1, Math.round((safeEnd - safeStart) / (double) BEAT_MS));
        builder.append(prefix).append(' ')
                .append(startBeat).append(' ')
                .append(lengthBeat).append(' ')
                .append(pitch).append(' ')
                .append(lyric(lyric)).append('\n');
    }

    private static List<PitchFrame> decode(KaraokeTrackRepository.MediaInput input) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            setDataSource(extractor, input.getUrl(), input.getHeaders());
            int track = selectAudioTrack(extractor);
            if (track < 0) throw new IllegalStateException("no audio track");
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (TextUtils.isEmpty(mime)) throw new IllegalStateException("unknown audio mime");
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            return decodeLoop(extractor, decoder, format);
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {
                }
                decoder.release();
            }
        }
    }

    private static List<PitchFrame> decodeLoop(MediaExtractor extractor, MediaCodec decoder, MediaFormat inputFormat) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        PitchFrameCollector collector = new PitchFrameCollector(sampleRate(inputFormat));
        boolean inputDone = false;
        boolean outputDone = false;
        MediaFormat outputFormat = inputFormat;
        while (!outputDone) {
            if (!inputDone) {
                int inputIndex = decoder.dequeueInputBuffer(10_000);
                if (inputIndex >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(inputIndex);
                    if (input == null) continue;
                    int size = extractor.readSampleData(input, 0);
                    if (size < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                        extractor.advance();
                    }
                }
            }
            int outputIndex = decoder.dequeueOutputBuffer(info, 10_000);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = decoder.getOutputFormat();
                collector.setSampleRate(sampleRate(outputFormat));
            } else if (outputIndex >= 0) {
                ByteBuffer output = decoder.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0) collect(collector, output, info, outputFormat);
                outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                decoder.releaseOutputBuffer(outputIndex, false);
            }
        }
        return collector.frames();
    }

    private static void collect(PitchFrameCollector collector, ByteBuffer buffer, MediaCodec.BufferInfo info, MediaFormat format) {
        int channels = Math.max(1, format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1);
        int encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING) ? format.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
        ByteBuffer data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        data.position(info.offset);
        data.limit(info.offset + info.size);
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) collectFloat(collector, data, channels);
        else collectPcm16(collector, data, channels);
    }

    private static void collectPcm16(PitchFrameCollector collector, ByteBuffer data, int channels) {
        int frameBytes = channels * 2;
        int samples = data.remaining() / frameBytes;
        for (int i = 0; i < samples; i++) {
            float mono = 0;
            for (int c = 0; c < channels; c++) mono += data.getShort() / 32768f;
            collector.add(mono / channels);
        }
    }

    private static void collectFloat(PitchFrameCollector collector, ByteBuffer data, int channels) {
        int frameBytes = channels * 4;
        int samples = data.remaining() / frameBytes;
        for (int i = 0; i < samples; i++) {
            float mono = 0;
            for (int c = 0; c < channels; c++) mono += data.getFloat();
            collector.add(mono / channels);
        }
    }

    private static void setDataSource(MediaExtractor extractor, String url, Map<String, String> headers) throws Exception {
        if (TextUtils.isEmpty(url)) throw new IllegalStateException("empty url");
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme) || "android.resource".equalsIgnoreCase(scheme)) {
            extractor.setDataSource(App.get(), uri, headers);
        } else if ("file".equalsIgnoreCase(scheme)) {
            extractor.setDataSource(Uri.decode(uri.getPath()));
        } else if (TextUtils.isEmpty(scheme)) {
            extractor.setDataSource(url);
        } else {
            extractor.setDataSource(url, headers);
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!TextUtils.isEmpty(mime) && mime.toLowerCase(Locale.ROOT).startsWith("audio/")) return i;
        }
        return -1;
    }

    private static int sampleRate(MediaFormat format) {
        return format != null && format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? Math.max(1, format.getInteger(MediaFormat.KEY_SAMPLE_RATE)) : 44_100;
    }

    private static List<Segment> segments(List<LyricsLine> lines, long durationMs) {
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < lines.size() && segments.size() < MAX_NOTES; i++) {
            LyricsLine line = lines.get(i);
            if (line == null || TextUtils.isEmpty(line.getText())) continue;
            long startMs = line.getTimeMs();
            long endMs = lineEnd(lines, i, durationMs);
            int before = segments.size();
            if (line.hasWords()) appendWordSegments(segments, line, startMs, endMs);
            else appendLineSegments(segments, line.getText(), startMs, endMs);
            if (segments.size() > before) segments.get(segments.size() - 1).lineEnd = true;
        }
        return segments;
    }

    private static void appendWordSegments(List<Segment> segments, LyricsLine line, long lineStartMs, long lineEndMs) {
        List<LyricsWord> words = line.getWords();
        for (int i = 0; i < words.size() && segments.size() < MAX_NOTES; i++) {
            LyricsWord word = words.get(i);
            if (word == null || TextUtils.isEmpty(word.getText())) continue;
            long startMs = lineStartMs + word.getStartOffsetMs();
            long endMs = word.getDurationMs() > 0 ? startMs + word.getDurationMs() : nextWordStart(lineStartMs, lineEndMs, words, i);
            segments.add(new Segment(startMs, endMs, word.getText()));
        }
    }

    private static void appendLineSegments(List<Segment> segments, String text, long startMs, long endMs) {
        List<String> units = splitUnits(text);
        if (units.isEmpty()) return;
        long durationMs = Math.max(MIN_NOTE_MS * units.size(), endMs - startMs);
        long unitMs = Math.max(MIN_NOTE_MS, durationMs / units.size());
        for (int i = 0; i < units.size() && segments.size() < MAX_NOTES; i++) {
            long unitStart = startMs + unitMs * i;
            long unitEnd = i == units.size() - 1 ? startMs + durationMs : unitStart + unitMs;
            segments.add(new Segment(unitStart, unitEnd, units.get(i)));
        }
    }

    private static long lineEnd(List<LyricsLine> lines, int index, long durationMs) {
        long startMs = lines.get(index).getTimeMs();
        long nextMs = index + 1 < lines.size() ? lines.get(index + 1).getTimeMs() : 0;
        long fallback = startMs + DEFAULT_LAST_LINE_MS;
        if (durationMs > startMs + MIN_NOTE_MS) fallback = Math.min(durationMs, fallback);
        if (nextMs <= startMs) return fallback;
        return Math.max(startMs + MIN_NOTE_MS, nextMs);
    }

    private static long nextWordStart(long lineStartMs, long lineEndMs, List<LyricsWord> words, int index) {
        if (index + 1 < words.size()) return lineStartMs + words.get(index + 1).getStartOffsetMs();
        return lineEndMs;
    }

    private static List<String> splitUnits(String text) {
        String clean = lyric(text);
        List<String> units = new ArrayList<>();
        if (TextUtils.isEmpty(clean)) return units;
        if (clean.matches(".*\\s+.*")) {
            for (String unit : clean.split("\\s+")) if (!TextUtils.isEmpty(unit)) units.add(unit);
        } else if (clean.codePointCount(0, clean.length()) <= 24) {
            for (int i = 0; i < clean.length(); ) {
                int codePoint = clean.codePointAt(i);
                units.add(new String(Character.toChars(codePoint)));
                i += Character.charCount(codePoint);
            }
        } else {
            units.add(clean);
        }
        return units;
    }

    private static String tag(String value, String fallback) {
        String text = lyric(value);
        return TextUtils.isEmpty(text) ? fallback : text;
    }

    private static String lyric(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static class Segment {

        private final long startMs;
        private final long endMs;
        private final String text;
        private boolean lineEnd;

        private Segment(long startMs, long endMs, String text) {
            this.startMs = Math.max(0, startMs);
            this.endMs = Math.max(this.startMs + MIN_NOTE_MS, endMs);
            this.text = text;
        }
    }

    private static class Note {

        private final Segment segment;
        private final boolean observed;
        private final double quality;
        private int pitch;
        private boolean estimated;

        private Note(Segment segment, PitchCandidate candidate) {
            this.segment = segment;
            this.pitch = candidate.pitch;
            this.quality = candidate.quality;
            this.observed = candidate.pitch >= 0;
        }
    }

    private static class PitchCandidate {

        private static final PitchCandidate EMPTY = new PitchCandidate(-1, 0);

        private final int pitch;
        private final double quality;

        private PitchCandidate(int pitch, double quality) {
            this.pitch = pitch >= 0 ? clampMidi(pitch) : -1;
            this.quality = Math.max(0, Math.min(1, quality));
        }
    }

    private static class Neighbor {

        private final Note note;
        private final int distance;

        private Neighbor(Note note, int distance) {
            this.note = note;
            this.distance = distance;
        }
    }

    private static class PitchFrame {

        private final long timeMs;
        private final double frequencyHz;
        private final double volume;
        private final double confidence;

        private PitchFrame(KaraokePitchSample sample) {
            this.timeMs = sample.getTimestampMs();
            this.frequencyHz = sample.getFrequencyHz();
            this.volume = sample.getVolume();
            this.confidence = sample.getConfidence();
        }

        private boolean valid() {
            return frequencyHz >= MIN_FREQUENCY_HZ && frequencyHz <= MAX_FREQUENCY_HZ && volume >= MIN_VOLUME && confidence >= MIN_CONFIDENCE;
        }

        private double midi() {
            return KaraokePitch.frequencyToMidi(frequencyHz);
        }
    }

    private static class PitchFrameCollector {

        private final float[] ring = new float[FRAME_SIZE];
        private final float[] frame = new float[FRAME_SIZE];
        private final List<PitchFrame> frames = new ArrayList<>();
        private YinPitchDetector detector;
        private long sampleCount;
        private int sampleRate;

        private PitchFrameCollector(int sampleRate) {
            setSampleRate(sampleRate);
        }

        private void setSampleRate(int sampleRate) {
            int safe = Math.max(1, sampleRate);
            if (this.sampleRate == safe && detector != null) return;
            this.sampleRate = safe;
            this.detector = new YinPitchDetector(safe, 0.12, 0.08, FRAME_SIZE);
        }

        private void add(float value) {
            ring[(int) (sampleCount % FRAME_SIZE)] = value;
            sampleCount++;
            if (sampleCount < FRAME_SIZE) return;
            if ((sampleCount - FRAME_SIZE) % HOP_SIZE != 0) return;
            copyFrame();
            long centerSample = sampleCount - FRAME_SIZE / 2L;
            long timeMs = Math.max(0, Math.round(centerSample * 1000.0 / sampleRate));
            frames.add(new PitchFrame(detector.detect(frame, frame.length, timeMs)));
        }

        private void copyFrame() {
            long start = sampleCount - FRAME_SIZE;
            for (int i = 0; i < FRAME_SIZE; i++) frame[i] = ring[(int) ((start + i) % FRAME_SIZE)];
        }

        private List<PitchFrame> frames() {
            return frames;
        }
    }

    private static class PitchWindow {

        private final List<Double> values;
        private final int total;

        private PitchWindow(List<Double> values, int total) {
            this.values = values;
            this.total = total;
        }

        private static PitchWindow from(List<PitchFrame> frames, long startMs, long endMs) {
            List<Double> values = new ArrayList<>();
            int total = 0;
            long safeStart = Math.max(0, startMs - WINDOW_MARGIN_MS);
            long safeEnd = Math.max(safeStart + MIN_NOTE_MS, endMs + WINDOW_MARGIN_MS);
            for (PitchFrame frame : frames) {
                if (frame.timeMs < safeStart) continue;
                if (frame.timeMs >= safeEnd) break;
                total++;
                if (frame.valid()) values.add(frame.midi());
            }
            return new PitchWindow(values, total);
        }

        private PitchCandidate candidate() {
            if (values.size() < 2 || total <= 0) return PitchCandidate.EMPTY;
            double ratio = values.size() / (double) total;
            if (ratio < MIN_WINDOW_VALID_RATIO) return PitchCandidate.EMPTY;
            Collections.sort(values);
            double median = values.get(values.size() / 2);
            double spread = percentile(0.80) - percentile(0.20);
            if (spread > MAX_WINDOW_SPREAD) return PitchCandidate.EMPTY;
            double quality = ratio * Math.max(0.15, 1.0 - spread / Math.max(1.0, MAX_WINDOW_SPREAD));
            return new PitchCandidate((int) Math.round(median), quality);
        }

        private double percentile(double p) {
            if (values.isEmpty()) return 0;
            int index = (int) Math.max(0, Math.min(values.size() - 1, Math.round((values.size() - 1) * p)));
            return values.get(index);
        }
    }
}
