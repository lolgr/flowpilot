package ai.flow.app;

import ai.flow.app.helpers.Utils;
import ai.flow.common.transformations.Camera;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Gdx.app.postRunnable;
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
    Stage stage;
    float uiWidth = Gdx.app.getGraphics().getWidth();
    float uiHeight = Gdx.app.getGraphics().getHeight();
    float widthScale = uiWidth / 1280;
    float heightScale = uiHeight / 640;

    ZMQSubHandler sh;

    SpriteBatch batch;
    Table cloudlogTable;

    Thread consoleThead;

    Texture lineTex = Utils.getLineTexture(Math.round(700*widthScale), Math.round(1*heightScale), Color.WHITE);

    public CloudLogConsole(FlowUI appContext) {
        this.appContext = appContext;

        stage = new Stage(new ScreenViewport());
        batch = new SpriteBatch();

        sh = new ZMQSubHandler(true);
        sh.createSubscribers(Arrays.asList("logMessage", "errorLogMessage"));
    }

    public boolean consoleStarted() {
        return consoleThead != null;
    }

    // Should be called from SettingsScreen; starts consoleThread
    public void startConsole(Table cloudlogTable) {
        this.cloudlogTable = cloudlogTable;
        if (consoleStarted()) return;


        consoleThead = new Thread(this);
        consoleThead.start();
    }

    public void fillConsoleSettings() {
        cloudlogTable.clear();

        TextField.TextFieldStyle style = new TextField.TextFieldStyle(appContext.skin.get(TextField.TextFieldStyle.class));
        style.font = appContext.skin.getFont("default-font-20");
        style.fontColor = Color.WHITE;
        style.background = createRoundedDrawable(new Color(0.15f, 0.15f, 0.15f, 1f), 15);

        TextArea textArea = new TextArea("Testing", style);

        cloudlogTable.row();
        cloudlogTable.add(textArea).expand().fill().pad(10);
        cloudlogTable.row();
    }

    private NinePatchDrawable createRoundedDrawable(Color color, int radius) {
        int size = radius * 2 + 1;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(color);
        pixmap.fillCircle(radius, radius, radius);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return new NinePatchDrawable(new NinePatch(texture, radius, radius, radius, radius));
    }

    // Runnable override; runs on thread start
    @Override
    public void run() {
        fillConsoleSettings();

        while (true);
    }

    // Should be called from SettingsScreen; stops consoleThread
    public void stopConsole() {

    }
}
