package com.fork.ytdl;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Saca la pista de audio de un MP4 con las APIs nativas de Android: MediaExtractor lee las
 * muestras y MediaMuxer las escribe en un MP4 nuevo. No decodifica ni recodifica, así que el
 * resultado es el audio original (AAC) dentro de un .m4a y sin ffmpeg.
 */
public final class AudioRemux {

    /** 1 MB, como pide el plan; las muestras de AAC son de pocos kB. */
    private static final int BUFFER_BYTES = 1024 * 1024;

    private AudioRemux() {
    }

    /** El progreso de la extraccion (0 a 100 de esta etapa) y la senal de cancelacion. */
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
     * Copia la primera pista de audio de `source` a `target` en un MP4.
     *
     * @return el MIME de la pista copiada, para el log.
     * @throws Failure "no_audio" si no hay pista o el códec no entra en un MP4,
     *                 "audio_vacio" si no se copió ninguna muestra, "cancel" si la cancelaron,
     *                 "error" para lo demas.
     */
    public static String extract(File source, File target, Watch watch) throws Failure {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        int track = -1;
        long copied = 0;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            track = findAudio(extractor);
            if (track < 0) {
                throw new Failure("no_audio", "el archivo no tiene ninguna pista audio/*");
            }
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = value(format.getString(MediaFormat.KEY_MIME));
            if (!muxable(mime)) {
                throw new Failure("no_audio",
                        mime + " no se puede escribir en un MP4 sin recodificar");
            }

            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new Failure("error", "no se pudo crear " + parent.getAbsolutePath());
            }
            extractor.selectTrack(track);
            muxer = new MediaMuxer(target.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(format);
            muxer.start();

            long durationUs = duration(format);
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                if (watch != null && watch.cancelled()) {
                    throw new Failure("cancel", "cancelado durante la extraccion");
                }
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) {
                    break;
                }
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = flags(extractor.getSampleFlags());
                muxer.writeSampleData(outTrack, buffer, info);
                copied++;
                if (watch != null && durationUs > 0) {
                    watch.onStage((int) (info.presentationTimeUs * 100 / durationUs));
                }
                extractor.advance();
            }
            if (copied == 0) {
                throw new Failure("audio_vacio", "la pista de audio no tenia muestras");
            }
            return mime;
        } catch (Failure failure) {
            throw failure;
        } catch (IllegalArgumentException e) {
            // addTrackFormat tira esto cuando el códec no tiene contenedor en MP4
            throw new Failure("no_audio", String.valueOf(e.getMessage()));
        } catch (Exception e) {
            throw new Failure("extraccion", String.valueOf(e));
        } finally {
            release(muxer, extractor);
        }
    }

    private static void release(MediaMuxer muxer, MediaExtractor extractor) {
        try {
            if (muxer != null) {
                muxer.release();
            }
        } catch (Exception ignored) {
            // un muxer a medio escribir deja archivo roto: el que llama lo borra igual
        }
        try {
            extractor.release();
        } catch (Exception ignored) {
        }
    }

    private static int findAudio(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = value(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME));
            if (mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    /** Codec que Android puede meter en un MP4 tal cual. Opus y Vorbis quedan afuera. */
    private static boolean muxable(String mime) {
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
     * Pasa las banderas de la muestra al muxer. El extractor entrega 1 (sincronica) y 2 (datos
     * del codec, el csd) con el mismo numero que espera MediaMuxer, asi que se copian igual; la
     * de frame parcial cambia (4 alla, 8 aqui) y por eso se traduce. Sin el 2 el m4a sale roto,
     * porque MediaMuxer no escribe el avcC/esds.
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
