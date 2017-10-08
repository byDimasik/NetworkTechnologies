package ru.nsu.fit.g15205.shishlyannikov;

import sun.misc.Signal;
import sun.misc.SignalHandler;

/***
 * Этот класс был честно украден с Хабра https://habrahabr.ru/post/78035/
 * После чего был несколько видоизменен под мои нужды
 */
public class DiagnosticSignalHandler implements SignalHandler {

    // Static method to install the signal handler
    public static void install(String signalName, SignalHandler handler) {
        Signal signal = new Signal(signalName);
        DiagnosticSignalHandler diagnosticSignalHandler = new DiagnosticSignalHandler();
        Signal.handle(signal, diagnosticSignalHandler);
        diagnosticSignalHandler.setHandler(handler);
    }
    private SignalHandler handler;

    private DiagnosticSignalHandler() {
    }

    private void setHandler(SignalHandler handler) {
        this.handler = handler;
    }

    // Signal handler method
    @Override
    public void handle(Signal sig) {
        try {
            handler.handle(sig);
        } catch (Exception e) {
            System.out.println("Signal handler failed, reason " + e);
        }
    }
}
