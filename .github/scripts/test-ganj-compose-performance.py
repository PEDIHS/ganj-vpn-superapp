#!/usr/bin/env python3
import importlib.util
import pathlib
import unittest

script=pathlib.Path(__file__).with_name("ganj-compose-performance.py")
spec=importlib.util.spec_from_file_location("ganj_perf",script)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class GfxFrameTests(unittest.TestCase):
    def test_parses_only_valid_profile_frames(self):
        raw="""junk
---PROFILEDATA---
Flags,IntendedVsync,Vsync,FrameCompleted
0,100000000,100000001,120000000
1,100000000,100000001,250000000
0,130000000,130000001,160000000
---PROFILEDATA---
"""
        self.assertEqual(module.frame_durations(raw),[20.,30.])
        self.assertEqual(module.summarize_frames([20.,30.])["over_16_67_pct"],100.)
    def test_empty_is_missing_not_false_success(self):
        self.assertIsNone(module.summarize_frames([])["p95_ms"])
    def test_nearest_rank_percentiles(self):
        self.assertEqual(module.percentile([10,20,30,40,50], .9),50.)
    def test_pss(self):
        self.assertEqual(module.total_pss("TOTAL PSS: 34,500"),34500)
        self.assertIsNone(module.total_pss("not available"))
if __name__=="__main__":unittest.main()
