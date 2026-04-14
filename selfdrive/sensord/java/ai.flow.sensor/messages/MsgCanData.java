package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgCanData extends MessageBase {
    public org.capnproto.StructList.Builder<Definitions.CanData.Builder> canData;

    public MsgCanData() {
        super();
        initFields();
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        canData = event.initCan(1);
        canData.get(0).initDat(8);
    }
}
