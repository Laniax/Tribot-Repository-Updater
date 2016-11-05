package updater;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import updater.packer.Packer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller {
    private static CookieManager manager;

    List<JSONObject> scripts = new ArrayList<>();
    List<String> queueCheck = new ArrayList<>();
    HashMap<String, List<String>> paths = new HashMap<>();

    @FXML Button login;
    @FXML Button pack;
    @FXML Button queue;
    @FXML Button update;
    @FXML TextField username;
    @FXML TextField password;
    @FXML TextField google2fa;
    @FXML TableView<ScriptData> table = new TableView<>();

    public void initialize() {
        manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);

        try {
            if (Files.exists(Paths.get("paths.ini"))) {
                List<String> lines = Files.readAllLines(Paths.get("paths.ini"));
                for (String line : lines) {
                    String[] split = line.split("\\|\\|");
                    if (split.length == 2) {
                        String[] pathSplit = split[1].split(";");
                        paths.put(split[0], new ArrayList<>(Arrays.asList(pathSplit)));
                    }
                }
            }

            if (Files.exists(Paths.get("cookies.ini"))) {
                System.out.println("Loading cookies.");
                List<String> lines = Files.readAllLines(Paths.get("cookies.ini"));
                for (String line : lines) {
                    System.out.println(line);
                    String[] data = line.split("\\|\\|");
                    if (data.length == 11) {
                        System.out.println("Adding cookie: " + data[0]);
                        HttpCookie cookie = new HttpCookie(data[0], data[1]);
                        cookie.setComment(data[2]);
                        cookie.setCommentURL(data[3]);
                        cookie.setDiscard(Boolean.valueOf(data[4]));
                        cookie.setDomain(data[5]);
                        cookie.setMaxAge(Long.valueOf(data[6]));
                        cookie.setPath(data[7]);
                        cookie.setPortlist(data[8]);
                        cookie.setSecure(Boolean.valueOf(data[9]));
                        cookie.setVersion(Integer.valueOf(data[10]));
                        manager.getCookieStore().add(new URI(cookie.getDomain()), cookie);
                    }
                }

                if (!loadScripts()) {
                    System.out.println("Cookies no longer valid.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        TableColumn idCol = new TableColumn("ID");
        idCol.setMinWidth(50);
        idCol.setPrefWidth(98);
        idCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures, ObservableValue>() {
            @Override
            public ObservableValue call(TableColumn.CellDataFeatures param) {
                return new SimpleStringProperty(((ScriptData) param.getValue()).id);
            }
        });

        TableColumn updateCol = new TableColumn("✓");
        updateCol.setMinWidth(25);
        updateCol.setPrefWidth(30);
        updateCol.setStyle( "-fx-alignment: CENTER-RIGHT;");
        updateCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ScriptData, CheckBox>, ObservableValue<CheckBox>>() {
            @Override
            public ObservableValue<CheckBox> call(TableColumn.CellDataFeatures<ScriptData, CheckBox> arg0) {
                ScriptData row = arg0.getValue();
                CheckBox checkBox = new CheckBox();
                checkBox.setAlignment(Pos.CENTER);
                checkBox.selectedProperty().setValue(row.selected);
                checkBox.selectedProperty().addListener((ov, old_val, new_val) -> row.selected = new_val);
                return new SimpleObjectProperty<>(checkBox);
            }
        });

        TableColumn nameCol = new TableColumn("Name");
        nameCol.setMinWidth(100);
        nameCol.setPrefWidth(200);
        nameCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures, ObservableValue>() {
            @Override
            public ObservableValue call(TableColumn.CellDataFeatures param) {
                return new SimpleStringProperty(((ScriptData) param.getValue()).name);
            }
        });

        TableColumn versionCol = new TableColumn("Version");
        versionCol.setMinWidth(50);
        versionCol.setPrefWidth(100);
        versionCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures, ObservableValue>() {
            @Override
            public ObservableValue call(TableColumn.CellDataFeatures param) {
                return new SimpleStringProperty(((ScriptData) param.getValue()).version);
            }
        });

        TableColumn packCol = new TableColumn("Pack Name");
        packCol.setMinWidth(100);
        packCol.setPrefWidth(150);
        packCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures, ObservableValue>() {
            @Override
            public ObservableValue call(TableColumn.CellDataFeatures param) {
                return new SimpleStringProperty(((ScriptData) param.getValue()).packName);
            }
        });

        TableColumn statusCol = new TableColumn("Status");
        statusCol.setMinWidth(100);
        statusCol.setPrefWidth(175);
        statusCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures, ObservableValue>() {
            @Override
            public ObservableValue call(TableColumn.CellDataFeatures param) {
                return new SimpleStringProperty(((ScriptData) param.getValue()).status);
            }
        });

        TableColumn pathCol = new TableColumn("Paths");
        pathCol.setMinWidth(40);
        pathCol.setPrefWidth(45);
        pathCol.setCellFactory(col -> {
            Button editButton = new Button("Edit");
            TableCell<ScriptData, Boolean> cell = new TableCell<ScriptData, Boolean>() {
                @Override
                public void updateItem(Boolean data, boolean empty) {
                    super.updateItem(data, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(editButton);
                    }
                }
            };

            editButton.setOnAction(e -> editPaths(cell.getTableView().getItems().get(cell.getIndex())));
            return cell ;
        });

        table.getColumns().clear();
        table.getColumns().addAll(updateCol, idCol, nameCol, versionCol, packCol, statusCol, pathCol);
    }

    private void editPaths(ScriptData data) {
        Stage stage = new Stage();

        Pane pane = new Pane();
        stage.setTitle(data.name + " Paths");
        ListView<String> listView = new ListView<>();
        listView.relocate(10, 10);
        listView.setPrefWidth(300);
        listView.setPrefHeight(300);
        if (paths.containsKey(data.name)) {
            listView.setItems(FXCollections.observableArrayList(paths.get(data.name)));
        }

        Button remove = new Button("Delete Selected");
        remove.setPrefWidth(100);
        remove.relocate(10, 320);
        remove.setOnAction(event -> {
            if (listView.getSelectionModel().getSelectedItems() != null && paths.containsKey(data.name)) {
                paths.get(data.name).remove(listView.getSelectionModel().getSelectedItem());
                listView.setItems(FXCollections.observableArrayList(paths.get(data.name)));
                listView.refresh();
            }
        });

        Button add = new Button("Add Path");
        add.setPrefWidth(100);
        add.relocate(210, 320);
        add.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select src paths for " + data.name);
            File path = chooser.showDialog(stage);

            if (path == null) return;

            if (!paths.containsKey(data.name)) {
                paths.put(data.name, new ArrayList<>());
            }

            paths.get(data.name).add(path.toString());
            listView.setItems(FXCollections.observableArrayList(paths.get(data.name)));
            listView.refresh();
        });

        pane.setPrefWidth(320);
        pane.setPrefHeight(350);
        pane.getChildren().addAll(listView, remove, add);

        stage.setScene(new Scene(pane));
        stage.showAndWait();

        List<String> lines = new ArrayList<>();
        for (String script : paths.keySet()) {
            StringBuilder builder = new StringBuilder();
            for (String path : paths.get(script)) {
                builder.append(path + ";");
            }

            lines.add(script + "||" + builder.toString());
        }

        try {
            Files.write(Paths.get("paths.ini"), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onUpdateButtonAction(ActionEvent event) {
        boolean uploaded = false;
        for (ScriptData data : table.getItems()) {
            if (data.selected) {
                File zip = Packer.getZip(data.name);
                if (zip != null) {
                    System.out.println("Found zip for " + data.name);
                    updateScript(data, zip);
                    queueCheck.add(data.name);
                    uploaded = true;
                } else {
                    System.out.println("No zip found for " + data.name);
                }
            }
        }

        if (uploaded) {
            new Thread(() -> {
                while (queueCheck.size() > 0) {
                    try {
                        Thread.sleep(5000);
                        System.out.println("Checking script queue for data.");
                        if (!checkQueue()) {
                            if (!login(username.getText(), password.getText())) {
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private boolean checkQueue() {
        try {
            String html = loadPage("https://tribot.org/repository/data/scripter_panel/script_queue?_=" + System.currentTimeMillis());
            System.out.println(html);

            if (html.equals("")) return false;
            JSONObject data = new JSONObject(html);
            JSONArray queueInfo = data.getJSONArray("aaData");
            List<String> toRemove = new ArrayList<>();

            for (String name : queueCheck) {
                OUT: for (int i = queueInfo.length() - 1; i > 0; i--) {
                    JSONObject info = queueInfo.getJSONObject(i);
                    if (info.getString("name").equals(name)) {
                        for (ScriptData scriptData : table.getItems()) {
                            if (scriptData.name.equals(name)) {
                                scriptData.status = info.getString("status");
                                if (!info.getString("status").equals("PENDING")) {
                                    toRemove.add(name);
                                }

                                break OUT;
                            }
                        }
                    }
                }
            }

            table.refresh();
            for (String remove : toRemove) {
                queueCheck.remove(remove);
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void updateScript(ScriptData script, File zip) {
        if (!Packer.hasPacked()) {
            packScripts();
        }

        String url = "https://tribot.org/repository/script/edit/#/source/".replace("#", script.id);
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("version", script.version);

        try {
            MultipartFormDataUtil multipartRequest = new MultipartFormDataUtil(url, params, zip);
            List<String> response = multipartRequest.getResponse();
            response.forEach(System.out::println);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void packScripts() {
        try {
            Packer.load(paths);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Script Packer Complete");
            alert.setHeaderText(null);
            alert.setContentText("You're scripts have been successfully packaged.");

            for (ScriptData data : table.getItems()) {
                File zip = Packer.getZip(data.name);
                data.packName = zip == null ? "None Found" : zip.getName();
            }

            table.refresh();
            alert.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onPackButtonAction(ActionEvent event) {
        packScripts();
    }

    @FXML
    protected void onLoginButtonAction(ActionEvent event) {
        try {
            if (!loadScripts()) {
                login(username.getText(), password.getText());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onViewQueueAction(ActionEvent event) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("fxml/queue.fxml"));
            Parent root1 = fxmlLoader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root1));
            stage.show();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private String loadPage(String url) throws IOException {
        try {
            URLConnection con = new URL(url).openConnection();
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.28 Safari/537.36");
            con.setRequestProperty("Cookie", getCookies());

            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null)
                stringBuilder.append(line + "\n");

            reader.close();

            List<String> lines = new ArrayList<>();
            Path file = Paths.get("cookies.ini");

            for (HttpCookie cookie : manager.getCookieStore().getCookies()) {
                StringBuilder b = new StringBuilder();
                b.append(cookie.getName() + "||");
                b.append(cookie.getValue() + "||");
                b.append(cookie.getComment() + "||");
                b.append(cookie.getCommentURL() + "||");
                b.append(cookie.getDiscard() + "||");
                b.append(cookie.getDomain() + "||");
                b.append(cookie.getMaxAge() + "||");
                b.append(cookie.getPath() + "||");
                b.append(cookie.getPortlist() + "||");
                b.append(cookie.getSecure() + "||");
                b.append(cookie.getVersion() + "||");
                lines.add(b.toString());
            }
            Files.write(file, lines, Charset.forName("UTF-8"));

            return stringBuilder.toString();
        }  catch (IOException e) {
            return "";
        }
    }

    private boolean loadScripts() throws IOException {
        String html = loadPage("https://tribot.org/repository/data/scripter_panel/published_scripts?_=" + System.currentTimeMillis());
        if (html.equals("")) return false;

        try {
            JSONObject json = new JSONObject(html);
            JSONArray jsonScripts = (JSONArray) json.get("aaData");

            for (int index = 0; index < jsonScripts.length(); index++) {
                scripts.add((JSONObject) jsonScripts.get(index));
                table.getItems().add(new ScriptData((JSONObject) jsonScripts.get(index)));
                System.out.println("Adding script: " + ((JSONObject) jsonScripts.get(index)).getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return true;
    }

    private String getFormValue(String inputName, String html) {
        Pattern pattern = Pattern.compile("name=\\\"" + inputName + "\\\" value=\"(.*)\\\"");
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean login(String username, String password) throws IOException {
        String html = loadPage("https://tribot.org/forums/login/");

        String loginStandardSubmitted = getFormValue("login__standard_submitted", html);
        String csrf = getFormValue("csrfKey", html);
        String ref = getFormValue("ref", html);
        String maxFileSize = getFormValue("MAX_FILE_SIZE", html);
        String plupload = getFormValue("plupload", html);

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("login__standard_submitted", loginStandardSubmitted);
        params.put("csrfKey", csrf);
        params.put("ref", ref);
        params.put("MAX_FILE_SIZE", maxFileSize);
        params.put("plupload", plupload);
        params.put("auth", username);
        params.put("password", password);
        params.put("google2fa", google2fa.getText());
        params.put("remember_me", "0");
        params.put("remember_me_checkbox", "1");
        params.put("signin_anonymous", "0");

        MultipartFormDataUtil multipartRequest = new MultipartFormDataUtil("https://tribot.org/forums/login/", params);
        List<String> response = multipartRequest.getResponse();
        return loadScripts();
    }

    public static String getCookies() {
        StringBuilder builder = new StringBuilder();
        for (HttpCookie cookie : Controller.manager.getCookieStore().getCookies()) {
            builder.append(cookie.getName() + "=" + cookie.getValue() + ";");
        }
        return builder.toString();
    }
}
