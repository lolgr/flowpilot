package ai.flow.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Arrays;

import messaging.ZMQSubHandler;

public class CloudLogConsole implements Runnable {
    private static StringBuilder logs = new StringBuilder();
    private static CloudLogConsole instance = new CloudLogConsole();

    private ZMQSubHandler sh;
    private Thread consoleThead;
    private Thread gdxUiThread;

    private static Label messagesLabel;
    private static ScrollPane messagesScrollPane;

    public CloudLogConsole() { }

    // Should only be called by start() using postRunnable()
    private static void setGdxUiThread() {
        instance.gdxUiThread = Thread.currentThread();
    }

    public static CloudLogConsole getInstance() {
        return instance;
    }

    public static boolean isConsoleStarted() {
        return instance.consoleThead != null;
    }

    // Called from AndroidLauncher; starts consoleThread
    public static void start() {
        // Early return if console is already started
        if (isConsoleStarted()) return;

        instance.sh = new ZMQSubHandler(true);
        instance.sh.createSubscribers(Arrays.asList("logMessage", "errorLogMessage"));

        instance.consoleThead = new Thread(instance);
        instance.consoleThead.start();
    }

    // Adds a log message to the logs StringBuilder
    public static synchronized void println(String newLog) {
        logs.append(newLog);
        logs.append("\n");

        if (instance.gdxUiThread != null)
            updateLabel();
    }

    private static synchronized void updateLabel() {
        // Early return if console is not started
        if (!isConsoleStarted()) return;

        // Ensure we are on the gdx ui thread
        if (!Thread.currentThread().equals(instance.gdxUiThread)) {
            Gdx.app.postRunnable(() -> updateLabel());
            return;
        }

        messagesLabel.setText(logs.toString());
        messagesLabel.invalidateHierarchy();

        if (messagesScrollPane != null) {
            messagesScrollPane.layout();
            messagesScrollPane.setScrollPercentY(1f);
            messagesScrollPane.updateVisualScroll();
        }
    }

    // Clears the table and adds console specific elements
    public static void fillConsoleSettings(FlowUI appContext, Table cloudlogTable) {
        // Early return if console is not started
        if (!isConsoleStarted()) return;

        if (instance.gdxUiThread == null)
            Gdx.app.postRunnable(() -> setGdxUiThread());
        
        // Ensure we are on the gdx ui thread
        if (!Thread.currentThread().equals(instance.gdxUiThread)) {
            Gdx.app.postRunnable(() -> fillConsoleSettings(appContext, cloudlogTable));
            return;
        }

        float uiWidth = Gdx.app.getGraphics().getWidth();
        float uiHeight = Gdx.app.getGraphics().getHeight();
        float widthScale = uiWidth / 1280;
        float heightScale = uiHeight / 640;

        cloudlogTable.clear();

        Actor ancestor = cloudlogTable.getParent();
        while (ancestor != null && !(ancestor instanceof ScrollPane)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor != null) {
            ((ScrollPane) ancestor).setScrollingDisabled(true, true);
        }

        Table titleTable = new Table();
        Table logTable = new Table();
        cloudlogTable.add(titleTable).width(800 * widthScale).height(75 * heightScale).bottom().row();
        cloudlogTable.add(logTable).width(800 * widthScale).height(uiHeight - 100 * heightScale).fill().pad(20 * heightScale, 0, 0, 0);

        Label title = new Label("CloudLog Console", appContext.skin, "default-font-bold-med", "white");

        titleTable.bottom();
        titleTable.add(title).center().padTop(20 * heightScale).fillX().row();

        if (messagesLabel == null) {
            messagesLabel = new Label("", appContext.skin, "default-font-25", "white");
        }
        messagesLabel.setWrap(true);
        messagesLabel.setAlignment(Align.topLeft);
        updateLabel();

        Table logContent = new Table();
        logContent.top().left();
        logContent.add(messagesLabel).expand().fill().left().top().pad(10 * heightScale, 15 * widthScale, 10 * heightScale, 15 * widthScale);

        messagesScrollPane = new ScrollPane(logContent, appContext.skin);
        messagesScrollPane.setSmoothScrolling(true);
        messagesScrollPane.setFadeScrollBars(false);
        messagesScrollPane.setScrollingDisabled(true, false);

        logTable.add(messagesScrollPane).expand().fill().top();
    }

    // Runnable override; runs on thread start
    @Override
    public void run() {
        println("CloudLog started...\n");

        try {
            while (true) {
                if (sh.updated("logMessage")) {
                    String msg = sh.recv("logMessage").getLogMessage().toString();

                    JsonValue jsonValue = new JsonReader().parse(msg);
                    println(jsonValue.get("filename").asString() + " " + jsonValue.get("msg").toString());
                }
                if (sh.updated("errorLogMessage")) {
                    String msg = sh.recv("errorLogMessage").getLogMessage().toString();
                    
                    JsonValue jsonValue = new JsonReader().parse(msg);
                    println(jsonValue.get("filename").asString() + " " + jsonValue.get("msg").toString());
                }
                // Thread.sleep(10);
            }
        } catch (Exception e) { 
            println("Exception in CloudLogConsole run: " + e);
        }
    }

    // Should be called from SettingsScreen; stops consoleThread
    public static void stop() {
        try {
            instance.consoleThead.interrupt();
            instance.consoleThead.join();

        } catch (Exception e) {  }
        instance.consoleThead = null;
    }
}
