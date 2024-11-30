package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 * <p>
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new HashSet<>();
    private AlarmStatus currentAlarmStatus = AlarmStatus.NO_ALARM;

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            getSensors().forEach(sensor -> changeSensorActivationStatus(sensor, false));
        }
        securityRepository.setArmingStatus(armingStatus);
    }

        private void catDetected(Boolean cat) {
            updateAlarmStatusBasedOnConditions(cat);
            statusListeners.forEach(sl -> sl.catDetected(cat));
        }

    private void updateAlarmStatusBasedOnConditions(Boolean cat) {
        if (isAllSensorsDeactivated()) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else if (getArmingStatus() == ArmingStatus.ARMED_HOME && cat) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (getArmingStatus() != ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    private boolean isAllSensorsDeactivated() {
        Set<Sensor> sensors = getSensors();
        return sensors.stream().noneMatch(Sensor::getActive);
    }

    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    private void notifyStatusChange(AlarmStatus status) {
        if (status != currentAlarmStatus) {
            statusListeners.forEach(sl -> sl.notify(status));
            currentAlarmStatus = status;
        }
    }

    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        notifyStatusChange(status);
    }

    private void processSensorActivation(Sensor sensor) {
        if (sensor.getActive()) {
            handleSensorActivated();
        } else {
            handleSensorDeactivated();
        }
    }

    private void handleSensorActivated() {
        if (securityRepository.getArmingStatus() != ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    private void handleSensorDeactivated() {
        if (securityRepository.getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
    }

    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
        processSensorActivation(sensor);
    }

    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
