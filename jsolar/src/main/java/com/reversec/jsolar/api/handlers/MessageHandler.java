package com.reversec.jsolar.api.handlers;

import com.reversec.jsolar.api.InvalidMessageException;
import com.reversec.jsolar.api.Protobuf;

public interface MessageHandler {
    public Protobuf.Message handle(Protobuf.Message message) throws InvalidMessageException;
}
