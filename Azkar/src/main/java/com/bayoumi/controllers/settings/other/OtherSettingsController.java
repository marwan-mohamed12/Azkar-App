package com.bayoumi.controllers.settings.other;

import com.bayoumi.controllers.settings.SettingsInterface;
import com.bayoumi.models.settings.OtherSettings;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.util.Logger;
import com.bayoumi.util.db.DatabaseManager;
import com.bayoumi.util.gui.BuilderUI;
import com.bayoumi.util.gui.HelperMethods;
import com.bayoumi.util.time.HijriDate;
import com.bayoumi.util.update.UpdateHandler;
import com.jfoenix.controls.JFXCheckBox;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.*;
import java.net.URI;
import java.net.URL;
import java.util.ResourceBundle;

public class OtherSettingsController implements Initializable, SettingsInterface {

    @FXML
    public Label hijriDateLabel;
    private OtherSettings otherSettings;
    @FXML
    private ComboBox<String> languageComboBox;
    @FXML
    private JFXCheckBox format24;
    @FXML
    private JFXCheckBox minimizeAtStart;
    @FXML
    private Spinner<Integer> hijriDateOffset;
    @FXML
    private JFXCheckBox darkTheme;
    @FXML
    private Label version;
    @FXML
    private VBox loadingBox;
    @FXML
    private JFXCheckBox autoUpdateCheckBox;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        otherSettings = Settings.getInstance().getOtherSettings();
        hijriDateLabel.setText(new HijriDate(otherSettings.getHijriOffset()).getString(otherSettings.getLanguageLocal()));

        hijriDateOffset.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-20, 20, 0));
        hijriDateOffset.getValueFactory().setValue(otherSettings.getHijriOffset());

        hijriDateOffset.valueProperty().addListener((observable, oldValue, newValue) ->
                hijriDateLabel.setText(new HijriDate(hijriDateOffset.getValue()).getString(otherSettings.getLanguageLocal())));


        languageComboBox.setItems(FXCollections.observableArrayList("عربي - Arabic", "إنجليزي - English"));
        languageComboBox.setValue(otherSettings.getLanguage());
        languageComboBox.setDisable(true);

        format24.setSelected(otherSettings.isEnable24Format());

        minimizeAtStart.setSelected(otherSettings.isMinimized());

        darkTheme.setSelected(otherSettings.isEnableDarkMode());
        darkTheme.setDisable(true);

        version.setText(DatabaseManager.getInstance().getVersion());

        autoUpdateCheckBox.setSelected(otherSettings.isAutomaticCheckForUpdates());
    }

    @FXML
    private void openWebsite() {
        try {
            Desktop.getDesktop().browse(new URI("https://www.abdelrahmanbayoumi.ml/Azkar-App/"));
        } catch (Exception e) {
            Logger.error(null, e, getClass().getName() + ".openWebsite()");
        }
    }

    @Override
    public void saveToDB() {
        otherSettings.setLanguage(languageComboBox.getValue());
        otherSettings.setEnable24Format(format24.isSelected());
        otherSettings.setEnableDarkMode(darkTheme.isSelected());
        otherSettings.setHijriOffset(hijriDateOffset.getValue());
        otherSettings.setMinimized(minimizeAtStart.isSelected());
        otherSettings.setAutomaticCheckForUpdates(autoUpdateCheckBox.isSelected());
        otherSettings.save();
    }

    @FXML
    private void openFeedback() {
        try {
            Stage stage = new Stage();
            stage.setScene(new Scene(FXMLLoader.load(getClass().getResource("/com/bayoumi/views/feedback/Feedback.fxml"))));
            stage.initModality(Modality.APPLICATION_MODAL);
            HelperMethods.SetIcon(stage);
            HelperMethods.ExitKeyCodeCombination(stage.getScene(), stage);
            stage.show();
            ((Stage) version.getScene().getWindow()).close();
        } catch (Exception e) {
            Logger.error(null, e, getClass().getName() + ".openFeedback()");
        }
    }

    @FXML
    private void checkForUpdate() {
        loadingBox.setVisible(true);
        switch (UpdateHandler.getInstance().checkUpdate()) {
            case 0:
                Logger.info(OtherSettings.class.getName() + ".checkForUpdate(): " + "No Update Found");
                Platform.runLater(() -> BuilderUI.showOkAlert(Alert.AlertType.INFORMATION, "لا يوجد تحديثات جديدة", true));
                break;
            case 1:
                UpdateHandler.getInstance().showInstallPrompt();
                break;
            case -1:
                Logger.info(OtherSettings.class.getName() + ".checkForUpdate(): " + "error => only installers and single bundle archives on macOS are supported for background updates");
                Platform.runLater(() -> BuilderUI.showOkAlert(Alert.AlertType.ERROR, "توجد مشكلة في البحث عن تحديثات جديدة", true));
                break;
        }
        loadingBox.setVisible(false);
    }
}