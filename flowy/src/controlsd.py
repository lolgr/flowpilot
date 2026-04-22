import os
import runpy


# DBC lookup requires this
os.environ["BASEDIR"] = "./"

import system.version
system.version.get_short_branch = lambda x: "Release"

import time
from common.params import Params
params = Params()

from selfdrive.controls.controlsd import main

# Always true IsOnroad for debugging
params.put_bool("IsOnroad", True)
params.put_bool("IsOffroad", False)

while True:
    controlsd_onroad = params.get_bool("IsOnroad")
    if controlsd_onroad:
        main()
        break
    time.sleep(.1)
