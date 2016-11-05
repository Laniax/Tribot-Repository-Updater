package updater;

import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScriptQueueViewer {
    @FXML WebView view;

    public void initialize() {
        WebEngine engine = view.getEngine();

        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Cookie", Arrays.asList(Controller.getCookies()));

        try {
            java.net.CookieHandler.getDefault().put(new URI("https://tribot.org/repository/scripter_panel/"), headers);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        engine.load("https://tribot.org/repository/scripter_panel/");
        engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                engine.executeScript("var ele = document.getElementById('nav'); ele.parentNode.removeChild(ele);");
                engine.executeScript("var ele = document.getElementsByTagName('header')[0]; ele.parentNode.removeChild(ele);");
                engine.executeScript("var ele = document.getElementsByTagName('header')[0]; ele.parentNode.removeChild(ele);");
                engine.executeScript("var ele = document.getElementsByClassName('panel-default')[0]; ele.parentNode.removeChild(ele);");
            }
        });
    }
}
