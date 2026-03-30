package com.reversec.jsolar.api.sessions;

import android.os.Looper;

import com.reversec.jsolar.api.InvalidMessageException;
import com.reversec.jsolar.api.Protobuf;
import com.reversec.jsolar.api.handlers.MessageHandler;
import com.reversec.jsolar.api.handlers.ReflectionMessageHandler;
import com.reversec.jsolar.api.links.Link;
import com.reversec.jsolar.connection.AbstractSession;
import com.reversec.jsolar.reflection.ObjectStore;

public class Session extends AbstractSession {

    private Link connector = null;
    public ObjectStore object_store = new ObjectStore();
    private MessageHandler reflection_message_handler = new ReflectionMessageHandler(this);

    public Session(Link connector) {
        super();

        this.connector = connector;
    }

    protected Session(String session_id) {
        super(session_id);
    }

    public static Session nullSession() {
        return new Session("null");
    }

    @Override
    protected Protobuf.Message handleMessage(Protobuf.Message message) throws InvalidMessageException {
        return this.reflection_message_handler.handle(message);
    }

    @Override
    public void run(){
        Looper.prepare();

        super.run();
    }

    @Override
    public void send(Protobuf.Message message) {
        this.connector.send(message);
    }

}
