try:
    import cereal.messaging as messaging
    import time
    from selfdrive.car.car_helpers import get_one_can

    from system.swaglog import cloudlog

    sm = messaging.SubMaster(['can'])
    # can_sock = messaging.sub_sock('can')
    while True:
        # can = get_one_can(can_sock)
        # can = messaging.recv_one_retry(sm.sock['can']).can
        sm.update(0)

        if sm.updated['can']:
            can = sm['can']
            if can[0].address == 0x02:
                raw = int.from_bytes(can[0].dat[0:2], byteorder="little", signed=True) * 0.1
                # cloudlog.info(can[0].dat)
                cloudlog.info(f"Found Steering: {raw}")

        # if not sm.all_checks():
        #     cloudlog.info("sm.all_checks() == false")

except Exception as e:
    try:
        from system.swaglog import cloudlog
        cloudlog.info(e)
    except:
        print(e)

