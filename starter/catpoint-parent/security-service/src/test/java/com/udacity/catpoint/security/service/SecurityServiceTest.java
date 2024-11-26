package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {
  @InjectMocks
  private SecurityService securityService;

  private final Sensor doorSensor = new Sensor("Sample door sensor", SensorType.DOOR);
  private final Sensor windowSensor = new Sensor("Sample window sensor", SensorType.WINDOW);
  private final Sensor motionSensor = new Sensor("Sample motion sensor", SensorType.MOTION);

  @Mock
  private SecurityRepository securityRepo;

  @Mock
  private ImageService imageService;

  @Mock
  private BufferedImage bufferedImage;

  @Mock
  private StatusListener statusListener;

  @BeforeEach
  public void init() {
    this.securityService = new SecurityService(this.securityRepo, this.imageService);
  }

  /**
   * 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
   */
  @ParameterizedTest
  @EnumSource(value = ArmingStatus.class, names = { "ARMED_AWAY", "ARMED_HOME" })
  public void alarmArmedAndSensorActivated_toPendingAlarmStatus(ArmingStatus armingStatus) {
    Mockito.when(this.securityRepo.getArmingStatus()).thenReturn(
      armingStatus
    );
    Mockito.when(this.securityRepo.getAlarmStatus()).thenReturn(
      AlarmStatus.NO_ALARM
    );
    this.securityService.changeSensorActivationStatus(this.doorSensor, true);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.PENDING_ALARM
    );
  }

  /**
   * 2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
   */
  @ParameterizedTest
  @EnumSource(value = ArmingStatus.class, names = { "ARMED_AWAY", "ARMED_HOME" })
  public void alarmArmedAndSensorActivatedAndInPendingAlarmStatus_toAlarmStatus(ArmingStatus armingStatus) {
    Mockito.when(this.securityRepo.getArmingStatus()).thenReturn(
      armingStatus
    );
    Mockito.when(this.securityRepo.getAlarmStatus()).thenReturn(
      AlarmStatus.PENDING_ALARM
    );
    this.securityService.changeSensorActivationStatus(this.doorSensor, true);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.ALARM
    );
  }

  /**
   * 3. If pending alarm and all sensors are inactive, return to no alarm state.
   */
  @Test
  public void pendingAlarmAndAllSensorsInactive_toNoAlarmStatus() {
    Mockito.when(this.securityRepo.getAlarmStatus()).thenReturn(
      AlarmStatus.PENDING_ALARM
    );
    for (final Sensor sensor : new Sensor[] { this.doorSensor, this.windowSensor, this.motionSensor }) {
      sensor.setActive(true);
      this.securityService.changeSensorActivationStatus(sensor, false);
    }

    // There are 3 sensors!
    Mockito.verify(this.securityRepo, Mockito.times(3)).setAlarmStatus(
      AlarmStatus.NO_ALARM
    );
  }

  /**
   * 4. If alarm is active, change in sensor state should not affect the alarm state.
   */
  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  public void alarmActive_sensorStateChangeDoNotAffectAlarmState(boolean isActive) {
    // Stubbing is only required in `false` case, hence this "lenient" call!
    Mockito.lenient().when(this.securityRepo.getAlarmStatus()).thenReturn(
      AlarmStatus.ALARM
    );

    this.securityService.addSensor(this.windowSensor);

    final Sensor sensor = this.doorSensor;
    this.securityService.addSensor(sensor);
    this.securityService.changeSensorActivationStatus(sensor, !isActive);

    Mockito.verify(this.securityRepo, Mockito.never()).setAlarmStatus(
      Mockito.any(AlarmStatus.class)
    );
  }

  /**
   * 5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.
   */
  @Test
  public void sensorActivatedWhileActiveAndSystemIsPending_changeToAlarmState() {
    Mockito.when(this.securityRepo.getAlarmStatus()).thenReturn(
      AlarmStatus.PENDING_ALARM
    );

    final Sensor sensor = this.doorSensor;
    this.securityService.addSensor(sensor);
    this.securityService.changeSensorActivationStatus(sensor, true);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.ALARM
    );
  }

  /**
   * 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
   */
  @Test
  public void sensorDeactivatedWhileInactive_noChangesToAlarmStatus() {
    final Sensor sensor = this.doorSensor;
    sensor.setActive(false);
    this.securityService.addSensor(sensor);
    this.securityService.changeSensorActivationStatus(sensor, false);

    Mockito.verify(this.securityRepo, Mockito.never()).setAlarmStatus(
      Mockito.any(AlarmStatus.class)
    );
  }

  /**
   * 7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
   */
  @Test
  public void imageServiceIdentifiesCatWhileSystemArmedHome_setAlarmStatus() {
    Mockito.when(this.securityRepo.getArmingStatus()).thenReturn(
      ArmingStatus.ARMED_HOME
    );

    Mockito.when(
      this.imageService.imageContainsCat(
        Mockito.any(BufferedImage.class),
        Mockito.anyFloat()
      )
    ).thenReturn(true);

    this.securityService.processImage(this.bufferedImage);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.ALARM
    );
  }

  /**
   * 8. If the image service identifies an image that does not contain a cat,
   * change the status to no alarm as long as the sensors are not active.
   */
  @Test
  public void imageServiceIdentifiesNoCatAndSensorsInactive_setStatusToNoAlarm() {
    Mockito.when(
      this.imageService.imageContainsCat(
        Mockito.any(BufferedImage.class),
        Mockito.anyFloat()
      )
    ).thenReturn(false);

    final Set<Sensor> sensors = Set.of(this.doorSensor, this.windowSensor, this.motionSensor);
    for (final Sensor sensor : sensors) {
      sensor.setActive(false);
    }
    Mockito.when(this.securityRepo.getSensors()).thenReturn(sensors);

    this.securityService.processImage(this.bufferedImage);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.NO_ALARM
    );
  }

  /**
   * 9. If the system is disarmed, set the status to no alarm.
   */
  @Test
  public void systemDisarmed_setStatusToNoAlarm() {
    this.securityService.setArmingStatus(ArmingStatus.DISARMED);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.NO_ALARM
    );
  }

  /**
   * 10. If the system is armed, reset all sensors to inactive.
   */
  @ParameterizedTest
  @EnumSource(value = AlarmStatus.class)
  public void systemArmed_setAllSensorsToInactive(AlarmStatus alarmStatus) {
    final Set<Sensor> sensors = Set.of(this.doorSensor, this.windowSensor, this.motionSensor);
    for (final Sensor sensor : sensors) {
      sensor.setActive(true);
    }
    Mockito.when(this.securityRepo.getSensors()).thenReturn(sensors);
    Mockito.when(this.securityRepo.getAlarmStatus()).thenReturn(alarmStatus);

    this.securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

    Assertions.assertTrue(
      sensors.stream().noneMatch(Sensor::getActive)
    );
  }

  /**
   * 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
   * (Trigger: Camera shows a cat)
   */
  @Test
  public void cameraShowsCatWhenSystemArmedHome_setStatusToAlarm() {
    Mockito.when(
      this.imageService.imageContainsCat(
        Mockito.any(BufferedImage.class),
        Mockito.anyFloat()
      )
    ).thenReturn(true);

    Mockito.when(this.securityRepo.getArmingStatus()).thenReturn(
      ArmingStatus.ARMED_HOME
    );

    this.securityService.processImage(this.bufferedImage);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.ALARM
    );
  }

  /**
   * 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
   * (Trigger: System switches to armed-home)
   */
  @Test
  public void switchToArmedHomeWhenCameraShowsCat_setStatusToAlarm() {
    Mockito.when(
      this.imageService.imageContainsCat(
        Mockito.any(BufferedImage.class),
        Mockito.anyFloat()
      )
    ).thenReturn(true);

    this.securityService.setArmingStatus(ArmingStatus.DISARMED);
    this.securityService.processImage(this.bufferedImage);
    this.securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

    Mockito.verify(this.securityRepo, Mockito.times(1)).setAlarmStatus(
      AlarmStatus.ALARM
    );
  }

  // Test coverage covers
  @Test
  public void addRemoveStatusListeners_noExceptionThrown() {
    Assertions.assertDoesNotThrow(() -> {
      this.securityService.addStatusListener(this.statusListener);
      this.securityService.removeStatusListener(this.statusListener);
    });
  }

  @ParameterizedTest
  @EnumSource(AlarmStatus.class)
  public void getAlarmStatus_followsSecurityRepo(AlarmStatus alarmStatus) {
    Mockito.when(this.securityRepo.getAlarmStatus()).thenReturn(alarmStatus);
    Assertions.assertEquals(alarmStatus, this.securityService.getAlarmStatus());
  }

  @Test
  public void addGetRemoveSensors_noExceptionsThrown() {
    Assertions.assertDoesNotThrow(() -> {
      for (final Sensor sensor : new Sensor[] { this.doorSensor, this.windowSensor, this.motionSensor }) {
        this.securityService.addSensor(sensor);
        this.securityService.getSensors();
        this.securityService.removeSensor(sensor);
      }
    });
  }
}
