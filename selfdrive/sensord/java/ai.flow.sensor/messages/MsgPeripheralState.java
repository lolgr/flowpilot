package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgPeripheralState extends MessageBase {
    public ai.flow.definitions.Definitions.PeripheralState.Builder peripheralState;

    public MsgPeripheralState() {
        super();
        initFields();
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        peripheralState = event.initPeripheralState();
    }
}
