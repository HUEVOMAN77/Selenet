import os
import threading

import youtube_dl


def start(url, outdir, fmt, sink):
    thread = threading.Thread(target=_run, args=(url, outdir, fmt, sink))
    thread.daemon = True
    thread.start()
    return True


class _SinkLogger(object):
    def __init__(self, sink):
        self._sink = sink

    def debug(self, msg):
        self._emit(msg)

    def info(self, msg):
        self._emit(msg)

    def warning(self, msg):
        self._emit('AVISO: ' + msg)

    def error(self, msg):
        self._emit('ERROR: ' + msg)

    def _emit(self, msg):
        if msg.startswith('[debug] '):
            return
        self._sink.log(msg)


def _progress_hook(sink):
    last = [0]

    def hook(d):
        status = d.get('status')
        if status == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            done = d.get('downloaded_bytes') or 0
            percent = int(done * 100 / total) if total else 0
            if percent != last[0]:
                last[0] = percent
                sink.log('%d%%' % percent)
        elif status == 'finished':
            last[0] = 0
            sink.log('descarga terminada, procesando archivo...')

    return hook


def _run(url, outdir, fmt, sink):
    options = {
        'format': fmt or 'best',
        'outtmpl': os.path.join(outdir, '%(title)s.%(ext)s'),
        'noplaylist': True,
        'quiet': True,
        'logger': _SinkLogger(sink),
        'progress_hooks': [_progress_hook(sink)],
    }
    try:
        with youtube_dl.YoutubeDL(options) as ydl:
            ydl.extract_info(url, download=True)
        sink.log('LISTO')
    except Exception as exc:
        sink.log('FALLO: %s' % exc)
