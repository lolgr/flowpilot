package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgDriverState extends MessageBase {
    public ai.flow.definitions.Definitions.DriverStateV2.Builder driverState;
    private ai.flow.definitions.Definitions.DriverStateV2.DriverData.Builder driverData;

    public MsgDriverState() {
        super();
        initFields();
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        driverState = event.initDriverStateV2();
        driverData = driverState.initLeftDriverData(); // TODO: send driver side through constructor?
    }
}
