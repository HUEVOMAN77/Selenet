import importlib
import importlib.util
import io
import json
import os
import re
import shutil
import sys
import tarfile
import threading

import youtube_dl
from youtube_dl.utils import DownloadError

_SOURCE = 'apk'


class Abort(Exception):
    pass


_CONTROLS = {}
_LOCK = threading.Lock()


def _read_version():
    try:
        from youtube_dl.version import __version__
        return __version__
    except Exception:
        return 'desconocida'


def engine_info():
    return ['youtube-dl', _read_version(), _SOURCE]


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


def _error_class():
    """DownloadError del motor activo; cambia si se carga uno descargado."""
    try:
        return youtube_dl.utils.DownloadError
    except AttributeError:
        return DownloadError


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
    except _error_class() as err:
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


# ------------------------------------------------------------------ calidades


def _quality_of(item):
    return item.get('br') or item.get('size') or 0


def _best_by_height(items, wanted):
    chosen = {}
    for item in items:
        if item['kind'] != wanted or item['height'] <= 0:
            continue
        current = chosen.get(item['height'])
        if current is None or _quality_of(item) > _quality_of(current):
            chosen[item['height']] = item
    return chosen


def _best_audio(items):
    chosen = {}
    for item in items:
        if item['kind'] != 'audio':
            continue
        current = chosen.get(item['ext'])
        if current is None or _quality_of(item) > _quality_of(current):
            chosen[item['ext']] = item
    ordered = []
    for ext in ('m4a', 'mp3'):
        item = chosen.pop(ext, None)
        if item is not None:
            ordered.append(item)
    ordered.extend(sorted(chosen.values(), key=_quality_of, reverse=True))
    return ordered


_MERGE_HEIGHTS = (1080, 720, 480)


def pick(items):
    """Primero lo que se baja en un archivo; despues las alturas que piden PC."""
    single = _best_by_height(items, 'video')
    split = _best_by_height(items, 'merge')
    rows = sorted(single.values(), key=lambda item: item['height'], reverse=True)
    rows.extend(split[height] for height in _MERGE_HEIGHTS
                if height in split and height not in single)
    if not rows:
        unknown = [item for item in items if item['kind'] == 'video' and item['height'] <= 0]
        unknown.sort(key=_quality_of, reverse=True)
        rows = unknown[:1]
    audio = _best_audio(items)
    if not audio:
        strays = [item for item in items if item['kind'] == 'audio']
        strays.sort(key=_quality_of, reverse=True)
        audio = strays[:1]
    return (rows[:6] + audio[:3])


def _selector(fmt_id, index=0):
    """youtube-dl sanitizea los format_id antes de compararlos con el selector."""
    text = str(fmt_id) if fmt_id else str(index)
    return re.sub(r'[\s,/+\[\]()]', '_', text)


_AUDIO_EXTS = ('mp3', 'm4a', 'aac', 'ogg', 'opus', 'wav', 'flac', 'weba')


def _kind_of(raw):
    """'video' y 'audio' se bajan en un archivo; 'merge' necesita ffmpeg."""
    vcodec = raw.get('vcodec')
    acodec = raw.get('acodec')
    ext = (raw.get('ext') or '').lower()
    if vcodec is None or acodec is None:
        # el extractor no informa codecs: es un archivo completo
        return 'audio' if (not raw.get('height') and ext in _AUDIO_EXTS) else 'video'
    if vcodec == 'none' and acodec != 'none':
        return 'audio'
    if acodec == 'none' and vcodec != 'none':
        return 'merge'
    if vcodec == 'none' and acodec == 'none':
        return 'audio' if ext in _AUDIO_EXTS else 'video'
    return 'video'


def _to_int(value, default=0):
    """Convierte un valor a int de forma segura (soporta None, float o str invalido)."""
    if value is None:
        return default
    try:
        return int(float(value))
    except (ValueError, TypeError):
        return default


def _item(raw, index=0):
    return {
        'id': _selector(raw.get('format_id'), index),
        'kind': _kind_of(raw),
        'height': _to_int(raw.get('height')),
        'ext': (raw.get('ext') or '?'),
        'fps': _to_int(raw.get('fps')),
        'br': _to_int(raw.get('tbr') or raw.get('vbr')),
        'size': _to_int(raw.get('filesize') or raw.get('filesize_approx')),
    }


def _flatten(info):
    """Extractors que devuelven un solo stream suelto en vez de una lista."""
    if not info.get('url'):
        return []
    return [_item(info, 0)]


def formats(url):
    """JSON con los formatos que se pueden bajar en un solo archivo, sin ffmpeg."""
    options = {
        'quiet': True,
        'noplaylist': True,
        'skip_download': True,
        'socket_timeout': 20,
        'retries': 0,
    }
    try:
        with youtube_dl.YoutubeDL(options) as ydl:
            info = ydl.extract_info(url, download=False)
    except Abort:
        return json.dumps({'ok': False, 'kind': 'error', 'error': 'cancelado'})
    except _error_class() as err:
        return json.dumps({'ok': False, 'kind': _classify(str(err)), 'error': str(err)})
    except Exception as err:
        return json.dumps({'ok': False,
                           'kind': _classify('%s: %s' % (type(err).__name__, err)),
                           'error': str(err)})

    while info and 'entries' in info:
        entries = [entry for entry in (info.get('entries') or []) if entry]
        info = entries[0] if entries else None

    if info is None:
        return json.dumps({'ok': False, 'kind': 'unavailable', 'error': 'respuesta vacia'})

    items = []
    for position, raw in enumerate(info.get('formats') or []):
        if raw is None:
            continue
        items.append(_item(raw, position))

    if not items:
        items = _flatten(info)

    return json.dumps({
        'ok': True,
        'title': info.get('title') or '',
        'duration': int(info.get('duration') or 0),
        'formats': pick(items),
    })


