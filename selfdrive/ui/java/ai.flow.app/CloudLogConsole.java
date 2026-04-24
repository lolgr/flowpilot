package ai.flow.app;

import ai.flow.app.helpers.Utils;
import ai.flow.common.transformations.Camera;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import ai.flow.app.CalibrationScreens.CalibrationInfo;
import ai.flow.common.ParamsInterface;
import ai.flow.common.SystemUtils;
import ai.flow.common.utils;

import static ai.flow.app.FlowUI.getPaddedButton;

import java.util.ArrayList;
import java.util.Arrays;

import messaging.ZMQSubHandler;

public class CloudLogConsole implements Runnable {
    FlowUI appContext;
    ParamsInterface params = ParamsInterface.getInstance();
    float uiWidth = Gdx.app.getGraphics().getWidth();
    float uiHeight = Gdx.app.getGraphics().getHeight();
    float widthScale = uiWidth / 1280;
    float heightScale = uiHeight / 640;

    ZMQSubHandler sh;

    Table cloudlogTable;

    Thread consoleThead;
    Label messages;

    Texture lineTex = Utils.getLineTexture(Math.round(700*widthScale), Math.round(1*heightScale), Color.WHITE);

    public CloudLogConsole(FlowUI appContext, Table cloudlogTable) {
        this.appContext = appContext;
        this.cloudlogTable = cloudlogTable;

        messages = new Label("", appContext.skin, "default-font-30", "white");
        messages.setWrap(true);

        sh = new ZMQSubHandler(true);
        sh.createSubscribers(Arrays.asList("logMessage", "errorLogMessage"));

        addLogMessage("CloudLog started...\n");
    }

    public boolean consoleStarted() {
        return consoleThead != null;
    }

    // Should be called from SettingsScreen; starts consoleThread
    public void startConsole() {
        if (consoleStarted()) return;

        consoleThead = new Thread(this);
        consoleThead.start();
    }

    // Adds a log message to the messages label
    public void addLogMessage(String newLog) {
        StringBuilder builder = new StringBuilder(messages.getText());
        builder.append(newLog);
        builder.append("\n");
        messages.setText(builder.toString());
    }

    // Clears the table and adds console specific elements
    public void fillConsoleSettings() {
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

        messages.setAlignment(Align.topLeft);
        Table logContent = new Table();
        logContent.top().left();
        logContent.add(messages).expand().fill().left().top().pad(10 * heightScale, 15 * widthScale, 10 * heightScale, 15 * widthScale);

        ScrollPane logScrollPane = new ScrollPane(logContent, appContext.skin);
        logScrollPane.setSmoothScrolling(true);
        logScrollPane.setFadeScrollBars(false);
        logScrollPane.setScrollingDisabled(true, false);

        logTable.add(logScrollPane).expand().fill().top();
    }

    // Runnable override; runs on thread start
    @Override
    public void run() {
        try {
            while (true) {
                if (sh.updated("logMessage")) {
                    String msg = sh.recv("logMessage").getLogMessage().toString();
                    Gdx.app.postRunnable(() -> addLogMessage(msg));
                }
                if (sh.updated("errorLogMessage")) {
                    String msg = sh.recv("errorLogMessage").getLogMessage().toString();
                    Gdx.app.postRunnable(() -> addLogMessage(msg));
                }
                Thread.sleep(100);
            }
        } catch (Exception e) { 
            Gdx.app.postRunnable(() -> addLogMessage("Exception in CloudLogConsole run: " + e));
        }
    }

    // Should be called from SettingsScreen; stops consoleThread
    public void stopConsole() {
        try {
            consoleThead.interrupt();
            consoleThead.join();

        } catch (Exception e) { 
            Thread.currentThread().interrupt();
        }
        consoleThead = null;
    }
}
