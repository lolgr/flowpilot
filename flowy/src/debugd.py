try:
    import cereal.messaging as messaging

    from system.swaglog import cloudlog

    sm = messaging.SubMaster(['can'])
    while True:
        can = messaging.recv_one_retry(sm.sock['can']).can

        # if can[0].address == 10:
        #     cloudlog.info(can.dat)

        # if not sm.all_checks():
        #     cloudlog.info("sm.all_checks() == false")

except Exception as e:
    try:
        from system.swaglog import cloudlog
        cloudlog.info(e)
    except:
        print(e)

