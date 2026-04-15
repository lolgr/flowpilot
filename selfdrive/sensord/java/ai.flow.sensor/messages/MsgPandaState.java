package ai.flow.sensor.messages;

import ai.flow.definitions.Definitions;
import ai.flow.definitions.MessageBase;

public class MsgPandaState extends MessageBase {
    public org.capnproto.StructList.Builder<Definitions.PandaState.Builder> pandaStates;

    public MsgPandaState() {
        super();
        initFields();
        bytesSerializedForm = computeSerializedMsgBytes();
        initSerializedBuffer();
    }

    private void initFields(){
        event = messageBuilder.initRoot(Definitions.Event.factory);
        pandaStates = event.initPandaStates(1);
    }
}
