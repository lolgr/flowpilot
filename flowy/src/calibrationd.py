from system.swaglog import cloudlog

try:
    import runpy

    import time
    from warnings import catch_warnings
    from common.params import Params
    params = Params()

    from selfdrive.calibration.calibrationd import main
    while True:
        controlsd_onroad = params.get_bool("IsOnroad")
        if controlsd_onroad:
            main()
            break
        time.sleep(.1)
except Exception as e:
    cloudlog.info(f"calibrationd Exception: {e}")
