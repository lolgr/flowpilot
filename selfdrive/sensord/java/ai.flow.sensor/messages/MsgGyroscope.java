package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgGyroscope extends MessageBase {
    public ai.flow.definitions.Definitions.SensorEventData.Builder gyroscope;

    public MsgGyroscope() {
        super();
        initFields();
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        gyroscope = event.initGyroscope();
    }
}
