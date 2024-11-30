package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.udacity.catpoint.security.data.AlarmStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    @InjectMocks
    private SecurityService securityService;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private StatusListener statusListener;

    @Mock
    private Sensor sensor;

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
    }

    @Test
    @DisplayName("Test add status listener")
    void addStatusListener_addsListener() {
        securityService.addStatusListener(statusListener);
        securityService.setAlarmStatus(ALARM);
        verify(statusListener, times(1)).notify(ALARM);
    }

    @Test
    @DisplayName("Test remove status listener")
    void removeStatusListener_removesListener() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
        securityService.setAlarmStatus(ALARM);
        verify(statusListener, times(0)).notify(any(AlarmStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    @DisplayName("Test setting arming status sets alarm status correctly")
    void setArmingStatus_setsAlarmStatus(ArmingStatus armingStatus) {
        securityService.setArmingStatus(armingStatus);
        verify(securityRepository, times(0)).setAlarmStatus(ALARM);
    }

    @Test
    void setArmingStatus_disarm_resetsAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(NO_ALARM);
    }

    @Test
    @DisplayName("Test sensor activation changes alarm status")
    void changeSensorActivationStatus_sensorActivated_setsAlarmStatus_handleSensorActivated() {
        sensor = new Sensor("Sensor-01", SensorType.DOOR);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(PENDING_ALARM);
        verify(securityRepository, times(1)).updateSensor(sensor);
    }

    @Test
    @DisplayName("Test sensor activation changes alarm status")
    void changeSensorActivationStatus_sensorActivated_setsAlarmStatus_handleSensorDeactivated() {
        sensor = new Sensor("Sensor-01", SensorType.DOOR);
        when(securityRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, times(1)).setAlarmStatus(NO_ALARM);
        verify(securityRepository, times(1)).updateSensor(sensor);
    }


    @Test
    void processImage_imageContainsCat_callsCatDetected() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);
        verify(imageService).imageContainsCat(image, 50.0f);
    }

    @Test
    void setArmingStatus_armedAway_changesSensorStatusToInactive() {
        Sensor sensor = mock(Sensor.class);
        when(sensor.getActive()).thenReturn(true);
        Set<Sensor> sensors = new HashSet<>();
        sensors.add(sensor);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        verify(securityRepository).updateSensor(sensor);
        verify(sensor).setActive(false);
    }

    @Test
    @DisplayName("Test setArmingStatus with ARMED_HOME and found no triggers ALARM status")
    void processImage_catDetectedInArmedHome_setsAlarm() {
        Sensor sensor = mock(Sensor.class);
        when(sensor.getActive()).thenReturn(true);
        Set<Sensor> sensors = new HashSet<>();
        sensors.add(sensor);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(image, 50.0f)).thenReturn(false);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }


    @Test
    void testNoActiveSensors_setsNoAlarm() {
        Sensor sensor1 = mock(Sensor.class);
        Sensor sensor2 = mock(Sensor.class);
        when(sensor1.getActive()).thenReturn(false);
        when(sensor2.getActive()).thenReturn(false);
        Set<Sensor> sensors = Set.of(sensor1, sensor2);
        when(securityRepository.getSensors()).thenReturn(sensors);
        BufferedImage image = mock(BufferedImage.class);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(NO_ALARM);
    }

    @Test
    @DisplayName("Test setArmingStatus with ARMED_HOME and cat detection triggers ALARM status")
    void processImage_armedHome_andCatDetected_setsAlarmToALARM() {
        Sensor sensor = mock(Sensor.class);
        when(sensor.getActive()).thenReturn(true);
        Set<Sensor> sensors = new HashSet<>();
        sensors.add(sensor);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(ALARM);
    }

    @Test
    @DisplayName("Test setArmingStatus with ARMED_HOME and cat detection triggers ALARM status")
    void processImage_armedHome_andCatDetected_setsAlarmToALARM_noCat() {
        SecurityRepository securityRepository = mock(SecurityRepository.class);
        ImageService imageService = mock(ImageService.class);
        SecurityService securityService = new SecurityService(securityRepository, imageService);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository).setAlarmStatus(NO_ALARM);
    }

    @Test
    @DisplayName("Test getAlarmStatus retrieves the correct AlarmStatus from SecurityRepository")
    void getAlarmStatus_returnsCorrectAlarmStatus() {
        SecurityRepository mockRepository = mock(SecurityRepository.class);
        when(mockRepository.getAlarmStatus()).thenReturn(PENDING_ALARM);
        SecurityService securityService = new SecurityService(mockRepository, mock(ImageService.class));
        AlarmStatus alarmStatus = securityService.getAlarmStatus();
        assertEquals(PENDING_ALARM, alarmStatus);
        verify(mockRepository).getAlarmStatus();
    }

    @Test
    @DisplayName("Test addSensor calls SecurityRepository.addSensor with the correct sensor")
    void addSensor_callsRepositoryAddSensor() {
        SecurityRepository mockRepository = mock(SecurityRepository.class);
        SecurityService securityService = new SecurityService(mockRepository, mock(ImageService.class));
        Sensor mockSensor = mock(Sensor.class);
        securityService.addSensor(mockSensor);
        verify(mockRepository).addSensor(mockSensor);
    }

    @Test
    @DisplayName("Test removeSensor calls SecurityRepository.removeSensor with the correct sensor")
    void removeSensor_callsRepositoryRemoveSensor() {
        SecurityRepository mockRepository = mock(SecurityRepository.class);
        SecurityService securityService = new SecurityService(mockRepository, mock(ImageService.class));
        Sensor mockSensor = mock(Sensor.class);
        securityService.removeSensor(mockSensor);
        verify(mockRepository).removeSensor(mockSensor);
    }
}