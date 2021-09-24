package com.bayoumi.controllers.home;

import com.batoulapps.adhan.Prayer;
import com.batoulapps.adhan.PrayerTimes;
import com.bayoumi.Launcher;
import com.bayoumi.controllers.azkar.timed.TimedAzkarController;
import com.bayoumi.controllers.home.periods.AzkarPeriodsController;
import com.bayoumi.controllers.home.prayertimes.PrayerTimesController;
import com.bayoumi.models.AbsoluteZekr;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.util.Logger;
import com.bayoumi.util.gui.HelperMethods;
import com.bayoumi.util.gui.notfication.Notification;
import com.bayoumi.util.prayertimes.PrayerTimesUtil;
import com.bayoumi.util.services.azkar.AzkarService;
import com.bayoumi.util.services.reminders.Reminder;
import com.bayoumi.util.services.reminders.ReminderUtil;
import com.bayoumi.util.time.HijriDate;
import com.bayoumi.util.time.Utilities;
import com.bayoumi.util.validation.SingleInstance;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.Date;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    // ==== Helper Objects ====
    public Date date;
    private String remainingTime;
    private AzkarPeriodsController azkarPeriodsController;
    private PrayerTimesController prayerTimesController;
    // ==== Settings Objects ====
    private Settings settings;
    private PrayerTimes prayerTimesToday;
    // ==== GUI Objects ====
    @FXML
    private Label timeLabel, day, hijriDate, gregorianDate;
    @FXML
    private Label currentPrayerText, remainingTimeLabel;
    @FXML
    private VBox container, periodBox;
    @FXML
    private HBox mainContainer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        FXMLLoader fxmlLoader;
        date = new Date();
        // initialize required dependencies
        settings = Settings.getInstance();
        prayerTimesToday = PrayerTimesUtil.getPrayerTimesToday(settings.getPrayerTimeSettings(), date);

        try {
            fxmlLoader = new FXMLLoader(getClass().getResource("/com/bayoumi/views/home/prayertimes/PrayerTimes.fxml"));
            mainContainer.getChildren().add(1, fxmlLoader.load());
            prayerTimesController = fxmlLoader.getController();
            prayerTimesController.setData(settings, prayerTimesToday);
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.error("loading PrayerTimes", ex, getClass().getName() + ".initialize()");
            Launcher.workFine.setValue(false);
        }

        // if no azkar for notification terminate the program
        if (!AbsoluteZekr.fetchData()) {
            Launcher.workFine.setValue(false);
            return;
        }

        initClock();
        day.setText(Utilities.getDay(settings.getOtherSettings().getLanguageLocal(), date));
        gregorianDate.setText(Utilities.getGregorianDate(settings.getOtherSettings().getLanguageLocal(), date));
        hijriDate.setText(new HijriDate(settings.getOtherSettings().getHijriOffset()).getString(settings.getOtherSettings().getLanguageLocal()));

        initReminders();

        try {
            fxmlLoader = new FXMLLoader(getClass().getResource("/com/bayoumi/views/home/periods/AzkarPeriods.fxml"));
            periodBox = fxmlLoader.load();
            azkarPeriodsController = fxmlLoader.getController();
            azkarPeriodsController.setData(settings);
            container.getChildren().add(2, periodBox);
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.error("loading AzkarPeriods", ex, getClass().getName() + ".initialize()");
            Launcher.workFine.setValue(false);
        }

        // if there is a change in HijriDate offset
        settings.getOtherSettings().addObserver((o, arg) -> {
            hijriDate.setText(new HijriDate(settings.getOtherSettings().getHijriOffset()).getString(settings.getOtherSettings().getLanguageLocal()));
            prayerTimesController.setPrayerTimesValuesToGUI();
        });

        // if time periods of Azkar and settings has changed
        settings.getAzkarSettings().addObserver((o, arg) -> {
            if (settings.getAzkarSettings().isStopped()) {
                AzkarService.stopService();
                periodBox.setDisable(true);
            } else {
                AzkarService.init(azkarPeriodsController, settings.getNotificationSettings());
                periodBox.setDisable(false);
            }
            // reinitialize Reminders
            initReminders();
        });

        settings.getPrayerTimeSettings().addObserver((o, arg) -> {
            prayerTimesToday = PrayerTimesUtil.getPrayerTimesToday(settings.getPrayerTimeSettings(), date);
            prayerTimesController.setPrayerTimes(prayerTimesToday);
            initReminders();
        });
    }


    private void initClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.ZERO, e -> {
            date = new Date();

            // -- increment time --
            String timeNow;
            if (settings.getOtherSettings().isEnable24Format()) {
                timeNow = Utilities.getTime24(settings.getOtherSettings().getLanguageLocal(), date);
            } else {
                timeNow = Utilities.getTime(settings.getOtherSettings().getLanguageLocal(), date);
            }
            timeLabel.setText(timeNow);

            // is new day? => change Dates and the prayer times
            if (timeNow.equals("12:00:00 AM") || timeNow.equals("12:00:00 ص") || timeNow.equals("00:00:00")
                    || !Utilities.getDay(settings.getOtherSettings().getLanguageLocal(), date).equals(day.getText())) {
                // update week day name
                day.setText(Utilities.getDay(settings.getOtherSettings().getLanguageLocal(), date));
                // update Gregorian date
                gregorianDate.setText(Utilities.getGregorianDate(settings.getOtherSettings().getLanguageLocal(), date));
                // update Hijri date
                hijriDate.setText(new HijriDate(settings.getOtherSettings().getHijriOffset()).getString(settings.getOtherSettings().getLanguageLocal()));
                // get prayer times for the new day
                prayerTimesToday = PrayerTimesUtil.getPrayerTimesToday(settings.getPrayerTimeSettings(), date);
                prayerTimesController.setPrayerTimes(prayerTimesToday);
                // reinitialize Reminders
                initReminders();
            }

            prayerTimesController.prayerTimelineAction();
            handlePrayerRemainingTime(date);
            checkForReminders(date);
        }), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }


    // ==============================================
    // ============== Buttons Handler ===============
    // ==============================================
    @FXML
    public void goToMorningAzkar() {
        showTimedAzkar("morning");
    }

    @FXML
    public void goToNightAzkar() {
        showTimedAzkar("night");
    }

    private void showTimedAzkar(String type) {
        try {
            Stage stage = new Stage();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bayoumi/views/azkar/timed/TimedAzkar.fxml"));
            stage.setScene(new Scene(loader.load()));
            stage.initOwner(SingleInstance.getInstance().getCurrentStage());
            stage.initModality(Modality.APPLICATION_MODAL);
            HelperMethods.SetIcon(stage);
            TimedAzkarController controller = loader.getController();
            controller.setData(type);
            HelperMethods.ExitKeyCodeCombination(stage.getScene(), stage);
            stage.show();
        } catch (Exception e) {
            Logger.error(null, e, getClass().getName() + ".showTimedAzkar()");
        }
    }

    @FXML
    private void goToSettings() {
        try {
            Stage stage = new Stage();
            stage.setScene(new Scene(FXMLLoader.load(getClass().getResource("/com/bayoumi/views/settings/Settings.fxml"))));
            stage.initOwner(SingleInstance.getInstance().getCurrentStage());
            stage.initModality(Modality.APPLICATION_MODAL);
            HelperMethods.SetIcon(stage);
            HelperMethods.ExitKeyCodeCombination(stage.getScene(), stage);
            stage.showAndWait();
        } catch (Exception e) {
            Logger.error(null, e, getClass().getName() + ".goToSettings()");
        }
    }


    // ==============================================
    // ============ Prayer Times Methods ============
    // ==============================================
    private void handlePrayerRemainingTime(Date dateNow) {
        Date nextPrayerTime;
        // take current Prayer ( when isha is finished and it's before 12pm )
        if (prayerTimesToday.nextPrayer().equals(Prayer.NONE)) {
            currentPrayerText.setText("مر على " + prayerTimesController.getCurrentPrayerValue());
            nextPrayerTime = prayerTimesToday.timeForPrayer(prayerTimesToday.currentPrayer());
        } else {
            currentPrayerText.setText("متبقي على " + prayerTimesController.getCurrentPrayerValue());
            nextPrayerTime = prayerTimesToday.timeForPrayer(prayerTimesToday.nextPrayer());
        }

        if (dateNow.compareTo(nextPrayerTime) < 0) {
            // dateNow is before nextPrayerTime
            remainingTime = "- ";
        } else if (dateNow.compareTo(nextPrayerTime) > 0) {
            // dateNow is after nextPrayerTime
            remainingTime = "+ ";
        }
        remainingTime += Utilities.findDifference(dateNow, nextPrayerTime);
        remainingTimeLabel.setText(remainingTime);
    }

    private void initReminders() {
        ReminderUtil.getInstance().clear();
        ReminderUtil.getInstance().add(new Reminder(prayerTimesToday.fajr, () -> playAdhan("صلاة الفجر")));
        ReminderUtil.getInstance().add(new Reminder(prayerTimesToday.dhuhr, () -> playAdhan("صلاة الظهر")));
        ReminderUtil.getInstance().add(new Reminder(prayerTimesToday.asr, () -> playAdhan("صلاة العصر")));
        ReminderUtil.getInstance().add(new Reminder(prayerTimesToday.maghrib, () -> playAdhan("صلاة المغرب")));
        ReminderUtil.getInstance().add(new Reminder(prayerTimesToday.isha, () -> playAdhan("صلاة العشاء")));
        if (settings.getAzkarSettings().getMorningAzkarOffset() != 0) {
            Date morningAzkarDate = ((Date) prayerTimesToday.fajr.clone());
            morningAzkarDate.setTime(prayerTimesToday.fajr.getTime() + (settings.getAzkarSettings().getMorningAzkarOffset() * 60000));
            ReminderUtil.getInstance().add(new Reminder(morningAzkarDate, () -> Platform.runLater(() ->
                    Notification.createControlsFX("أذكار الصباح",
                            new Image("/com/bayoumi/images/sun_50px.png"),
                            () -> Launcher.homeController.goToMorningAzkar(),
                            30, settings.getNotificationSettings()))));
        }
        if (settings.getAzkarSettings().getNightAzkarOffset() != 0) {
            Date nightAzkarDate = ((Date) prayerTimesToday.asr.clone());
            nightAzkarDate.setTime(prayerTimesToday.asr.getTime() + (settings.getAzkarSettings().getNightAzkarOffset() * 60000));
            ReminderUtil.getInstance().add(new Reminder(nightAzkarDate, () -> Platform.runLater(() ->
                    Notification.createControlsFX("أذكار المساء",
                            new Image("/com/bayoumi/images/night_50px.png"),
                            () -> Launcher.homeController.goToNightAzkar(),
                            30, settings.getNotificationSettings()))));
        }
    }

    private void checkForReminders(Date date) {
        ReminderUtil.getInstance().validate(date);
    }


    private void playAdhan(String prayerName) {
        System.out.println("playAdhan() => " + prayerName);
        Platform.runLater(() -> Notification.createControlsFX(prayerName, new Image("/com/bayoumi/images/Kaaba.png")
                , null, 300, settings.getNotificationSettings()));
    }

}
