package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgGpsLocationExternal extends MessageBase {
    public ai.flow.definitions.Definitions.GpsLocationData.Builder gpsLocationExternal;

    public MsgGpsLocationExternal() {
        super();
        initFields();
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        gpsLocationExternal = event.initGpsLocationExternal();
    }
}
