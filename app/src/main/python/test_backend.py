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


if __name__ == '__main__':
    unittest.main()
