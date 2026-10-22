import os
import re
import threading

import youtube_dl
from youtube_dl.utils import DownloadError

try:
    from youtube_dl.version import __version__ as ENGINE_VERSION
except ImportError:
    ENGINE_VERSION = 'desconocida'


class Abort(Exception):
    pass


_CONTROLS = {}
_LOCK = threading.Lock()


def engine_info():
    return ['youtube-dl', ENGINE_VERSION, 'apk']


def register(task_id):
    with _LOCK:
        _CONTROLS[task_id] = {'stop': None}
    return True


def request(task_id, action):
    with _LOCK:
        slot = _CONTROLS.get(task_id)
        if slot is not None:
            slot['stop'] = action
    return True


def forget(task_id):
    with _LOCK:
        _CONTROLS.pop(task_id, None)
    return True


def _stop_action(task_id):
    with _LOCK:
        slot = _CONTROLS.get(task_id)
        return slot['stop'] if slot else None


_NETWORK = re.compile(
    r'urlopen error|timed out|timeout|unable to download|unable to open url|'
    r'connection reset|connection aborted|temporary failure in name resolution|'
    r'remote end closed|ssl|http error 5\d\d|getaddrinfo|network',
    re.I)
_PRIVATE = re.compile(r'private|log in|cookies|age restricted|members only|frs', re.I)
_UNAVAILABLE = re.compile(
    r'not available|no longer|removed|unplayable|geo|region|'
    r'not a valid url|video is unavailable|please play', re.I)
_UNSUPPORTED = re.compile(r'unsupported url|no suitable extractor|extractor not found', re.I)
_SPACE = re.compile(r'no space left|unable to create|errno 28|disk full', re.I)
_FORMAT = re.compile(r'requested format|format .{0,40}not (found|available)|no formats', re.I)


def _classify(text):
    if _SPACE.search(text):
        return 'space'
    if _UNSUPPORTED.search(text):
        return 'unsupported'
    if _PRIVATE.search(text):
        return 'private'
    if _FORMAT.search(text):
        return 'format'
    if _NETWORK.search(text):
        return 'network'
    if _UNAVAILABLE.search(text):
        return 'unavailable'
    return 'error'


def retryable(kind):
    return kind == 'network'


class _SinkLogger(object):
    def __init__(self, sink, task_id):
        self._sink = sink
        self._task_id = task_id

    def debug(self, msg):
        self._emit(msg)

    def info(self, msg):
        self._emit(msg)

    def warning(self, msg):
        self._emit('AVISO: ' + msg)

    def error(self, msg):
        self._emit('ERROR: ' + msg)

    def _emit(self, msg):
        if msg.startswith('[debug] ') or msg.startswith('youtube-dl ') or msg == '':
            return
        if _stop_action(self._task_id):
            raise Abort()
        self._sink.onLog(msg)


def _human_rate(value):
    if not value:
        return ''
    units = ['B/s', 'KiB/s', 'MiB/s', 'GiB/s']
    size = float(value)
    for unit in units:
        if size < 1024 or unit == units[-1]:
            return '%.1f %s' % (size, unit)
        size /= 1024.0
    return ''


def _eta(seconds):
    if not seconds or seconds < 0:
        return ''
    minutes, sec = divmod(int(seconds), 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return '%d:%02d:%02d' % (hours, minutes, sec)
    return '%d:%02d' % (minutes, sec)


def run(task_id, url, outdir, fmt, sink):
    """Descarga un item. Bloqueante: lo llama el hilo del servicio."""
    register(task_id)
    state = {'final': None, 'partial': None, 'percent': -1}

    def hook(d):
        if _stop_action(task_id):
            raise Abort()
        status = d.get('status')
        if status not in ('downloading', 'finished'):
            return
        state['final'] = d.get('filename') or state['final']
        state['partial'] = d.get('tmpfilename') or state['partial']
        if status == 'finished':
            state['percent'] = 100
            sink.onProgress(100, '', '', state['final'], state['partial'])
            return
        total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
        done = d.get('downloaded_bytes') or 0
        percent = int(done * 100 / total) if total else 0
        if percent != state['percent']:
            state['percent'] = percent
            sink.onProgress(percent, _human_rate(d.get('download_rate')),
                            _eta(d.get('eta')), state['final'], state['partial'])

    options = {
        'format': fmt or 'best',
        'outtmpl': os.path.join(outdir, '%(title)s.%(ext)s'),
        'noplaylist': True,
        'quiet': True,
        'no_warnings': False,
        'logger': _SinkLogger(sink, task_id),
        'progress_hooks': [hook],
        'retries': 0,
        'fragment_retries': 3,
        'socket_timeout': 30,
        'continuedl': True,
        'concurrent_fragment_downloads': 2,
    }

    stop = None
    try:
        with youtube_dl.YoutubeDL(options) as ydl:
            try:
                info = ydl.extract_info(url, download=True)
                if info and info.get('title'):
                    sink.onTitle(info['title'])
                try:
                    state['final'] = ydl.prepare_filename(info) or state['final']
                except Exception:
                    pass
            except Abort:
                stop = _stop_action(task_id)
                if not stop:
                    raise
    except Abort:
        stop = _stop_action(task_id) or 'cancel'
    except DownloadError as err:
        if _stop_action(task_id):
            stop = _stop_action(task_id)
        else:
            sink.onError(_classify(str(err)), str(err))
            return False
    except Exception as err:
        if _stop_action(task_id):
            stop = _stop_action(task_id)
        else:
            sink.onError(_classify('%s: %s' % (err.__class__.__name__, err)), str(err))
            return False

    if stop == 'pause':
        sink.onStopped('pause', state['partial'])
        return False
    if stop:
        sink.onStopped('cancel', state['partial'])
        return False

    path = state['final']
    if path and os.path.exists(path):
        sink.onDone(path, os.path.getsize(path))
        return True
    sink.onError('error', 'el motor termino sin dejar un archivo visible')
    return False
