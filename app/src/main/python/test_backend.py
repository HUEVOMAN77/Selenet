import io
import shutil
import tempfile
import unittest
import sys
import os
from unittest.mock import MagicMock

# Mock youtube_dl if it is not installed in the environment
try:
    import youtube_dl
except ImportError:
    mock_ytdl = MagicMock()
    mock_ytdl.utils.DownloadError = Exception
    sys.modules['youtube_dl'] = mock_ytdl
    sys.modules['youtube_dl.utils'] = mock_ytdl.utils

# Add backend directory to sys.path
sys.path.insert(0, os.path.dirname(__file__))

import backend


class TestBackend(unittest.TestCase):

    def test_human_rate(self):
        self.assertEqual(backend._human_rate(0), '')
        self.assertEqual(backend._human_rate(512), '512.0 B/s')
        self.assertEqual(backend._human_rate(1024), '1.0 KiB/s')
        self.assertEqual(backend._human_rate(1048576), '1.0 MiB/s')

    def test_eta(self):
        self.assertEqual(backend._eta(None), '')
        self.assertEqual(backend._eta(-5), '')
        self.assertEqual(backend._eta(45), '0:45')
        self.assertEqual(backend._eta(125), '2:05')
        self.assertEqual(backend._eta(3665), '1:01:05')

    def test_tuple_version(self):
        self.assertEqual(backend._tuple_version("2025.04.07"), (2025, 4, 7))
        self.assertEqual(backend._tuple_version("2025.4"), (2025, 4, 0))
        self.assertEqual(backend._tuple_version("invalid"), (0, 0, 0))

    def test_classify(self):
        self.assertEqual(backend._classify("urlopen error: timed out"), "network")
        self.assertEqual(backend._classify("HTTP Error 403: Forbidden"), "forbidden")
        self.assertEqual(
            backend._classify("ERROR: giving up after 3 fragment retries"), "forbidden")
        self.assertEqual(backend._classify("no space left on device"), "space")
        self.assertEqual(backend._classify("This video is private"), "private")
        self.assertEqual(backend._classify("Requested format is not available"), "format")
        self.assertEqual(backend._classify("Unsupported URL"), "unsupported")
        self.assertEqual(backend._classify("Video is unavailable"), "unavailable")
        self.assertEqual(backend._classify("Unknown error"), "error")

    def test_kind_of(self):
        raw_video = {'vcodec': 'h264', 'acodec': 'aac', 'ext': 'mp4'}
        self.assertEqual(backend._kind_of(raw_video), 'video')

        raw_audio = {'vcodec': 'none', 'acodec': 'mp3', 'ext': 'mp3'}
        self.assertEqual(backend._kind_of(raw_audio), 'audio')

        raw_merge = {'vcodec': 'h264', 'acodec': 'none', 'ext': 'mp4'}
        self.assertEqual(backend._kind_of(raw_merge), 'merge')

    def test_to_int(self):
        self.assertEqual(backend._to_int(None), 0)
        self.assertEqual(backend._to_int("360"), 360)
        self.assertEqual(backend._to_int(720.5), 720)
        self.assertEqual(backend._to_int("invalid", 10), 10)

    def test_item(self):
        raw = {
            'format_id': '18',
            'vcodec': 'h264',
            'acodec': 'aac',
            'height': '360.0',
            'ext': 'mp4',
            'fps': 29.97,
            'tbr': '500',
            'filesize': None
        }
        item = backend._item(raw, 0)
        self.assertEqual(item['id'], '18')
        self.assertEqual(item['kind'], 'video')
        self.assertEqual(item['height'], 360)
        self.assertEqual(item['ext'], 'mp4')
        self.assertEqual(item['fps'], 29)
        self.assertEqual(item['br'], 500)
        self.assertEqual(item['size'], 0)


class Rejected(Exception):
    """Lo que lanza el motor cuando YouTube responde 403."""


class _Sink(object):
    def __init__(self):
        self.events = []

    def onLog(self, msg):
        self.events.append(('log', msg))

    def onTitle(self, title):
        self.events.append(('title', title))

    def onProgress(self, *args):
        pass

    def onDone(self, path, size):
        self.events.append(('done', path))

    def onError(self, kind, detail):
        self.events.append(('error', kind))

    def onStopped(self, action, partial):
        self.events.append(('stopped', action))


