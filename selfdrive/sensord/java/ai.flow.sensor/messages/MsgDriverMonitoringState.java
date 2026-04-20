package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgDriverMonitoringState extends MessageBase {
    public ai.flow.definitions.Definitions.DriverMonitoringState.Builder driverMonitoringState;

    public MsgDriverMonitoringState() {
        super();
        initFields();
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        driverMonitoringState = event.initDriverMonitoringState();
    }
}