# ------------------------------------------------------------- actualizar motor


def _drop_modules():
    for name in list(sys.modules):
        if name == 'youtube_dl' or name.startswith('youtube_dl.'):
            del sys.modules[name]


def _load_from(lib_dir):
    package = os.path.join(lib_dir, 'youtube_dl')
    init = os.path.join(package, '__init__.py')
    if not os.path.isfile(init):
        raise ValueError('no hay paquete youtube_dl en ' + lib_dir)
    spec = importlib.util.spec_from_file_location(
        'youtube_dl', init, submodule_search_locations=[package])
    module = importlib.util.module_from_spec(spec)
    sys.modules['youtube_dl'] = module
    spec.loader.exec_module(module)
    return module


def activate(lib_dir):
    """Carga el motor de lib_dir; si algo falla deja el del APK. Devuelve la version."""
    global youtube_dl, _SOURCE
    _drop_modules()
    if lib_dir:
        try:
            youtube_dl = _load_from(lib_dir)
            _SOURCE = 'descargado'
            return _read_version()
        except Exception:
            _drop_modules()
    youtube_dl = importlib.import_module('youtube_dl')
    _SOURCE = 'apk'
    return _read_version()


def use(lib_dir):
    """Lo llama Java al arrancar para recuperar el motor ya descargado."""
    if not lib_dir or not os.path.isdir(os.path.join(lib_dir, 'youtube_dl')):
        return activate(None)
    return activate(lib_dir)


def reset():
    return activate(None)


def _tuple_version(text):
    parts = []
    for piece in (text or '').split('.'):
        parts.append(int(piece) if piece.isdigit() else 0)
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts[:3])


def _peek_version(dir_path):
    try:
        with io.open(os.path.join(dir_path, 'youtube_dl', 'version.py'),
                     encoding='utf-8') as handle:
            for line in handle:
                match = re.match(r"__version__\s*=\s*['\"]([^'\"]+)['\"]", line.strip())
                if match:
                    return match.group(1)
    except Exception:
        pass
    return ''


def _extract(tar_path, out_dir):
    """Descomprime solo el paquete youtube_dl del tarball, sin salirse de out_dir."""
    root = os.path.normpath(out_dir)
    written = 0
    with tarfile.open(tar_path, 'r:*') as archive:
        for member in archive:
            if not member.isfile():
                continue
            parts = member.name.split('/')
            if len(parts) < 3 or parts[1] != 'youtube_dl' or '..' in parts:
                continue
            relative = '/'.join(parts[2:])
            destination = os.path.normpath(os.path.join(root, relative))
            if not destination.startswith(root + os.sep):
                continue
            parent = os.path.dirname(destination)
            if not os.path.isdir(parent):
                os.makedirs(parent)
            source = archive.extractfile(member)
            if source is None:
                continue
            try:
                with source, io.open(destination, 'wb') as out:
                    shutil.copyfileobj(source, out, 65536)
            finally:
                source.close()
            written += 1
    return written


def install(tar_path, lib_dir, force=False):
    """Instala el motor del tarball si es mas nuevo; si no, deja vigente el anterior."""
    current = _read_version()
    staging = os.path.join(lib_dir, '.nuevo')
    activo = os.path.join(lib_dir, 'activo')
    try:
        if not os.path.isdir(lib_dir):
            os.makedirs(lib_dir)
        if os.path.isdir(staging):
            shutil.rmtree(staging)
        # _extract suelta el contenido del paquete dentro del directorio que le den
        package = os.path.join(staging, 'youtube_dl')
        os.makedirs(package)
        written = _extract(tar_path, package)
        nuevo = _peek_version(staging)
        if written == 0 or not nuevo:
            return json.dumps({'ok': False, 'kind': 'format',
                               'error': 'el paquete no trae youtube_dl'})
        if not force and _tuple_version(nuevo) <= _tuple_version(current):
            return json.dumps({'ok': False, 'kind': 'igual', 'version': current,
                               'nueva': nuevo})
        if os.path.isdir(activo):
            shutil.rmtree(activo)
        os.rename(staging, activo)
        version = activate(activo)
        if _SOURCE != 'descargado':
            # el paquete bajo no arranca: se descarta para no reintentarlo al reiniciar
            shutil.rmtree(activo, True)
            return json.dumps({'ok': False, 'kind': 'format', 'version': version,
                               'error': 'el motor descargado no arranco, sigue el del APK'})
        return json.dumps({'ok': True, 'anterior': current, 'version': version,
                           'origen': _SOURCE, 'archivos': written})
    except Exception as err:
        return json.dumps({'ok': False,
                           'kind': _classify('%s: %s' % (type(err).__name__, err)),
                           'error': str(err)})
    finally:
        if os.path.isdir(staging):
            shutil.rmtree(staging, True)
        try:
            if os.path.isfile(tar_path):
                os.remove(tar_path)
        except OSError:
            pass
