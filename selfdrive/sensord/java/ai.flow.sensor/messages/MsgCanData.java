package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgCanData extends MessageBase {
    public org.capnproto.StructList.Builder<Definitions.CanData.Builder> canData;

    public MsgCanData() {
        super();
        initFields(8);
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    public MsgCanData(int datSize) {
        super();
        initFields(datSize);
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(int datSize){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        canData = event.initCan(1);
        canData.get(0).initDat(datSize);
    }
}
