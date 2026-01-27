package com.reversec.jsolar.logger;

public interface OnLogMessageListener<T> {
    public void onLogMessage(Logger<T> tLogger, LogMessage message);
}