class _YoutubeDLSpy(object):
    """Registra que cliente le pasan por options y falla con los clientes `blocked`."""

    def __init__(self, blocked, outfile, message=None):
        self.blocked = blocked
        self.outfile = outfile
        self.message = message
        self.seen = []
        self.attempts = []

    def __call__(self, options):
        self.last = options
        return self

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def extract_info(self, url, download=True):
        client = self.last['youtube_player_clients'][0]
        self.seen.append(client)
        self.attempts.append((client, self.last.get('continuedl'),
                              self.last.get('fragment_retries')))
        if client in self.blocked:
            raise Rejected(self.message or
                           'ERROR: unable to download video data: HTTP Error 403: Forbidden')
        with io.open(self.outfile, 'wb') as handle:
            handle.write(b'datos')
        return {'title': 'un audio'}

    def prepare_filename(self, info):
        return self.outfile


class TestClientFallback(unittest.TestCase):
    """El 403 de YouTube se resuelve cambiando de cliente, no reintentando el mismo."""

    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix='ydl-test-')
        self.outfile = os.path.join(self.dir, 'audio.m4a')
        self.real = backend.youtube_dl

    def tearDown(self):
        backend.youtube_dl = self.real
        shutil.rmtree(self.dir, True)

    def run_blocked(self, task_id, blocked, fmt='best', message=None):
        """Activa el motor de mentira y descarga con los clientes marcados como 403."""
        spy = _YoutubeDLSpy(blocked, self.outfile, message)

        class utils(object):
            DownloadError = Rejected

        class stub(object):
            pass

        holder = stub()
        holder.YoutubeDL = spy
        holder.utils = utils
        backend.youtube_dl = holder
        self.spy = spy
        sink = _Sink()
        return backend.run(task_id, 'https://youtu.be/x', self.dir, fmt, sink), sink

    def test_reintenta_con_el_cliente_siguiente(self):
        ok, sink = self.run_blocked('t1', [backend.CLIENTS[0]], 'bestaudio')
        self.assertTrue(ok)
        self.assertEqual(self.spy.seen, backend.CLIENTS[:2])
        self.assertIn(('done', self.outfile), sink.events)

    def test_no_rehace_la_ruta_cuando_el_primero_pasa(self):
        ok, _sink = self.run_blocked('t2', [])
        self.assertTrue(ok)
        self.assertEqual(self.spy.seen, [backend.CLIENTS[0]])

    def test_agota_la_cadena_y_reporta_bloqueo(self):
        ok, sink = self.run_blocked('t3', list(backend.CLIENTS))
        self.assertFalse(ok)
        self.assertEqual(self.spy.seen, backend.CLIENTS)
        self.assertEqual(sink.events[-1], ('error', 'forbidden'))

    def test_los_reintentos_no_sigan_el_corte_previo(self):
        self.run_blocked('t4', [backend.CLIENTS[0]])
        first, second = self.spy.attempts
        self.assertEqual(first[1], True)
        self.assertEqual(second[1], False)
        self.assertLess(second[2], first[2])

    def test_un_error_de_red_no_cambia_de_cliente(self):
        # sin 403 cualquier cliente se cae igual: no hay que insistir con otros
        ok, sink = self.run_blocked('t5', list(backend.CLIENTS),
                                    message='urlopen error timed out')
        self.assertFalse(ok)
        self.assertEqual(self.spy.seen, [backend.CLIENTS[0]])
        self.assertEqual(sink.events[-1], ('error', 'network'))


class TestEngineGuard(unittest.TestCase):
    """Actualizar el motor no puede instalar un engine que no cambia de cliente."""

    def _tree(self, body):
        root = tempfile.mkdtemp(prefix='ydl-engine-')
        self.addCleanup(shutil.rmtree, root, True)
        extractor = os.path.join(root, 'youtube_dl', 'extractor')
        os.makedirs(extractor)
        with io.open(os.path.join(extractor, 'youtube.py'), 'w', encoding='utf-8') as handle:
            handle.write(body)
        return root

    def test_motor_org_no_pasa(self):
        self.assertFalse(backend._supports_clients(self._tree('client_names = candidates[:1]')))

    def test_fork_parchado_pasa(self):
        self.assertTrue(backend._supports_clients(
            self._tree("wanted = params.get('youtube_player_clients')")))

    def test_sin_archivo_no_explota(self):
        root = tempfile.mkdtemp(prefix='ydl-vacio-')
        self.addCleanup(shutil.rmtree, root, True)
        self.assertFalse(backend._supports_clients(root))


if __name__ == '__main__':
    unittest.main()
