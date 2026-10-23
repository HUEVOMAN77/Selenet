package com.fork.ytdl;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Junta una pista de video sin audio con una pista de audio en un solo MP4, sin ffmpeg.
 * Lee las muestras con MediaExtractor y las escribe con MediaMuxer: no decodifica ni
 * recodifica, asi que el resultado conserva la calidad exacta del video y del audio que
 * entrego el sitio. Es lo que permite 1080p y mas, donde YouTube manda el video y el audio
 * por separado.
 */
public final class Merger {

    /** 1 MB por pista: una muestra de video 1080p rara vez pasa de unos pocos kB. */
    private static final int BUFFER_BYTES = 1024 * 1024;

    private Merger() {
    }

    /** Progreso de la union (0 a 100 de esta etapa) y la senal de cancelacion. */
    public interface Watch {
        void onStage(int percent);

        boolean cancelled();
    }

    /** Error con el tipo que entiende Errors.reason(), para que el mensaje salga en espanol. */
    public static class Failure extends Exception {
        public final String kind;

        Failure(String kind, String message) {
            super(message);
            this.kind = kind;
        }
    }

    /**
     * Escribe en `target` un MP4 con el video de `videoFile` y el audio de `audioFile`.
     *
     * @return texto con los codices usados, para el log.
     * @throws Failure "no_video" si el video no tiene pista de video, "no_audio" si el audio
     *                 no tiene pista o el codec no entra en un MP4, "merge" si el telefono no
     *                 pudo leer o escribir el contenedor, "cancel" si la cortaron.
     */
    public static String merge(File videoFile, File audioFile, File target, Watch watch)
            throws Failure {
        MediaExtractor video = new MediaExtractor();
        MediaExtractor audio = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            video.setDataSource(videoFile.getAbsolutePath());
            int videoTrack = findTrack(video, "video/");
            if (videoTrack < 0) {
                throw new Failure("no_video", "el archivo de video no tiene pista de video");
            }
            MediaFormat videoFormat = video.getTrackFormat(videoTrack);
            String videoMime = value(videoFormat.getString(MediaFormat.KEY_MIME));
            if (!muxableVideo(videoMime)) {
                throw new Failure("merge", videoMime
                        + " no se puede escribir en un MP4 sin recodificar");
            }

            audio.setDataSource(audioFile.getAbsolutePath());
            int audioTrack = findTrack(audio, "audio/");
            if (audioTrack < 0) {
                throw new Failure("no_audio", "el archivo de audio no tiene pista de audio");
            }
            MediaFormat audioFormat = audio.getTrackFormat(audioTrack);
            String audioMime = value(audioFormat.getString(MediaFormat.KEY_MIME));
            if (!muxableAudio(audioMime)) {
                throw new Failure("no_audio", audioMime
                        + " no se puede escribir en un MP4 sin recodificar");
            }

            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new Failure("merge", "no se pudo crear " + parent.getAbsolutePath());
            }
            video.selectTrack(videoTrack);
            audio.selectTrack(audioTrack);
            muxer = new MediaMuxer(target.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outVideo = muxer.addTrack(videoFormat);
            int outAudio = muxer.addTrack(audioFormat);
            muxer.start();

            long span = Math.max(duration(videoFormat), duration(audioFormat));
            ByteBuffer videoBuffer = ByteBuffer.allocate(BUFFER_BYTES);
            ByteBuffer audioBuffer = ByteBuffer.allocate(BUFFER_BYTES);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            int videoSize = video.readSampleData(videoBuffer, 0);
            int audioSize = audio.readSampleData(audioBuffer, 0);
            long lastVideo = -1;
            long lastAudio = -1;
            long written = 0;
            while (videoSize >= 0 || audioSize >= 0) {
                if (watch != null && watch.cancelled()) {
                    throw new Failure("cancel", "cancelado durante la union");
                }
                // se escribe primero la muestra que va mas atras en el tiempo: el contenedor
                // queda interleado y se puede abrir en cualquier punto sin leer todo el archivo
                boolean takeVideo;
                if (audioSize < 0) {
                    takeVideo = true;
                } else if (videoSize < 0) {
                    takeVideo = false;
                } else {
                    takeVideo = video.getSampleTime() <= audio.getSampleTime();
                }
                long time;
                if (takeVideo) {
                    info.offset = 0;
                    info.size = videoSize;
                    time = video.getSampleTime();
                    // MediaMuxer exige tiempos que no retrocedan por pista; con B frames el
                    // orden de decodificacion a veces entrega el mismo valor, y se sostiene
                    // el anterior en vez de tirar la muestra
                    if (time <= lastVideo) {
                        time = lastVideo + 1;
                    }
                    info.presentationTimeUs = time;
                    info.flags = flags(video.getSampleFlags());
                    muxer.writeSampleData(outVideo, videoBuffer, info);
                    lastVideo = time;
                    video.advance();
                    videoSize = video.readSampleData(videoBuffer, 0);
                } else {
                    info.offset = 0;
                    info.size = audioSize;
                    time = audio.getSampleTime();
                    if (time <= lastAudio) {
                        time = lastAudio + 1;
                    }
                    info.presentationTimeUs = time;
                    info.flags = flags(audio.getSampleFlags());
                    muxer.writeSampleData(outAudio, audioBuffer, info);
                    lastAudio = time;
                    audio.advance();
                    audioSize = audio.readSampleData(audioBuffer, 0);
                }
                written++;
                if (watch != null && span > 0 && written % 16 == 0) {
                    watch.onStage((int) Math.min(99, time * 100 / span));
                }
            }
            if (lastVideo < 0) {
                throw new Failure("no_video", "la pista de video no tenia muestras");
            }
            if (lastAudio < 0) {
                throw new Failure("no_audio", "la pista de audio no tenia muestras");
            }
            if (watch != null) {
                watch.onStage(100);
            }
            return videoMime + "+" + audioMime;
        } catch (Failure failure) {
            throw failure;
        } catch (IllegalArgumentException e) {
            // addTrack tira esto cuando el codec no tiene lugar en un MP4
            throw new Failure("merge", String.valueOf(e.getMessage()));
        } catch (Exception e) {
            throw new Failure("merge", String.valueOf(e));
        } finally {
            release(muxer, video, audio);
        }
    }

    private static void release(MediaMuxer muxer, MediaExtractor video, MediaExtractor audio) {
        try {
            if (muxer != null) {
                muxer.release();
            }
        } catch (Exception ignored) {
            // un muxer a medio escribir deja archivo roto: el que llama lo borra igual
        }
        try {
            video.release();
        } catch (Exception ignored) {
        }
        try {
            audio.release();
        } catch (Exception ignored) {
        }
    }

    private static int findTrack(MediaExtractor extractor, String prefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = value(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME));
            if (mime.startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Video que Android mete en un MP4 tal cual. VP9 y AV1 quedan afuera: no tienen lugar en
     * un MP4 y recodificarlos en el telefono cuesta tiempo y bateria.
     */
    private static boolean muxableVideo(String mime) {
        String subtype = mime.replaceFirst("^video/", "").toLowerCase(Locale.US);
        return subtype.equals("avc") || subtype.equals("hevc")
                || subtype.equals("mp4v-es") || subtype.startsWith("h264");
    }

    /** Audio que entra en un MP4. Opus y Vorbis (los habituales de webm) quedan afuera. */
    private static boolean muxableAudio(String mime) {
        String subtype = mime.replaceFirst("^audio/", "").toLowerCase(Locale.US);
        return subtype.startsWith("mp4a") || subtype.startsWith("aac")
                || subtype.startsWith("amr") || subtype.equals("3gpp")
                || subtype.equals("alaw") || subtype.equals("ulaw")
                || subtype.equals("m4a");
    }

    private static long duration(MediaFormat format) {
        try {
            return format.getLong(MediaFormat.KEY_DURATION);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Pasa las banderas de la muestra al muxer, igual que en la extraccion de audio: la
     * sincronica y la de datos del codec tienen el mismo numero en los dos lados, y la de
     * frame parcial se traduce. Sin los datos del codec el MP4 sale sin avcC ni esds.
     */
    private static int flags(int sampleFlags) {
        int out = sampleFlags & (MediaExtractor.SAMPLE_FLAG_SYNC
                | MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
        if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            out |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
        }
        return out;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
